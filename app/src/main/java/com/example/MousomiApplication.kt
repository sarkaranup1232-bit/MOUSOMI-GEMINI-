package com.example

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MousomiApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    runCatching {
      if (FirebaseApp.getApps(this).isEmpty()) {
        FirebaseApp.initializeApp(this)
      }
    }
  }
}
