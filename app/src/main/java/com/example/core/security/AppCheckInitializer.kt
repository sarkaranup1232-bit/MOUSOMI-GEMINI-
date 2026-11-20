package com.example.core.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent? = null) {
  try {
    val appContext = context.applicationContext
    if (FirebaseApp.getApps(appContext).isEmpty()) {
      FirebaseApp.initializeApp(appContext)
    }

    val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
      ?: runCatching {
        BuildConfig::class.java.getField(DEBUG_TOKEN_EXTRA).get(null) as? String
      }.getOrNull()

    if (BuildConfig.DEBUG || !debugToken.isNullOrEmpty()) {
      if (!debugToken.isNullOrEmpty()) {
        val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }
          .getOrNull() ?: "[DEFAULT]"
        val prefs = appContext.getSharedPreferences(
          "$DEBUG_STORE_PREFIX$persistenceKey",
          Context.MODE_PRIVATE,
        )
        prefs.edit().putString(DEBUG_SECRET_KEY, debugToken).commit()
        Log.d(TAG, "App Check debug secret persisted synchronously.")
      } else {
        Log.w(TAG, "No FIREBASE_APPCHECK_DEBUG_TOKEN extra found; using default debug factory.")
      }
      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
      )
    } else {
      val siteKeyResId = appContext.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        appContext.packageName,
      )
      if (siteKeyResId != 0) {
        val siteKey = appContext.getString(siteKeyResId)
        if (siteKey.isNotBlank()) {
          FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
          )
        } else {
          Log.e(TAG, "recaptcha_site_key resource string is blank.")
        }
      } else {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
      }
    }
  } catch (e: Exception) {
    Log.e(TAG, "Failed to initialize Firebase App Check", e)
  }
}
