package com.example.data.sync

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DriveRecoveryTest {
    @Test fun pinValidationKeepsLeadingZeroesAndEnforcesFourToTenDigits() {
        listOf("0001", "0123456789").forEach { assertTrue(DriveRecoveryMethod.PIN.accepts(it.toCharArray())) }
        listOf("", "123", "12345678901", "123a", "12 34", "-1234", "\u0661\u0662\u0663\u0664").forEach { assertFalse(DriveRecoveryMethod.PIN.accepts(it.toCharArray())) }
    }

    @Test fun pinEnvelopeRoundTripsAndRejectsWrongCredentials() {
        val key = SyncCrypto.newDataKey()
        val envelope = DriveRecoveryEnvelope.create("0001".toCharArray(), DriveRecoveryMethod.PIN, key, "folder-123")
        val restored = DriveRecoveryEnvelope.decode(envelope.encode())
        assertEquals(DriveRecoveryMethod.PIN, restored.method)
        assertArrayEquals(key, restored.unlock("0001".toCharArray()))
        assertNull(restored.unlock("1".toCharArray()))
        assertNull(restored.unlock("0002".toCharArray()))
        assertEquals("folder-123", restored.folderId)
        assertNotEquals("mynotes.templates.json", restored.templatesName)
    }

    @Test fun legacyPassphraseEnvelopesStillUnlock() {
        val key = SyncCrypto.newDataKey()
        val salt = SyncCrypto.newSalt()
        val derived = SyncCrypto.deriveKeyFromPassphrase("old phrase".toCharArray(), salt)
        val json = JSONObject().put("version", 1).put("salt", SyncCrypto.encodeBase64(salt))
            .put("wrappedDek", SyncCrypto.encodeBase64(SyncCrypto.wrapDataKey(key, derived))).toString()
        val legacy = DriveRecoveryEnvelope.decode(json)
        assertArrayEquals(key, legacy.unlock("old phrase".toCharArray()))
        assertEquals("mynotes.reminders.json", legacy.remindersName)
        assertEquals(DriveRecoveryMethod.PASSPHRASE, legacy.method)
        derived.fill(0)
    }

    @Test fun invalidOrFutureMetadataCannotBeUsedForRecovery() {
        val envelope = DriveRecoveryEnvelope.create("long phrase".toCharArray(), DriveRecoveryMethod.PASSPHRASE, SyncCrypto.newDataKey(), "folder")
        listOf(
            JSONObject(envelope.encode()).put("version", 3),
            JSONObject(envelope.encode()).put("iterations", Int.MAX_VALUE),
            JSONObject(envelope.encode()).put("folderId", "../outside"),
            JSONObject(envelope.encode()).put("method", "UNKNOWN"),
        ).forEach { value -> assertThrows(IllegalArgumentException::class.java) { DriveRecoveryEnvelope.decode(value.toString()) } }
    }
}