package com.digitalgram.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.digitalgram.android.data.AppSettings
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth

/**
 * Custom Dropbox OAuth handler that receives the redirect directly
 * and manages token persistence in the same activity lifecycle
 */
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
        
        android.util.Log.d("DropboxAuthActivity", "onCreate - authStarted: $authStarted, resumeCount: $resumeCount")
        android.util.Log.d("DropboxAuthActivity", "Intent: ${intent?.action}, data: ${intent?.data}")
        
        // Check if this is a redirect callback from browser
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            handleOAuthRedirect(intent.data!!)
        } else if (!authStarted) {
            startDropboxAuth()
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        android.util.Log.d("DropboxAuthActivity", "onNewIntent: ${intent.action}, data: ${intent.data}")
        
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
        android.util.Log.d("DropboxAuthActivity", "Starting auth with key: ${appKey.take(10)}...")
        
        authStarted = true
        val requestConfig = DbxRequestConfig.newBuilder("DigitalGram").build()
        
        try {
            Auth.startOAuth2PKCE(this, appKey, requestConfig)
        } catch (e: Exception) {
            android.util.Log.e("DropboxAuthActivity", "Auth start failed", e)
            setResult(RESULT_AUTH_FAILED)
            finish()
        }
    }
    
    private fun handleOAuthRedirect(uri: Uri) {
        android.util.Log.d("DropboxAuthActivity", "Handling OAuth redirect: $uri")
        android.util.Log.d("DropboxAuthActivity", "URI scheme: ${uri.scheme}, path: ${uri.path}")
        
        // Extract token directly from the URI parameters
        // The SDK uses oauth_token_secret for the actual access token in PKCE flow
        val tokenSecret = uri.getQueryParameter("oauth_token_secret")
        val oauthToken = uri.getQueryParameter("oauth_token")
        val uid = uri.getQueryParameter("uid")
        val state = uri.getQueryParameter("state")
        
        android.util.Log.d("DropboxAuthActivity", "Token secret: ${tokenSecret?.take(20)}...")
        android.util.Log.d("DropboxAuthActivity", "OAuth token: $oauthToken")
        android.util.Log.d("DropboxAuthActivity", "UID: $uid")
        android.util.Log.d("DropboxAuthActivity", "State: ${state?.take(20)}...")
        
        // Check if we got a token
        if (!tokenSecret.isNullOrEmpty()) {
            android.util.Log.d("DropboxAuthActivity", "✓ Got access token from URI!")
            returnSuccess(tokenSecret)
        } else {
            // Try to let SDK process it first
            android.os.Handler(mainLooper).postDelayed({
                checkAuthResult()
            }, 1000)
        }
    }
    
    override fun onResume() {
        super.onResume()
        resumeCount++
        android.util.Log.d("DropboxAuthActivity", "onResume - authStarted: $authStarted, resumeCount: $resumeCount")
    }
    
    private fun checkAuthResult() {
        android.util.Log.d("DropboxAuthActivity", "Checking auth result...")
        
        // Try PKCE credential first
        val credential = Auth.getDbxCredential()
        android.util.Log.d("DropboxAuthActivity", "  PKCE credential: ${credential != null}")
        
        if (credential?.accessToken != null) {
            android.util.Log.d("DropboxAuthActivity", "✓ Got PKCE token!")
            returnSuccess(credential.accessToken)
            return
        }
        
        // Try legacy token
        val legacyToken = Auth.getOAuth2Token()
        android.util.Log.d("DropboxAuthActivity", "  Legacy token: ${legacyToken != null}")
        
        if (!legacyToken.isNullOrEmpty()) {
            android.util.Log.d("DropboxAuthActivity", "✓ Got legacy token!")
            returnSuccess(legacyToken)
            return
        }
        
        // No token - user may have cancelled or there was an error
        android.util.Log.w("DropboxAuthActivity", "✗ No token received")
        setResult(RESULT_AUTH_FAILED)
        finish()
    }
    
    private fun returnSuccess(token: String) {
        // Save directly here to ensure it's persisted
        settings.dropboxAccessToken = token
        
        val resultIntent = Intent().apply {
            putExtra(EXTRA_ACCESS_TOKEN, token)
        }
        setResult(RESULT_AUTH_SUCCESS, resultIntent)
        finish()
    }
}
