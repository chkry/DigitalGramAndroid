package com.digitalgram.android

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.databinding.ActivityPasscodeBinding
import java.util.concurrent.Executor

class PasscodeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPasscodeBinding
    private lateinit var settings: AppSettings
    private lateinit var executor: Executor
    
    private var currentPasscode = StringBuilder()
    private var firstPasscode = ""
    private var mode = PasscodeMode.VERIFY
    private var isConfirming = false
    private var attempts = 0
    
    private val dotViews = mutableListOf<View>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasscodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settings = AppSettings.getInstance(this)
        executor = ContextCompat.getMainExecutor(this)
        
        mode = PasscodeMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: PasscodeMode.VERIFY.name)
        
        applyThemeColors()
        setupUI()
        setupKeypad()
        setupBackPressHandler()
        
        // Show biometric prompt for VERIFY mode if fingerprint is enabled
        if (mode == PasscodeMode.VERIFY && settings.fingerprintEnabled) {
            showBiometricPrompt()
        }
    }
    
    private fun applyThemeColors() {
        val themeColors = com.digitalgram.android.util.ThemeColors.getTheme(settings.theme, this)
        
        // Apply background color
        binding.root.setBackgroundColor(themeColors.backgroundColor)
        
        // Apply text colors
        binding.titleText.setTextColor(themeColors.textColor)
        
        // Apply button colors (number pad)
        val buttonViews = listOf(
            binding.key0, binding.key1, binding.key2, binding.key3,
            binding.key4, binding.key5, binding.key6, binding.key7,
            binding.key8, binding.key9, binding.keyDelete
        )
        buttonViews.forEach { button ->
            button.setTextColor(themeColors.textColor)
        }
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == PasscodeMode.VERIFY && intent.getBooleanExtra(EXTRA_LOCK_SCREEN, false)) {
                    // Don't allow back press on lock screen
                    return
                }
                // Allow normal back behavior
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }
    
    private fun setupUI() {
        dotViews.addAll(listOf(
            binding.dot1, binding.dot2, binding.dot3, binding.dot4
        ))
        
        updateTitle()
        updateDots()
        
        // Hide biometric button if not in verify mode or not available
        binding.biometricButton.visibility = if (mode == PasscodeMode.VERIFY && settings.fingerprintEnabled) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        
        binding.biometricButton.setOnClickListener {
            showBiometricPrompt()
        }
    }
    
    private fun updateTitle() {
        binding.titleText.text = when {
            mode == PasscodeMode.SET && !isConfirming -> getString(R.string.set_passcode)
            mode == PasscodeMode.SET && isConfirming -> getString(R.string.confirm_passcode)
            mode == PasscodeMode.CHANGE && firstPasscode.isEmpty() -> getString(R.string.enter_passcode)
            mode == PasscodeMode.CHANGE && !isConfirming -> getString(R.string.set_passcode)
            mode == PasscodeMode.CHANGE && isConfirming -> getString(R.string.confirm_passcode)
            else -> getString(R.string.enter_passcode)
        }
    }
    
    private fun setupKeypad() {
        val buttons = listOf(
            binding.key1, binding.key2, binding.key3,
            binding.key4, binding.key5, binding.key6,
            binding.key7, binding.key8, binding.key9,
            binding.key0
        )
        
        buttons.forEachIndexed { index, button ->
            val digit = if (index == 9) "0" else (index + 1).toString()
            button.text = digit
            button.setOnClickListener { onDigitPressed(digit) }
        }
        
        binding.keyDelete.setOnClickListener { onDeletePressed() }
        binding.keyCancel.setOnClickListener { 
            setResult(RESULT_CANCELED)
            finish() 
        }
    }
    
    private fun onDigitPressed(digit: String) {
        if (currentPasscode.length < 4) {
            currentPasscode.append(digit)
            updateDots()
            
            if (currentPasscode.length == 4) {
                validatePasscode()
            }
        }
    }
    
    private fun onDeletePressed() {
        if (currentPasscode.isNotEmpty()) {
            currentPasscode.deleteCharAt(currentPasscode.length - 1)
            updateDots()
        }
    }
    
    private fun updateDots() {
        dotViews.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index < currentPasscode.length) 
                    R.drawable.passcode_dot_filled 
                else 
                    R.drawable.passcode_dot_empty
            )
        }
    }
    
    private fun validatePasscode() {
        when (mode) {
            PasscodeMode.SET -> handleSetMode()
            PasscodeMode.VERIFY -> handleVerifyMode()
            PasscodeMode.CHANGE -> handleChangeMode()
        }
    }
    
    private fun handleSetMode() {
        if (!isConfirming) {
            firstPasscode = currentPasscode.toString()
            isConfirming = true
            currentPasscode.clear()
            updateTitle()
            updateDots()
        } else {
            if (currentPasscode.toString() == firstPasscode) {
                settings.passcode = firstPasscode
                setResult(RESULT_OK)
                finish()
            } else {
                showError(getString(R.string.passcode_mismatch))
                firstPasscode = ""
                isConfirming = false
                currentPasscode.clear()
                updateTitle()
                updateDots()
            }
        }
    }
    
    private fun handleVerifyMode() {
        if (settings.verifyPasscode(currentPasscode.toString())) {
            setResult(RESULT_OK)
            finish()
        } else {
            attempts++
            showError(getString(R.string.wrong_passcode))
            currentPasscode.clear()
            updateDots()
            
            if (attempts >= MAX_ATTEMPTS) {
                Toast.makeText(this, "Too many attempts", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    private fun handleChangeMode() {
        // First verify current passcode
        if (firstPasscode.isEmpty() && !isConfirming) {
            if (settings.verifyPasscode(currentPasscode.toString())) {
                // Current passcode verified, now set new one
                firstPasscode = "verified"
                currentPasscode.clear()
                updateTitle()
                updateDots()
            } else {
                showError(getString(R.string.wrong_passcode))
                currentPasscode.clear()
                updateDots()
            }
        } else if (firstPasscode == "verified" && !isConfirming) {
            // Store new passcode for confirmation
            firstPasscode = currentPasscode.toString()
            isConfirming = true
            currentPasscode.clear()
            updateTitle()
            updateDots()
        } else if (isConfirming) {
            // Confirm new passcode
            if (currentPasscode.toString() == firstPasscode) {
                settings.passcode = firstPasscode
                setResult(RESULT_OK)
                finish()
            } else {
                showError(getString(R.string.passcode_mismatch))
                firstPasscode = "verified"
                isConfirming = false
                currentPasscode.clear()
                updateTitle()
                updateDots()
            }
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // Animate shake
        binding.dotsContainer.animate()
            .translationX(-20f).setDuration(50)
            .withEndAction {
                binding.dotsContainer.animate()
                    .translationX(20f).setDuration(50)
                    .withEndAction {
                        binding.dotsContainer.animate()
                            .translationX(0f).setDuration(50)
                            .start()
                    }
                    .start()
            }
            .start()
    }
    
    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return
        }
        
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    setResult(RESULT_OK)
                    finish()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // User can still try passcode
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // User can still try passcode
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle(getString(R.string.use_fingerprint))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(getString(R.string.passcode))
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_LOCK_SCREEN = "lock_screen"
        const val MAX_ATTEMPTS = 5
    }
}
