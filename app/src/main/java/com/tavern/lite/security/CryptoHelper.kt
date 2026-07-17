package com.tavern.lite.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoHelper @Inject constructor() {

    companion object {
        private const val KEY_ALIAS = "tavern_api_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128
    }

    // 惰性建密钥：init{} 同步 keystore IPC + 首启 generateKey(几十~几百ms) 原本卡在冷启动首帧路径
    // （CryptoHelper 经 SettingsStore→MainActivity 字段注入在主线程构造）。改为首次 encrypt/decrypt
    // 时按需建，那时已在 DataStore flow mapper 的 IO 线程。加锁防首启并发两路 crypto 交错建密钥（X2 审计 Med）。
    private val keyLock = Any()

    private fun ensureKeyExists() = synchronized(keyLock) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()
        }
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // iv (12 bytes) + encrypted data → base64
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String {
        val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, IV_SIZE)
        val encrypted = decoded.copyOfRange(IV_SIZE, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_SIZE, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    /**
     * 尝试解密，失败则返回 null（用于判断是否为加密数据）
     */
    fun tryDecrypt(cipherText: String): String? {
        return try {
            decrypt(cipherText)
        } catch (e: Exception) {
            Log.w("CryptoHelper", "解密失败，可能是未加密数据或密钥损坏", e)
            null
        }
    }

    private fun getKey(): SecretKey {
        ensureKeyExists()
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getKey(KEY_ALIAS, null) as SecretKey
    }
}
