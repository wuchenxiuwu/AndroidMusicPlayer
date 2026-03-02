package com.example.androidmusicplayer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UserAgreementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_agreement)
        
        val tvAgreement = findViewById<TextView>(R.id.tv_user_agreement)
        tvAgreement?.text = getString(R.string.user_agreement_content)
    }
}
