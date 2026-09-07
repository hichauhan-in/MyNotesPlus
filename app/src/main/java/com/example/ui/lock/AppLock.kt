package com.example.ui.lock

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BrandGradientButton
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.brandGradientHorizontal
import com.example.ui.theme.neumorphicRaised

private val ALLOWED_AUTHENTICATORS = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    BiometricManager.Authenticators.BIOMETRIC_STRONG else BiometricManager.Authenticators.BIOMETRIC_WEAK) or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal class AppLockSession : ViewModel() {
    var initialized = false
    var unlocked by mutableStateOf(false)
}

/**
 * Gates [content] behind a biometric / device-credential prompt when [enabled].
 *
 * - Enabling the lock mid-session (e.g. toggling it in Settings) does NOT lock the
 *   current session - it takes effect from the next launch / return, so the user
 *   stays exactly where they were.
 * - Locks again whenever the app is genuinely backgrounded (ignores rotation).
 * - Re-prompts automatically every time the app returns to the foreground.
 * - The manual "Unlock" button works reliably even after the user cancels, because a
 *   single [BiometricPrompt] instance is reused (creating a new one per attempt races
 *   with the library's internal fragment and is the usual cause of a dead button).
 * - Authentication failures keep the vault closed. Unlock state survives rotation,
 *   but is never restored from saved instance state after process death.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val activity = LocalContext.current.findFragmentActivity()
    if (activity == null) {
        if (enabled) LockScreen(error = "Secure authentication is unavailable. Reopen the app.", onUnlock = {}) else content()
        return
    }

    val session: AppLockSession = viewModel()
    if (!session.initialized) {
        session.unlocked = !enabled
        session.initialized = true
    }
    var error by remember { mutableStateOf<String?>(null) }
    val promptShowing = remember { mutableStateOf(false) }
    val currentEnabled by rememberUpdatedState(enabled)

    // A single reused prompt for the whole session.
    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing.value = false
                    error = null
                    session.unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing.value = false
                    error = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        -> null

                        else -> errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    // Single mismatch (e.g. wrong finger) - keep the prompt open.
                }
            },
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MyNotes+")
            .setSubtitle("Confirm your identity to continue")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
    }

    fun authenticate() {
        if (session.unlocked || promptShowing.value) return
        if (!canAuthenticate(activity)) {
            error = "Authentication is unavailable. Check your device screen lock, then try again."
            return
        }
        error = null
        promptShowing.value = true
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            promptShowing.value = false
            error = e.message ?: "Authentication unavailable"
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    if (currentEnabled && !activity.isChangingConfigurations) session.unlocked = false

                Lifecycle.Event.ON_RESUME ->
                    if (currentEnabled && !session.unlocked) authenticate()

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!enabled || session.unlocked) {
        content()
    } else {
        LockScreen(error = error, onUnlock = { authenticate() })
    }
}

@Composable
private fun LockScreen(error: String?, onUnlock: () -> Unit) {
    val neu = LocalNeuColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .neumorphicRaised(48.dp, neu, elevation = 12.dp)
                    .clip(CircleShape)
                    .background(brandGradientHorizontal()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "MyNotes+ is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = error ?: "Unlock with your fingerprint, face or screen lock to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            BrandGradientButton(
                text = "Unlock",
                icon = Icons.Rounded.Fingerprint,
                onClick = onUnlock,
            )
            if (error != null) {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                }) { Text("Device security settings") }
            }
        }
    }
}

private fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
