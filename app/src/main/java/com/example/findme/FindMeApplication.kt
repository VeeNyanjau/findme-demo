package com.example.findme

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import android.util.Log

class FindMeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
                if (BuildConfig.FIREBASE_API_KEY.contains("your_") || BuildConfig.FIREBASE_APP_ID.contains("your_")) {
                    Log.e("FindMeApp", "ERROR: Firebase Config is missing. Please populate .env file.")
                    // Make it obvious why it failed, but don't crash loop if possible, or show UI?
                    // throw RuntimeException("FindMe Configuration Error: Please fill in the .env file with your Firebase credentials.")
                }

                val options = FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .build()
                
                
                FirebaseApp.initializeApp(this, options)
                Log.d("FindMeApp", "Firebase initialized manually")
        } catch (e: Exception) {
            Log.e("FindMeApp", "Failed to initialize Firebase", e)
            // Rethrow runtime exceptions to ensure developer sees the config error
            if (e is RuntimeException) throw e
        }
    }
}
