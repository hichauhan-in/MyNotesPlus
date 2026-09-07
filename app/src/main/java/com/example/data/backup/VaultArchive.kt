package com.example.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object VaultArchive {
    const val EXTENSION = "mynotesbackup"
    const val MIN_PASSPHRASE = 12
    const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRIES = 20_001
    private val magic = byteArrayOf(77, 89, 78, 86, 65, 85, 76, 49)
    private val random = SecureRandom()

    data class Entry(val name: String, val bytes: () -> ByteArray)

    fun write(output: OutputStream, passphrase: CharArray, entries: List<Entry>) {
        try { writeRecords(output, passphrase, entries) } finally { passphrase.fill('\u0000') }
    }

    private fun writeRecords(output: OutputStream, passphrase: CharArray, entries: List<Entry>) {
        require(passphrase.size >= MIN_PASSPHRASE) { "Use at least 12 characters" }
        validateNames(entries.map { it.name })
        val salt = ByteArray(16).also(random::nextBytes)
        val key = derive(passphrase, salt)
        try {
            val target = DataOutputStream(output)
            target.write(magic)
            target.write(salt)
            target.writeInt(entries.size)
            var total = 0L
            entries.forEachIndexed { index, entry ->
                val bytes = entry.bytes()
                require(bytes.size <= MAX_ENTRY_BYTES) { "A backup item exceeds the 32 MB limit" }
                total += bytes.size
                require(total <= MAX_TOTAL_BYTES) { "The backup exceeds the 512 MB limit" }
                val payload = ByteArrayOutputStream().also { buffer ->
                    DataOutputStream(buffer).apply { writeUTF(entry.name); write(bytes) }
                }.toByteArray()
                val iv = ByteArray(12).also(random::nextBytes)
                val cipher = cipher(Cipher.ENCRYPT_MODE, key, iv, entries.size, index)
                val encrypted = cipher.doFinal(payload)
                target.writeInt(encrypted.size)
                target.write(iv)
                target.write(encrypted)
            }
            target.flush()
        } finally {
            key.fill(0)
            passphrase.fill('\u0000')
        }
    }

    fun read(input: InputStream, passphrase: CharArray, onEntry: (String, ByteArray) -> Unit) {
        try { readRecords(input, passphrase, onEntry) } finally { passphrase.fill('\u0000') }
    }

    private fun readRecords(input: InputStream, passphrase: CharArray, onEntry: (String, ByteArray) -> Unit) {
        val source = DataInputStream(input)
        val signature = ByteArray(magic.size).also(source::readFully)
        require(signature.contentEquals(magic)) { "Not a MyNotes+ backup" }
        val salt = ByteArray(16).also(source::readFully)
        val count = source.readInt()
        require(count in 1..MAX_ENTRIES) { "Invalid backup record count" }
        val key = derive(passphrase, salt)
        try {
            val names = mutableSetOf<String>()
            var total = 0L
            repeat(count) { index ->
                val size = source.readInt()
                require(size in 18..(MAX_ENTRY_BYTES + 256)) { "Invalid backup record size" }
                val iv = ByteArray(12).also(source::readFully)
                val encrypted = ByteArray(size).also(source::readFully)
                val payload = cipher(Cipher.DECRYPT_MODE, key, iv, count, index).doFinal(encrypted)
                val record = DataInputStream(ByteArrayInputStream(payload))
                val name = record.readUTF()
                require(index != 0 || name == "manifest") { "The backup manifest must be first" }
                require(validName(name) && names.add(name)) { "Duplicate or invalid backup record" }
                val bytes = record.readBytes()
                require(bytes.size <= MAX_ENTRY_BYTES) { "A backup item exceeds the 32 MB limit" }
                total += bytes.size
                require(total <= MAX_TOTAL_BYTES) { "The backup exceeds the 512 MB limit" }
                onEntry(name, bytes)
            }
            require(source.read() == -1) { "Unexpected data after the backup" }
            require("manifest" in names) { "Backup manifest is missing" }
        } finally {
            key.fill(0)
            passphrase.fill('\u0000')
        }
    }

    private fun validateNames(names: List<String>) {
        require(names.size in 1..MAX_ENTRIES && names.first() == "manifest")
        require(names.all(::validName) && names.distinct().size == names.size)
    }

    private fun validName(name: String): Boolean = name.matches(Regex("[a-z][a-z0-9-]{0,79}"))

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, 1_300_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray, count: Int, index: Int): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(magic + ByteBuffer.allocate(8).putInt(count).putInt(index).array())
        }
}