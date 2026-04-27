package com.digitalgram.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.digitalgram.android.data.AppSettings
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth

class DropboxAuthActivity : AppCompatActivity() {

    private var authStarted = false
    private var resumeCount = 0
    private lateinit var settings: AppSettings

    companion object {
        const val RESULT_AUTH_SUCCESS = 100
        const val RESULT_AUTH_FAILED = 101
        const val EXTRA_ACCESS_TOKEN = "access_token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings.getInstance(this)
        authStarted = savedInstanceState?.getBoolean("auth_started", false) ?: false
        resumeCount = savedInstanceState?.getInt("resume_count", 0) ?: 0

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            handleOAuthRedirect(intent.data!!)
        } else if (!authStarted) {
            startDropboxAuth()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            handleOAuthRedirect(intent.data!!)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("auth_started", authStarted)
        outState.putInt("resume_count", resumeCount)
    }

    private fun startDropboxAuth() {
        val appKey = BuildConfig.DROPBOX_APP_KEY.trim()

        // Clear any legacy long-lived token before starting fresh PKCE flow
        if (settings.dropboxAccessToken.isNotEmpty()) {
            settings.dropboxAccessToken = ""
        }

        authStarted = true
        val requestConfig = DbxRequestConfig.newBuilder("DigitalGram").build()

        try {
            Auth.startOAuth2PKCE(this, appKey, requestConfig)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.w("DropboxAuth", "auth start failed", e)
            setResult(RESULT_AUTH_FAILED)
            finish()
        }
    }

    private fun handleOAuthRedirect(uri: Uri) {
        if (BuildConfig.DEBUG) android.util.Log.w("DropboxAuth", "oauth redirect received")

        android.os.Handler(mainLooper).postDelayed({
            checkAuthResult()
        }, 500)
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        if (BuildConfig.DEBUG) android.util.Log.w("DropboxAuth", "onResume count=$resumeCount authStarted=$authStarted")
    }

    private fun checkAuthResult() {
        val credential = Auth.getDbxCredential()

        if (credential?.accessToken != null) {
            if (BuildConfig.DEBUG) android.util.Log.w("DropboxAuth", "pkce auth succeeded")
            returnSuccess(credential.accessToken)
            return
        }

        if (BuildConfig.DEBUG) android.util.Log.w("DropboxAuth", "auth result: no credential")
        setResult(RESULT_AUTH_FAILED)
        finish()
    }

    private fun returnSuccess(token: String) {
        settings.dropboxAccessToken = token

        val resultIntent = Intent().apply {
            putExtra(EXTRA_ACCESS_TOKEN, token)
        }
        setResult(RESULT_AUTH_SUCCESS, resultIntent)
        finish()
    }
}
