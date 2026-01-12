package com.example.findme

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * AuthManager
 * Handles Anonymous Authentication and User Record Creation.
 */
object AuthManager {

    private const val TAG = "AuthManager"
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://findme-demo-15d1e-default-rtdb.firebaseio.com/")
    private val usersRef = database.getReference("users")

    /**
     * Signs in anonymously if not already signed in.
     * Returns the UID via callback.
     */
    fun signInAnonymously(onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Already signed in: ${currentUser.uid}")
            // Ensure record exists or update last active
            updateUserRecord(currentUser.uid)
            onSuccess(currentUser.uid)
        } else {
            Log.d(TAG, "No user signed in. Attempting anonymous auth...")
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        Log.d(TAG, "SignIn Success: ${user.uid}")
                        createUserRecord(user.uid)
                        onSuccess(user.uid)
                    } else {
                        onFailure(Exception("User is null after success"))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "SignIn Failed", e)
                    onFailure(e)
                }
        }
    }

    private fun createUserRecord(uid: String) {
        val timestamp = System.currentTimeMillis()
        val userMap = mapOf(
            "uid" to uid,
            "createdAt" to timestamp,
            "lastActive" to timestamp
        )
        
        usersRef.child(uid).setValue(userMap)
            .addOnSuccessListener { Log.d(TAG, "User record created/updated in DB") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to write user record", e) }
    }

    private fun updateUserRecord(uid: String) {
        val timestamp = System.currentTimeMillis()
        usersRef.child(uid).child("lastActive").setValue(timestamp)
    }

    fun getCurrentUid(): String? {
        return auth.currentUser?.uid
    }
}
