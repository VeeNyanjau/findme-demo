package com.example.findme

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MainActivity (Dashboard)
 * The main interface for the user to trigger emergency alerts.
 * FEATURES:
 * - Real GPS (FusedLocationProvider)
 * - Robust Offline Fallback (Current -> Last Known -> Error Warning)
 * - Real SMS (ACTION_SENDTO)
 * - Edit Contacts (Navigation to SetupActivity)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var communityId: String? = null
    private var currentUid: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startPassiveLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission required for alerts!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val btnTestAlert = findViewById<Button>(R.id.btnTestAlert)
        val btnEditContacts = findViewById<Button>(R.id.btnEditContacts)



        // Anonymous Auth (One time init is fine here, or move to onResume if we want to retry)
        AuthManager.signInAnonymously(
            onSuccess = { uid ->
                currentUid = uid
                Toast.makeText(this, "Signed in anonymously", Toast.LENGTH_SHORT).show()
                refreshDashboard() // Refresh UI now that we have UID
            },
            onFailure = {
                Toast.makeText(this, "Auth Failed", Toast.LENGTH_SHORT).show()
            }
        )

        // Start passive updates if permission arguably already exists or will be requested
        // Proactively request permission immediately
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startPassiveLocationUpdates()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        btnTestAlert.setOnClickListener {
            checkPermissionsAndTrigger()
        }

        btnEditContacts.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
            finish() // Finish Main so it reloads fresh on return? Or we just use onResume.
            // Actually, if we finish(), Setup starts Main new.
            // SetupActivity call: startActivity(Intent(this, MainActivity::class.java)); finish()
            // So MainActivity IS recreated.
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
        startSafetyService()
        startListeningForDialogs()
    }



    private fun refreshDashboard() {
        val tvContactInfo = findViewById<TextView>(R.id.tvContactInfo)
        val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
        communityId = sharedPref.getString("COMMUNITY_ID", "Not Set")
        val myHandle = sharedPref.getString("MY_HANDLE", "Loading...")
        tvContactInfo.text = "ID: $myHandle  |  COMMUNITY: $communityId"

        tvContactInfo.setOnClickListener {
            if (myHandle != null && myHandle.startsWith("USER-")) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("User ID", myHandle)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "ID Copied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPassiveLocationUpdates() {
        // Request passive updates to keep the "Last Known Location" fresh
        val request = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10000
        ).build()
        
        val callback = object : com.google.android.gms.location.LocationCallback() {}
        fusedLocationClient.requestLocationUpdates(request, callback, mainLooper)
    }

    private fun checkPermissionsAndTrigger() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                fetchLocationAndAlert()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndAlert() {
        // Immediate fetch from cache. No waiting for fresh GPS fix.
        // Request a fresh, high-accuracy location. 
        // usage: getCurrentLocation(priority, token)
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    processAlert(location.latitude, location.longitude, "GPS (Fresh)")
                } else {
                    // Fallback to last known if current fetch fails (timeout or null)
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                         if (lastLoc != null) {
                             processAlert(lastLoc.latitude, lastLoc.longitude, "GPS (Cached Fallback)")
                         } else {
                             processAlert(0.0, 0.0, "Unknown", isLocationMissing = true)
                         }
                    }
                }
            }
            .addOnFailureListener {
                processAlert(0.0, 0.0, "Error", isLocationMissing = true)
            }
    }

    private fun processAlert(lat: Double, lon: Double, source: String, isLocationMissing: Boolean = false) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val locationStr = if (!isLocationMissing) "Lat: $lat, Lon: $lon" else "Location Unavailable"

        // Construct Map Payload for Firebase
        val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
        val myHandle = sharedPref.getString("MY_HANDLE", null) ?: "ANONYMOUS"
        val myPhone = sharedPref.getString("MY_PHONE", "") ?: ""

        val alertData = mapOf(
            "type" to "EMERGENCY",
            "senderId" to myHandle,
            "senderPhone" to myPhone,
            "timestamp" to timestamp,
            "location" to locationStr,
            "source" to source,
            "rawLat" to lat,
            "rawLon" to lon
        )

        Toast.makeText(this, "Broadcasting to $communityId...", Toast.LENGTH_SHORT).show()
        
        // Send to Firebase
        val targetCommunity = communityId ?: "PUBLIC"
        FirebaseManager.broadcastAlert(
            targetCommunity,
            alertData,
            onSuccess = {
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("ALERT BROADCASTED")
                    .setMessage("Your emergency alert has been successfully synced to the cloud.\n\nCommunity: $targetCommunity")
                    .setPositiveButton("OK", null)
                    .create()
                dialog.show()
            },
            onFailure = { e ->
                Toast.makeText(this, "Broadcast Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private val alertListenerMap = mutableMapOf<String, com.google.firebase.database.ChildEventListener>()
    private val sessionStartTime = System.currentTimeMillis()
    
    private fun startSafetyService() {
        val intent = Intent(this, SafetyService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startListeningForDialogs() {
        if (communityId.isNullOrEmpty() || communityId == "Not Set") return
        if (alertListenerMap.containsKey(communityId)) return

        val listener = FirebaseManager.listenForAlerts(communityId!!) { data ->
            val timestampStr = data["timestamp"] as? String ?: return@listenForAlerts
            try {
                // UI logic: Only show Dialog for recent alerts
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestampStr)
                val alertTime = date?.time ?: 0
                val fiveMinsAgo = System.currentTimeMillis() - (5 * 60 * 1000)
                
                if (alertTime > fiveMinsAgo) {
                     val senderId = (data["senderId"] as? String)?.trim() ?: ""
                     val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
                     val myHandle = sharedPref.getString("MY_HANDLE", "")?.trim() ?: ""
                     
                     if (senderId.isNotEmpty() && senderId != myHandle) {
                         runOnUiThread { showAlertDialog(data) }
                     }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        alertListenerMap[communityId!!] = listener
    }     

    private fun showAlertDialog(data: Map<String, Any>) {
         val sender = data["senderId"] ?: "Unknown"
         val senderPhone = data["senderPhone"] as? String ?: ""
         val location = data["location"] ?: "Unknown Location"
         val mapLink = "https://maps.google.com/?q=${data["rawLat"]},${data["rawLon"]}" // Simple link reconstruction
         
         val message = if (senderPhone.isNotEmpty()) {
             "User: $sender\nPhone: $senderPhone\n\nLocation: $location\n\nTime: ${data["timestamp"]}"
         } else {
             "User: $sender\n\nLocation: $location\n\nTime: ${data["timestamp"]}"
         }

         val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("EMERGENCY ALERT")
            .setMessage(message)
            .setPositiveButton("OPEN MAP") { _, _ ->
                 val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapLink))
                 startActivity(intent)
            }
            .setNegativeButton("DISMISS", null)
         
         if (senderPhone.isNotEmpty()) {
             builder.setNeutralButton("CALL USER") { _, _ ->
                 val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$senderPhone"))
                 startActivity(intent)
             }
         }
         
         builder.show()
    }
    override fun onDestroy() {
        super.onDestroy()
        alertListenerMap.forEach { (community, listener) ->
            FirebaseManager.removeAlertListener(community, listener)
        }
        alertListenerMap.clear()
    }
}