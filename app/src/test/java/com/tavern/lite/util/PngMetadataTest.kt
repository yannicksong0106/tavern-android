package com.tavern.lite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PngMetadataTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `writeTextChunk inserts metadata before IEND and readTextChunks returns it`() {
        val png = temp.newFile("card.png").also { it.writeBytes(minimalPng()) }

        PngMetadata.writeTextChunk(png, "name", "Alice")

        assertEquals(mapOf("name" to "Alice"), PngMetadata.readTextChunks(png))
    }

    @Test
    fun `writeCharaCard and readCharaCard round trip utf8 json`() {
        val source = temp.newFile("source.png").also { it.writeBytes(minimalPng()) }
        val output = temp.newFile("output.png")
        val json = """{"name":"Alice","description":"你好"}"""

        PngMetadata.writeCharaCard(source, json, output)

        assertEquals(json, PngMetadata.readCharaCard(output))
        assertEquals(emptyMap<String, String>(), PngMetadata.readTextChunks(source))
    }

    @Test
    fun `readCharaCard returns null when chara chunk is absent`() {
        val png = temp.newFile("empty.png").also { it.writeBytes(minimalPng()) }

        assertNull(PngMetadata.readCharaCard(png))
    }

    @Test
    fun `readTextChunks rejects non png file`() {
        val file = temp.newFile("not-png.bin").also { it.writeBytes(ByteArray(8) { 1 }) }

        assertThrows(IllegalArgumentException::class.java) {
            PngMetadata.readTextChunks(file)
        }
    }

    @Test
    fun `writeTextChunk rejects png without IEND`() {
        val file = temp.newFile("broken.png").also {
            it.writeBytes(PNG_SIGNATURE + byteArrayOf(0, 0, 0, 0))
        }

        assertThrows(IllegalArgumentException::class.java) {
            PngMetadata.writeTextChunk(file, "name", "Alice")
        }
    }

    private fun minimalPng(): ByteArray = PNG_SIGNATURE + chunk("IEND", ByteArray(0))

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array() +
            typeBytes +
            data +
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc.value.toInt()).array()
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
