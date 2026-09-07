package com.example.ui.lock

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.BrandGradientButton
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.brandGradientHorizontal
import com.example.ui.theme.neumorphicRaised

private val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

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
 * - Fails *open* if the device has no biometric or screen lock enrolled, so the
 *   user is never locked out of their own encrypted notes.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val activity = LocalContext.current.findFragmentActivity()
    if (activity == null) {
        content()
        return
    }

    // Whether the lock was already ON when this gate first appeared. If the user turns
    // it on later, this stays false, so the current session is never interrupted.
    val lockedAtStart = rememberSaveable { enabled }
    var unlocked by rememberSaveable { mutableStateOf(!lockedAtStart) }
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
                    unlocked = true
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
        if (unlocked || promptShowing.value) return
        if (!canAuthenticate(activity)) {
            unlocked = true
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
                    if (currentEnabled && !activity.isChangingConfigurations) unlocked = false

                Lifecycle.Event.ON_RESUME ->
                    if (currentEnabled && !unlocked) authenticate()

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!enabled || unlocked) {
        content()
    } else {
        LockScreen(error = error, onUnlock = { authenticate() })
    }
}

@Composable
private fun LockScreen(error: String?, onUnlock: () -> Unit) {
    val neu = LocalNeuColors.current
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
