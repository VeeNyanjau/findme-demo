package com.example.findme

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * ConsentActivity
 * This is the entry point of the application.
 * It strictly adheres to ethical guidelines by forcing an explicit user consent
 * before any functionality is accessible.
 */
class ConsentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consent)

        // Check if user already consented. If so, move to Setup or Main.
        val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
        val hasConsented = sharedPref.getBoolean("HAS_CONSENTED", false)
        val communityId = sharedPref.getString("COMMUNITY_ID", "")

        if (hasConsented) {
            if (!communityId.isNullOrEmpty()) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, SetupActivity::class.java))
            }
            finish()
            return
        }

        val btnAgree = findViewById<Button>(R.id.btnAgree)
        val btnDecline = findViewById<Button>(R.id.btnDecline)

        btnAgree.setOnClickListener {
            // Save consent state
            sharedPref.edit().putBoolean("HAS_CONSENTED", true).apply()
            
            Toast.makeText(this, "Consent recorded. Proceeding to setup.", Toast.LENGTH_SHORT).show()
            
            // Navigate to SetupActivity
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
        }

        btnDecline.setOnClickListener {
            // Exit the application if consent is not given
            Toast.makeText(this, "Consent declined. Exiting app.", Toast.LENGTH_SHORT).show()
            finishAffinity() 
        }
    }
}
