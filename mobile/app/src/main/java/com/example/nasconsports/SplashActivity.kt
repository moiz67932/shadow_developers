package com.example.nasconsports

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** A short branded launch; session restoration remains owned by MainActivity. */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openArena = Runnable {
        if (!isFinishing) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_splash)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(28, bars.top + 28, 28, bars.bottom + 28)
            insets
        }
    }
    override fun onResume() { super.onResume(); handler.postDelayed(openArena, 450) }
    override fun onPause() { handler.removeCallbacks(openArena); super.onPause() }
}
