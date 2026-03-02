package com.example.androidmusicplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        val agreeButton = findViewById<Button>(R.id.btn_agree)
        val disagreeButton = findViewById<Button>(R.id.btn_disagree)
        
        agreeButton?.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            navigateToMain()
        }
        
        disagreeButton?.setOnClickListener {
            finish()
        }
        
        handler.postDelayed({
            navigateToMain()
        }, 5000)
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
