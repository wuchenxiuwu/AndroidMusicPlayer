package com.example.androidmusicplayer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PrivacyPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)
        
        val tvPolicy = findViewById<TextView>(R.id.tv_privacy_policy)
        tvPolicy?.text = getString(R.string.privacy_policy_content)
    }
}
