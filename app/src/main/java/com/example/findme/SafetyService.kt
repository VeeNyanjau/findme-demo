package com.example.findme

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SafetyService
 * Background service to monitor Firebase alerts even when the app is minimized.
 */
class SafetyService : Service() {

    companion object {
        const val CHANNEL_ID = "SAFETY_MONITOR_CHANNEL"
        const val EMERGENCY_CHANNEL_ID = "EMERGENCY_CHANNEL"
        const val NOTIFICATION_ID = 999
    }

    private var communityId: String? = null
    private var alertListener: com.google.firebase.database.ChildEventListener? = null
    // Use the same filtering logic as Activity
    private val sessionStartTime = System.currentTimeMillis()

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not binding, just starting
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start Foreground immediately
        val notification = createMonitoringNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Load Community ID
        val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
        val newCommunityId = sharedPref.getString("COMMUNITY_ID", null)

        if (newCommunityId != communityId) {
            // Community changed or starting fresh
            stopListening()
            communityId = newCommunityId
            startListening()
        }

        // If no community, we just run idle (or stopSelf? Better to keep running to show user we are "Ready")
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }

    private fun startListening() {
        if (communityId.isNullOrEmpty()) return

        alertListener = FirebaseManager.listenForAlerts(communityId!!) { data ->
            processAlert(data)
        }
    }

    private fun stopListening() {
        if (communityId != null && alertListener != null) {
            FirebaseManager.removeAlertListener(communityId!!, alertListener!!)
            alertListener = null
        }
    }

    private fun processAlert(data: Map<String, Any>) {
        val timestampStr = data["timestamp"] as? String ?: return
        try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestampStr)
            val alertTime = date?.time ?: 0
            
            // Sync logic with MainActivity: Last Seen + 5 min window
            val sharedPref = getSharedPreferences("SafetyAppPrefs", Context.MODE_PRIVATE)
            val lastSeenTime = sharedPref.getLong("LAST_ALERT_TIMESTAMP", 0)
            val fiveMinsAgo = System.currentTimeMillis() - (5 * 60 * 1000)
            
            // Strict filter again, ensuring Service respects same rules
            val threshold = if (lastSeenTime > fiveMinsAgo) lastSeenTime else fiveMinsAgo

            if (alertTime > threshold) {
                 // Self Check
                 val senderId = (data["senderId"] as? String)?.trim() ?: ""
                 val myHandle = sharedPref.getString("MY_HANDLE", "")?.trim() ?: ""

                 if (senderId.isNotEmpty() && senderId != myHandle) {
                     // Update Last Seen (This might race with MainActivity but that's safe, both just bump it up)
                     sharedPref.edit().putLong("LAST_ALERT_TIMESTAMP", alertTime).apply()
                     
                     triggerSystemNotification(data)
                 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createMonitoringNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FindMe Active")
            .setContentText("Monitoring for community alerts...")
            .setSmallIcon(android.R.drawable.ic_menu_compass) 
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Min priority hides it from status bar on many versions
            .build()
    }

    private fun triggerSystemNotification(data: Map<String, Any>) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return 
        }

        val sender = data["senderId"] ?: "Unknown"
        val location = data["location"] ?: "Unknown Location"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("EMERGENCY: $sender")
            .setContentText("Help needed at $location")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Monitor Channel (Min Priority - silent and minimized)
            val monitorChannel = NotificationChannel(CHANNEL_ID, "Safety Monitor", NotificationManager.IMPORTANCE_MIN)
            monitorChannel.description = "Persistent notification for background monitoring"
            
            // Emergency Channel (High Priority)
            val emergencyChannel = NotificationChannel(EMERGENCY_CHANNEL_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH)
            emergencyChannel.description = "Critical alerts from your community"
            
            manager.createNotificationChannel(monitorChannel)
            manager.createNotificationChannel(emergencyChannel)
        }
    }
}
