package com.tavern.lite.security

import android.util.Base64
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * CryptoHelper 测试 — mock-based API 行为验证
 *
 * 覆盖范围：
 * - encrypt: Base64 编码、Cipher 初始化参数、IV 前置拼接
 * - decrypt: Base64 解码、IV 拆分、Cipher 解密
 * - tryDecrypt: 解密失败返回 null（异常处理）
 *
 * 局限：无法验证真实 AES/GCM 加密正确性（需 Robolectric 或 instrumented test）
 */
class CryptoHelperTest {

    private lateinit var cryptoHelper: CryptoHelper
    private lateinit var mockKey: SecretKey
    private lateinit var mockCipher: Cipher
    private lateinit var mockKeyStore: KeyStore

    @Before
    fun setUp() {
        mockkStatic(KeyStore::class)
        mockkStatic(Cipher::class)
        mockkStatic(Base64::class)

        mockKey = mockk(relaxed = true)
        mockCipher = mockk(relaxed = true)
        mockKeyStore = mockk(relaxed = true)

        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.containsAlias("tavern_api_key") } returns true
        every { mockKeyStore.getKey("tavern_api_key", null) } returns mockKey
        every { Cipher.getInstance("AES/GCM/NoPadding") } returns mockCipher

        cryptoHelper = CryptoHelper()
    }

    @Test
    fun `encrypt returns base64 of iv plus encrypted data`() {
        val iv = ByteArray(12) { it.toByte() }
        val encrypted = ByteArray(16) { (it + 100).toByte() }
        val combined = iv + encrypted
        val expectedBase64 = "dGVzdC1jaXBoZXJ0ZXh0"

        every { mockCipher.init(Cipher.ENCRYPT_MODE, mockKey) } just Runs
        every { mockCipher.iv } returns iv
        every { mockCipher.doFinal("hello".toByteArray(Charsets.UTF_8)) } returns encrypted
        every { Base64.encodeToString(combined, Base64.NO_WRAP) } returns expectedBase64

        val result = cryptoHelper.encrypt("hello")

        assertEquals(expectedBase64, result)
        verify { mockCipher.init(Cipher.ENCRYPT_MODE, mockKey) }
    }

    @Test
    fun `decrypt reverses encrypt with mocked cipher`() {
        val iv = ByteArray(12) { it.toByte() }
        val encrypted = ByteArray(24) { (it + 50).toByte() }
        val combined = iv + encrypted
        val base64Cipher = "mocked-cipher-text"
        val expectedPlain = "decrypted!"

        every { Base64.decode(base64Cipher, Base64.NO_WRAP) } returns combined
        every {
            mockCipher.init(Cipher.DECRYPT_MODE, mockKey, any<GCMParameterSpec>())
        } just Runs
        every { mockCipher.doFinal(encrypted) } returns expectedPlain.toByteArray(Charsets.UTF_8)

        val result = cryptoHelper.decrypt(base64Cipher)

        assertEquals(expectedPlain, result)
    }

    @Test
    fun `tryDecrypt returns null when cipher throws`() {
        val base64Cipher = "bad-ciphertext"

        every { Base64.decode(base64Cipher, Base64.NO_WRAP) } returns ByteArray(28)
        every {
            mockCipher.init(Cipher.DECRYPT_MODE, mockKey, any<GCMParameterSpec>())
        } just Runs
        every { mockCipher.doFinal(any()) } throws javax.crypto.AEADBadTagException("tampered")

        val result = cryptoHelper.tryDecrypt(base64Cipher)

        assertNull(result)
    }

    @Test
    fun `encrypt handles empty string`() {
        val iv = ByteArray(12) { it.toByte() }
        val encrypted = ByteArray(0)
        val combined = iv
        val expectedBase64 = "empty-result"

        every { mockCipher.init(Cipher.ENCRYPT_MODE, mockKey) } just Runs
        every { mockCipher.iv } returns iv
        every { mockCipher.doFinal("".toByteArray(Charsets.UTF_8)) } returns encrypted
        every { Base64.encodeToString(combined, Base64.NO_WRAP) } returns expectedBase64

        val result = cryptoHelper.encrypt("")

        assertEquals(expectedBase64, result)
    }

    @Test
    fun `encrypt handles long string`() {
        val longString = "A".repeat(10000)
        val iv = ByteArray(12) { it.toByte() }
        val encrypted = ByteArray(10020) { it.toByte() }
        val combined = iv + encrypted
        val expectedBase64 = "long-string-encrypted"

        every { mockCipher.init(Cipher.ENCRYPT_MODE, mockKey) } just Runs
        every { mockCipher.iv } returns iv
        every { mockCipher.doFinal(longString.toByteArray(Charsets.UTF_8)) } returns encrypted
        every { Base64.encodeToString(combined, Base64.NO_WRAP) } returns expectedBase64

        val result = cryptoHelper.encrypt(longString)

        assertEquals(expectedBase64, result)
    }

    @Test
    fun `encrypt uses correct cipher transformation`() {
        val iv = ByteArray(12) { 0 }
        val encrypted = ByteArray(16) { 0 }

        every { mockCipher.init(Cipher.ENCRYPT_MODE, mockKey) } just Runs
        every { mockCipher.iv } returns iv
        every { mockCipher.doFinal(any()) } returns encrypted
        every { Base64.encodeToString(any(), Base64.NO_WRAP) } returns "x"

        cryptoHelper.encrypt("test")

        verify { Cipher.getInstance("AES/GCM/NoPadding") }
    }
}
