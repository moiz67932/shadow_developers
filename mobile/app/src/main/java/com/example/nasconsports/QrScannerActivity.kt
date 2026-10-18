package com.example.nasconsports

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Bundled QR decoding; works without a scanner app or a network connection. */
@Suppress("DEPRECATION")
class QrScannerActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private var camera: Camera? = null
    private var resumed = false
    private var delivered = false
    private var torch = false
    private var lastFrame = 0L
    private val decoding = AtomicBoolean(false)
    private val decoder = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.screen_qr_scanner)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scanner_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val pad = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad); insets
        }
        status = findViewById(R.id.scanner_status)
        preview = findViewById<TextureView>(R.id.scanner_preview).apply { surfaceTextureListener = this@QrScannerActivity }
        findViewById<View>(R.id.scanner_close).setOnClickListener { finish() }
        findViewById<View>(R.id.scanner_permission).setOnClickListener { requestCamera() }
        findViewById<MaterialButton>(R.id.scanner_torch).setOnClickListener {
            val active = camera ?: return@setOnClickListener
            try {
                val parameters = active.parameters
                if (parameters.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) != true) {
                    status.text = "This camera has no flashlight."; return@setOnClickListener
                }
                torch = !torch
                parameters.flashMode = if (torch) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
                active.parameters = parameters
                findViewById<MaterialButton>(R.id.scanner_torch).text = if (torch) "Turn flashlight off" else "Turn flashlight on"
            } catch (_: Exception) { status.text = "Flashlight unavailable. Try more ambient light." }
        }
    }
    override fun onResume() { super.onResume(); resumed = true; requestCamera() }
    override fun onPause() { resumed = false; releaseCamera(); super.onPause() }
    override fun onDestroy() { decoder.shutdownNow(); super.onDestroy() }
    private fun requestCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 2033)
    }
    override fun onRequestPermissionsResult(code: Int, names: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, names, results)
        if (code == 2033 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else status.text = "Camera access was denied. Enable Camera in Android app settings, or return and paste a link."
    }
    private fun openCamera() {
        if (!resumed || delivered || camera != null || !preview.isAvailable) return
        try {
            var cameraId = 0
            val info = Camera.CameraInfo()
            for (id in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(id, info)
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { cameraId = id; break }
            }
            Camera.getCameraInfo(cameraId, info)
            val active = Camera.open(cameraId)
            camera = active
            val parameters = active.parameters
            parameters.supportedPreviewSizes?.filter { it.width <= 1280 && it.height <= 960 }?.maxByOrNull { it.width * it.height }?.let { parameters.setPreviewSize(it.width, it.height) }
            parameters.previewFormat = android.graphics.ImageFormat.NV21
            if (parameters.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            active.parameters = parameters
            val degrees = when (windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            active.setDisplayOrientation(if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) (360 - (info.orientation + degrees) % 360) % 360 else (info.orientation - degrees + 360) % 360)
            active.setPreviewTexture(preview.surfaceTexture)
            active.setPreviewCallback { bytes, source ->
                if (bytes == null || delivered || !resumed || SystemClock.elapsedRealtime() - lastFrame < 300 || !decoding.compareAndSet(false, true)) return@setPreviewCallback
                lastFrame = SystemClock.elapsedRealtime()
                val size = source.parameters.previewSize
                val copy = bytes.copyOf()
                decoder.execute {
                    try {
                        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)) }
                        val luminance = PlanarYUVLuminanceSource(copy, size.width, size.height, 0, 0, size.width, size.height, false)
                        val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance)))
                        runOnUiThread {
                            if (!resumed || delivered || isFinishing) return@runOnUiThread
                            delivered = true; releaseCamera()
                            setResult(Activity.RESULT_OK, Intent().putExtra("SCAN_RESULT", result.text)); finish()
                        }
                    } catch (_: Exception) { /* Continue reading frames until a QR is in focus. */ }
                    finally { decoding.set(false) }
                }
            }
            active.startPreview()
            status.text = "Align a NaSCon QR code inside the frame."
        } catch (_: Exception) { releaseCamera(); status.text = "Camera unavailable. Close other camera apps and retry, or paste a link." }
    }
    private fun releaseCamera() { camera?.let { try { it.setPreviewCallback(null); it.stopPreview(); it.release() } catch (_: Exception) {} }; camera = null; torch = false }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera() }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { releaseCamera(); return true }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
