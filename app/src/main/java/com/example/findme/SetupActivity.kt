package com.example.findme

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SetupActivity
 * Handles Identity Generation and Community Connection (Create/Join).
 */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etCommunityName = findViewById<EditText>(R.id.etCommunityName)
        val etPhone = findViewById<EditText>(R.id.etEmergencyPhone)
        val btnCreate = findViewById<Button>(R.id.btnCreate)
        val btnJoin = findViewById<Button>(R.id.btnJoin)
        val tvMyHandle = findViewById<TextView>(R.id.tvMyHandle)

        // Load Prefs
        val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
        val myHandle = sharedPref.getString("MY_HANDLE", null)
        val savedCommunity = sharedPref.getString("COMMUNITY_ID", "")
        etCommunityName.setText(savedCommunity)
        etPhone.setText(sharedPref.getString("EMERGENCY_PHONE", ""))
        findViewById<EditText>(R.id.etMyPhone).setText(sharedPref.getString("MY_PHONE", ""))

        // 1. My User ID Logic (Fixed)
        if (myHandle == null) {
            // Generate on first run
            AuthManager.signInAnonymously({ }, { }) // Ensure auth
            tvMyHandle.text = "Generating..."
            FirebaseManager.generateUniqueUsername(
                onSuccess = { handle ->
                    tvMyHandle.text = handle
                    sharedPref.edit().putString("MY_HANDLE", handle).apply()
                },
                onFailure = {
                    tvMyHandle.text = "Error Generating ID"
                }
            )
        } else {
            tvMyHandle.text = myHandle
        }

        // Clipboard: Copy ID Logic
        tvMyHandle.setOnClickListener {
            val text = tvMyHandle.text.toString()
            if (text.startsWith("USER-")) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("User ID", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "ID Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Button Logic
        btnCreate.setOnClickListener {
            handleCommunityAction(etCommunityName, etPhone, isCreate = true)
        }

        btnJoin.setOnClickListener {
            handleCommunityAction(etCommunityName, etPhone, isCreate = false)
        }
    }

    private fun handleCommunityAction(etName: EditText, etPhone: EditText, isCreate: Boolean) {
        val name = etName.text.toString().trim()
        val emergencyPhone = etPhone.text.toString().trim()
        val myPhone = findViewById<EditText>(R.id.etMyPhone).text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a Community Name", Toast.LENGTH_SHORT).show()
            return
        }

        if (myPhone.isEmpty()) {
            Toast.makeText(this, "Your Phone Number is required for identity", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = AuthManager.getCurrentUid()
        if (uid == null) {
             Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
             AuthManager.signInAnonymously({ }, {}) 
             return
        }
        
        val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
        val currentHandle = sharedPref.getString("MY_HANDLE", null)

        Toast.makeText(this, "Verifying Identity...", Toast.LENGTH_SHORT).show()

        // 1. Check if this phone already has an ID
        FirebaseManager.getUserIdByPhone(myPhone, 
            onSuccess = { existingId ->
                 val finalHandle = if (!existingId.isNullOrEmpty()) {
                     // RECOVERY
                     if (existingId != currentHandle) {
                        Toast.makeText(this, "Welcome back! ID Recovered: $existingId", Toast.LENGTH_LONG).show()
                        sharedPref.edit().putString("MY_HANDLE", existingId).apply()
                        findViewById<TextView>(R.id.tvMyHandle).text = existingId // Visual update
                     }
                     existingId
                 } else {
                     // NEW REGISTRATION
                     // Use the currently generated one (or wait if null? It's generated in onCreate)
                     // If null for some reason (race condition), unlikely but safeguard:
                     val newId = currentHandle ?: "USER-ERR" 
                     FirebaseManager.saveUserMapping(myPhone, newId, {}, {})
                     newId
                 }
                 
                 // 2. Proceed with Community Action using finalHandle/uid
                 // Note: 'uid' is Firebase Auth UID (anonymous), 'finalHandle' is our custom ID.
                 // We pass Auth UID to createCommunity creator field.
                 
                 proceedWithCommunity(name, emergencyPhone, myPhone, isCreate, uid)
            },
            onFailure = {
                 Toast.makeText(this, "Network Error. Try again.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun proceedWithCommunity(name: String, emergencyPhone: String, myPhone: String, isCreate: Boolean, authUid: String) {
        val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
        
        if (isCreate) {
            FirebaseManager.createCommunity(name, authUid,
                onSuccess = {
                    saveAndExit(sharedPref, name, emergencyPhone, myPhone, "Community '$name' Created!")
                },
                onFailure = { e ->
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            FirebaseManager.joinCommunity(name,
                onSuccess = {
                    saveAndExit(sharedPref, name, emergencyPhone, myPhone, "Joined '$name'!")
                },
                onFailure = { e ->
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun saveAndExit(prefs: android.content.SharedPreferences, community: String, emergencyPhone: String, myPhone: String, msg: String) {
        prefs.edit()
            .putString("COMMUNITY_ID", community)
            .putString("EMERGENCY_PHONE", emergencyPhone)
            .putString("MY_PHONE", myPhone)
            .apply()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
