package com.example.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultArchiveTest {
    private fun password() = "a long test passphrase".toCharArray()

    private fun archive(): ByteArray = ByteArrayOutputStream().also { output ->
        VaultArchive.write(output, password(), listOf(
            VaultArchive.Entry("manifest") { "private note title and books".toByteArray() },
            VaultArchive.Entry("media-0") { byteArrayOf(1, 2, 3, 4) },
        ))
    }.toByteArray()

    @Test fun restoresAllRecordsWithoutPlaintextInTheFile() {
        val encrypted = archive()
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("private note title"))
        val restored = linkedMapOf<String, ByteArray>()
        VaultArchive.read(ByteArrayInputStream(encrypted), password()) { name, data -> restored[name] = data }
        assertArrayEquals("private note title and books".toByteArray(), restored["manifest"])
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), restored["media-0"])
    }

    @Test fun wrongPassphraseNeverDeliversARecord() {
        var delivered = false
        val encrypted = archive()
        assertThrows(Exception::class.java) {
            VaultArchive.read(ByteArrayInputStream(encrypted), "not the password".toCharArray()) { _, _ -> delivered = true }
        }
        assertFalse(delivered)
    }

    @Test fun tamperingAndTruncationAreRejected() {
        val encrypted = archive()
        val modified = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        listOf(modified, encrypted.copyOf(encrypted.size - 1), encrypted + byteArrayOf(0)).forEach { invalid ->
            assertThrows(Exception::class.java) { VaultArchive.read(ByteArrayInputStream(invalid), password()) { _, _ -> } }
        }
    }

    @Test fun duplicateAndPathLikeNamesAreRejected() {
        listOf("../secret", "media/0", "manifest").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                VaultArchive.write(ByteArrayOutputStream(), password(), listOf(
                    VaultArchive.Entry("manifest") { byteArrayOf() }, VaultArchive.Entry(name) { byteArrayOf() },
                ))
            }
        }
    }

    @Test fun oversizedDeclaredRecordIsRejectedBeforeAllocation() {
        val encrypted = archive()
        java.nio.ByteBuffer.wrap(encrypted, 28, 4).putInt(Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) {
            VaultArchive.read(ByteArrayInputStream(encrypted), password()) { _, _ -> }
        }
    }

    @Test fun successfulWriteClearsTheSuppliedPassphrase() {
        val passphrase = password()
        VaultArchive.write(ByteArrayOutputStream(), passphrase, listOf(VaultArchive.Entry("manifest") { byteArrayOf() }))
        assertTrue(passphrase.all { it == '\u0000' })
    }
}