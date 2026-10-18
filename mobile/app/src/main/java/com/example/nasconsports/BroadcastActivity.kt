package com.example.nasconsports

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Embeds MediaMTX's actual WHIP publisher; no simulated live state or camera-only preview. */
class BroadcastActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var message: TextView
    private var publisher = ""
    private val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val server = Uri.parse(getSharedPreferences("settings", MODE_PRIVATE).getString("server", "http://127.0.0.1:8080"))
        publisher = intent.getStringExtra("publisher") ?: ""
        val uri = Uri.parse(publisher)
        if (uri.host != server.host || uri.scheme !in listOf("http", "https") || !uri.path.orEmpty().matches(Regex("/live/[A-Za-z0-9_-]+/publish"))) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 28, 16, 16); setBackgroundColor(0xff070b13.toInt()) }
        message = TextView(this).apply { text = "BROADCAST STUDIO\nAllow camera/microphone, then connect in the publisher. HTTPS or loopback is required for camera access."; setTextColor(0xfff8fafc.toInt()); textSize = 16f }
        root.addView(message)
        web = WebView(this).apply {
            setBackgroundColor(0xff070b13.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val next = request.url
                    return next.scheme != uri.scheme || next.host != uri.host || next.port != uri.port
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (!isFinishing && request.origin.host == uri.host && request.origin.port == uri.port && request.origin.scheme == uri.scheme && permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                            val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                            if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
                        } else request.deny()
                    }
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text = "Stop publishing & return"; setOnClickListener { finish() } })
        setContentView(root)
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) web.loadUrl(publisher)
        else requestPermissions(permissions, 2032)
    }
    override fun onRequestPermissionsResult(code: Int, names: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, names, results)
        if (code == 2032 && results.size == 2 && results.all { it == PackageManager.PERMISSION_GRANTED }) web.loadUrl(publisher)
        else message.text = "Camera and microphone permission are required. Reopen the studio after enabling them in Android settings."
    }
    override fun onPause() { if (::web.isInitialized) { web.loadUrl("about:blank"); web.onPause() }; super.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) { web.onResume(); if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) web.loadUrl(publisher) } }
    override fun onDestroy() { if (::web.isInitialized) { web.stopLoading(); web.destroy() }; super.onDestroy() }
}
