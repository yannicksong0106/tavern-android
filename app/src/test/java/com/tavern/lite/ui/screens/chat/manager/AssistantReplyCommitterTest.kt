package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.repository.ChatRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantReplyCommitterTest {

    @MockK private lateinit var chatRepository: ChatRepository

    private val chatId = 10L
    private lateinit var committer: AssistantReplyCommitter

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        committer = AssistantReplyCommitter(
            chatId = chatId,
            chatRepository = chatRepository,
            random = Random(1)
        )
    }

    @Test
    fun `commit returns false when assistant id is null`() = runTest {
        var committedCount = 0

        val result = committer.commitAssistantReply(
            assistantMsgId = null,
            isCancelled = { false },
            onEmotionUpdate = {},
            onAssistantReplyCommitted = { committedCount++ }
        )

        assertFalse(result)
        assertEquals(0, committedCount)
        coVerify(exactly = 0) { chatRepository.getMessageById(any()) }
    }

    @Test
    fun `commit returns false when cancelled and cancellation is respected`() = runTest {
        var committedCount = 0

        val result = committer.commitAssistantReply(
            assistantMsgId = 88L,
            isCancelled = { true },
            onEmotionUpdate = {},
            onAssistantReplyCommitted = { committedCount++ }
        )

        assertFalse(result)
        assertEquals(0, committedCount)
        coVerify(exactly = 0) { chatRepository.getMessageById(any()) }
    }

    @Test
    fun `commit updates emotion and emits committed event`() = runTest {
        var committedCount = 0
        var emotionContent: String? = null
        coEvery { chatRepository.getMessageById(88L) } returns MessageEntity(
            id = 88L,
            chatId = chatId,
            role = "assistant",
            content = "Single reply"
        )

        val result = committer.commitAssistantReply(
            assistantMsgId = 88L,
            isCancelled = { false },
            onEmotionUpdate = { emotionContent = it },
            onAssistantReplyCommitted = { committedCount++ }
        )

        assertTrue(result)
        assertEquals("Single reply", emotionContent)
        assertEquals(1, committedCount)
    }

    @Test
    fun `commit splits multi paragraph assistant replies`() = runTest {
        var committedCount = 0
        coEvery { chatRepository.getMessageById(88L) } returns MessageEntity(
            id = 88L,
            chatId = chatId,
            role = "assistant",
            content = "First\n\nSecond\n\nThird",
            characterId = 7L
        )
        coEvery { chatRepository.updateMessageContent(88L, "First") } just runs
        coEvery { chatRepository.sendMessage(chatId, "Second", "assistant", 7L) } returns 101L
        coEvery { chatRepository.sendMessage(chatId, "Third", "assistant", 7L) } returns 102L

        val result = committer.commitAssistantReply(
            assistantMsgId = 88L,
            isCancelled = { false },
            updateEmotion = false,
            onEmotionUpdate = {},
            onAssistantReplyCommitted = { committedCount++ }
        )

        assertTrue(result)
        assertEquals(1, committedCount)
        coVerify(exactly = 1) { chatRepository.updateMessageContent(88L, "First") }
        coVerify(exactly = 1) { chatRepository.sendMessage(chatId, "Second", "assistant", 7L) }
        coVerify(exactly = 1) { chatRepository.sendMessage(chatId, "Third", "assistant", 7L) }
    }
}
