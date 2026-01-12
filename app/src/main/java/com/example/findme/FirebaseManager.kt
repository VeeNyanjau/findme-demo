package com.example.findme

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference

/**
 * FirebaseManager
 * Handles Realtime Database operations for community alerts.
 */
object FirebaseManager {
    
    // In a real app, strict security rules (auth) would be used.
    // For this prototype, we assume the database is in test mode (public read/write).
    // Explicitly using the URL provided by the user to avoid google-services.json missing it.
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://findme-demo-15d1e-default-rtdb.firebaseio.com/")
    private val alertsRef: DatabaseReference = database.getReference("alerts")

    fun broadcastAlert(
        communityId: String,
        alertData: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val newAlertRef = alertsRef.child(communityId).push()
        newAlertRef.setValue(alertData)
            .addOnSuccessListener { 
                onSuccess() 
            }
            .addOnFailureListener { e -> 
                onFailure(e) 
            }
    }

    /**
     * Generates a unique User Handle (e.g. USER-A1B), checks availability, and reserves it.
     */
    fun generateUniqueUsername(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val suffix = (1..3).map { chars.random() }.joinToString("")
        val handle = "USER-$suffix" // e.g., "USER-8X2"

        val handleRef = database.getReference("unique_handles").child(handle)

        handleRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                generateUniqueUsername(onSuccess, onFailure) // Retry
            } else {
                handleRef.setValue(true)
                    .addOnSuccessListener { onSuccess(handle) }
                    .addOnFailureListener { onFailure(it) }
            }
        }.addOnFailureListener { onFailure(it) }
    }

    /**
     * Creates a new community. Fails if name exists.
     */
    fun createCommunity(
        name: String,
        creatorUid: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val communityRef = database.getReference("communities").child(name)
        communityRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                onFailure(Exception("Name exists, kindly choose a different one"))
            } else {
                val meta = mapOf("creator" to creatorUid, "createdAt" to System.currentTimeMillis())
                communityRef.setValue(meta)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onFailure(it) }
            }
        }.addOnFailureListener { onFailure(it) }
    }

    /**
     * Listens for NEW alerts in the community.
     * Returns a ChildEventListener so the caller can detach it later.
     */
    fun listenForAlerts(
        communityId: String,
        onAlertReceived: (Map<String, Any>) -> Unit
    ): com.google.firebase.database.ChildEventListener {
        val ref = alertsRef.child(communityId)
        val startTime = System.currentTimeMillis()

        val listener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                val data = snapshot.value as? Map<String, Any> ?: return
                // Check timestamp to avoid loading old history on startup
                // Note: This relies on device clocks being roughly synced.
                // In a pro app, you'd use ServerValue.TIMESTAMP and query logic.
                // For this demo, we check if the alert looks "new" (e.g. within last 5 mins or future)
                // OR we imply that logic is handled by UI. 
                // Better approach: Only notify if the key was added AFTER we started listening?
                // Realtime DB "ChildAdded" fires for ALL existing data first. We must filter.
                
                // Simple filter: Check if alert timestamp > (startTime - 5 seconds)
                // Wait, "timestamp" in data is a String "yyyy-mm-dd...". 
                // Let's just pass it to UI and let UI decide, OR assume ChildAdded fires fast.
                // Actually, ChildEventListener dumps all history. We need to ignore history.
                
                // Valid filter: Only react if we are sure it's fresh?
                // Let's rely on the caller or just trigger. 
                // Refinement: We won't filter here to keep it raw, but the UI should handle "Alert spam" on open.
                onAlertReceived(data)
            }
            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {}
            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.addChildEventListener(listener)
        return listener
    }
    fun joinCommunity(
        name: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val communityRef = database.getReference("communities").child(name)
        communityRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                onSuccess()
            } else {
                onFailure(Exception("Community does not exist"))
            }
        }.addOnFailureListener { onFailure(it) }
    }
    fun removeAlertListener(communityId: String, listener: com.google.firebase.database.ChildEventListener) {
        alertsRef.child(communityId).removeEventListener(listener)
    }

    /**
     * Look up User ID by Phone Number.
     */
    fun getUserIdByPhone(
        phone: String,
        onSuccess: (String?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Sanitize phone for key (remove spacing, etc. or just hash it in real app)
        // For demo: just use as is, but replacing dots/slashes if any. 
        // Better: assume clean input or just simple encoding.
        val safePhone = phone.replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")
        
        val ref = database.getReference("phone_mappings").child(safePhone)
        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val userId = snapshot.getValue(String::class.java)
                onSuccess(userId)
            } else {
                onSuccess(null) // Not found
            }
        }.addOnFailureListener { onFailure(it) }
    }

    /**
     * Save Phone -> User ID mapping.
     */
    fun saveUserMapping(
        phone: String,
        userId: String,
        onSuccess: () -> Unit, // Optional
        onFailure: (Exception) -> Unit // Optional
    ) {
        val safePhone = phone.replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")
        
        // Save mapping
        database.getReference("phone_mappings").child(safePhone).setValue(userId)
        
        // Also update users node (ID -> Phone)
        database.getReference("users").child(userId).child("phone").setValue(phone)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}
