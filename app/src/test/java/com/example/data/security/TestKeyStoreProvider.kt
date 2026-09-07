package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

class TestKeyStoreProvider : Provider("AndroidKeyStore", 1.0, "In-memory test keystore") {
    init {
        put("KeyStore.AndroidKeyStore", TestKeyStore::class.java.name)
        put("KeyGenerator.AES", TestKeyGenerator::class.java.name)
    }

    companion object {
        internal val keys = ConcurrentHashMap<String, SecretKey>()
        fun install() { if (Security.getProvider("AndroidKeyStore") == null) Security.addProvider(TestKeyStoreProvider()) }
    }
}

class TestKeyGenerator : KeyGeneratorSpi() {
    private var alias = ""
    override fun engineInit(random: SecureRandom?) = Unit
    override fun engineInit(size: Int, random: SecureRandom?) = Unit
    override fun engineInit(parameters: AlgorithmParameterSpec, random: SecureRandom?) {
        alias = (parameters as KeyGenParameterSpec).keystoreAlias
    }
    override fun engineGenerateKey(): SecretKey {
        val key = KeyGenerator.getInstance("AES", "SunJCE").apply { init(256) }.generateKey()
        return TestKeyStoreProvider.keys.putIfAbsent(alias, key) ?: key
    }
}

class TestKeyStore : KeyStoreSpi() {
    override fun engineGetKey(alias: String, password: CharArray?): Key? = TestKeyStoreProvider.keys[alias]
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCreationDate(alias: String): Date? = if (engineContainsAlias(alias)) Date(0) else null
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) { TestKeyStoreProvider.keys[alias] = key as SecretKey }
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) = throw UnsupportedOperationException()
    override fun engineSetCertificateEntry(alias: String, certificate: Certificate) = throw UnsupportedOperationException()
    override fun engineDeleteEntry(alias: String) { TestKeyStoreProvider.keys.remove(alias) }
    override fun engineAliases(): Enumeration<String> = Collections.enumeration(TestKeyStoreProvider.keys.keys)
    override fun engineContainsAlias(alias: String): Boolean = TestKeyStoreProvider.keys.containsKey(alias)
    override fun engineSize(): Int = TestKeyStoreProvider.keys.size
    override fun engineIsKeyEntry(alias: String): Boolean = engineContainsAlias(alias)
    override fun engineIsCertificateEntry(alias: String): Boolean = false
    override fun engineGetCertificateAlias(certificate: Certificate): String? = null
    override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}