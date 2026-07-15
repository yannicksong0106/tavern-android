package com.tavern.lite.util

import android.content.Context
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.CharacterCard
import com.tavern.lite.data.model.CharacterData
import com.tavern.lite.data.repository.CharacterRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
class SillyTavernImporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var characterRepository: CharacterRepository

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var importer: SillyTavernImporter

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        filesDir = temp.newFolder("files")
        cacheDir = temp.newFolder("cache")
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        importer = SillyTavernImporter(context, characterRepository, json)
    }

    @Test
    fun `importFromJson creates character from card data`() = runTest {
        val dataSlot = slot<CharacterData>()
        val file = temp.newFile("alice.json").also {
            it.writeText(json.encodeToString(sampleCard()), Charsets.UTF_8)
        }
        coEvery { characterRepository.createCharacter(capture(dataSlot), null) } returns 42L

        val result = importer.importFromJson(file)

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrNull())
        assertEquals("Alice", dataSlot.captured.name)
        assertEquals("curious", dataSlot.captured.personality)
    }

    @Test
    fun `importFromJson returns failure for malformed json`() = runTest {
        val file = temp.newFile("broken.json").also { it.writeText("""{"data":""") }

        val result = importer.importFromJson(file)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { characterRepository.createCharacter(any(), any()) }
    }

    @Test
    fun `importFromJson fails gracefully on deeply nested json instead of crashing`() = runTest {
        // X5 验证：深嵌套 JSON 触发递归下降解析栈溢出（StackOverflowError 是 Error 非 Exception）。
        // 修复前只 catch Exception，Error 传播出去导致 App 崩溃；修复后应返回 failure。
        val depth = 100_000
        val payload = "{\"spec\":\"chara_card_v2\",\"data\":{\"name\":\"x\",\"extensions\":{\"e\":" +
            "[".repeat(depth) + "]".repeat(depth) + "}}}"
        val file = temp.newFile("deep.json").also { it.writeText(payload, Charsets.UTF_8) }

        val result = importer.importFromJson(file)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { characterRepository.createCharacter(any(), any()) }
    }

    @Test
    fun `importFromPng reads chara metadata and copies avatar`() = runTest {
        val dataSlot = slot<CharacterData>()
        val avatarSlot = slot<String>()
        val source = temp.newFile("source.png").also { it.writeBytes(minimalPng()) }
        val png = temp.newFile("card.png")
        PngMetadata.writeCharaCard(source, json.encodeToString(sampleCard()), png)
        coEvery { characterRepository.createCharacter(capture(dataSlot), capture(avatarSlot)) } returns 7L

        val result = importer.importFromPng(png)

        assertTrue(result.isSuccess)
        assertEquals(7L, result.getOrNull())
        assertEquals("Alice", dataSlot.captured.name)
        assertTrue(File(avatarSlot.captured).exists())
        assertTrue(File(avatarSlot.captured).startsWith(File(filesDir, "avatars")))
    }

    @Test
    fun `importFromPng returns failure when chara metadata is missing`() = runTest {
        val png = temp.newFile("empty.png").also { it.writeBytes(minimalPng()) }

        val result = importer.importFromPng(png)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { characterRepository.createCharacter(any(), any()) }
    }

    @Test
    fun `exportToJson writes character card`() = runTest {
        val entity = sampleEntity()
        val output = temp.newFile("export.json")
        coEvery { characterRepository.getCharacterById(1L) } returns entity
        every { characterRepository.toCharacterCard(entity) } returns sampleCard()

        val result = importer.exportToJson(1L, output)

        assertTrue(result.isSuccess)
        val exported = json.decodeFromString<CharacterCard>(output.readText(Charsets.UTF_8))
        assertEquals("Alice", exported.data.name)
        assertEquals(listOf("tag-a", "tag-b"), exported.data.tags)
    }

    @Test
    fun `exportToJson returns failure when character is missing`() = runTest {
        val output = temp.newFile("missing.json")
        coEvery { characterRepository.getCharacterById(404L) } returns null

        val result = importer.exportToJson(404L, output)

        assertTrue(result.isFailure)
        assertEquals("", output.readText())
    }

    @Test
    fun `exportToPng embeds card using avatar when present`() = runTest {
        val avatar = temp.newFile("avatar.png").also { it.writeBytes(minimalPng()) }
        val entity = sampleEntity(avatarPath = avatar.absolutePath)
        val output = temp.newFile("card.png")
        coEvery { characterRepository.getCharacterById(1L) } returns entity
        every { characterRepository.toCharacterCard(entity) } returns sampleCard()

        val result = importer.exportToPng(1L, output)

        assertTrue(result.isSuccess)
        val exported = json.decodeFromString<CharacterCard>(PngMetadata.readCharaCard(output)!!)
        assertEquals("Alice", exported.data.name)
    }

    @Test
    fun `exportToPng creates placeholder when avatar is missing`() = runTest {
        val entity = sampleEntity(avatarPath = null)
        val output = temp.newFile("placeholder-card.png")
        coEvery { characterRepository.getCharacterById(1L) } returns entity
        every { characterRepository.toCharacterCard(entity) } returns sampleCard()

        val result = importer.exportToPng(1L, output)

        assertTrue(result.isSuccess)
        assertTrue(File(cacheDir, "placeholder.png").exists())
        val exported = json.decodeFromString<CharacterCard>(PngMetadata.readCharaCard(output)!!)
        assertEquals("Alice", exported.data.name)
    }

    private fun sampleCard(): CharacterCard = CharacterCard(
        data = CharacterData(
            name = "Alice",
            description = "description",
            personality = "curious",
            firstMes = "hello",
            mesExample = "<START>",
            tags = listOf("tag-a", "tag-b"),
            creator = "tester",
            characterVersion = "1.2",
            systemPrompt = "system",
            postHistoryInstructions = "post"
        )
    )

    private fun sampleEntity(avatarPath: String? = null): CharacterEntity = CharacterEntity(
        id = 1L,
        name = "Alice",
        description = "description",
        personality = "curious",
        firstMes = "hello",
        mesExample = "<START>",
        avatarPath = avatarPath,
        systemPrompt = "system",
        postHistoryInstructions = "post",
        tags = """["tag-a","tag-b"]""",
        creator = "tester",
        version = "1.2"
    )

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
