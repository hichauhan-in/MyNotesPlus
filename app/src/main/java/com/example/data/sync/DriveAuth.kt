package com.example.data.sync

import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope

/**
 * Builds the Google "Authorization" request for cloud sync. We ask for two narrow scopes:
 *  - drive.file    : files this app creates/opens (the visible "MyNotes" sync folder) - non-sensitive
 *  - drive.appdata : the app's private, hidden folder that holds the wrapped encryption key
 *                    envelope - this is a *sensitive* scope, so the account must be a consent-screen
 *                    test user (or the app verified) to grant it.
 * We deliberately avoid the broad, *restricted* `drive` scope, so no Google security assessment is
 * required.
 */
object DriveAuth {
    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE), Scope(SCOPE_DRIVE_APPDATA)))
            .build()
}
