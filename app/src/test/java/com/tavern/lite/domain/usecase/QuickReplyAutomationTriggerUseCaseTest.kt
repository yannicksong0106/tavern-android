package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.repository.QuickReplyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyAutomationTriggerUseCaseTest {
    private val repository = mockk<QuickReplyRepository>()
    private val executor = StScriptLiteExecutor(StScriptLiteParser())
    private val useCase = QuickReplyAutomationTriggerUseCase(repository, executor)

    @Test
    fun `trigger executes matching replies with auto run and carries variables`() = runTest {
        coEvery { repository.getRepliesByAutomationId("start", 7L, 9L) } returns listOf(
            QuickReplyEntity(
                id = 1,
                setId = 1,
                label = "Seed",
                script = "/setvar mood calm",
                automationId = "start",
                allowAutoRun = true
            ),
            QuickReplyEntity(
                id = 2,
                setId = 1,
                label = "Use",
                script = "/getvar mood\n/input {{mood}}",
                automationId = "start",
                allowAutoRun = true
            )
        )

        val result = useCase(" start ", characterId = 7L, chatId = 9L)

        coVerify { repository.getRepliesByAutomationId("start", 7L, 9L) }
        assertTrue(result.hasMatches)
        assertEquals("start", result.automationId)
        assertEquals("calm", result.variables["mood"])
        assertEquals(listOf("calm"), result.echoes)
        assertEquals(listOf(StScriptAction.SetInput("calm")), result.actions)
        assertTrue(result.blockedReasons.isEmpty())
    }

    @Test
    fun `trigger keeps auto run safety boundary even when reply can send`() = runTest {
        coEvery { repository.getRepliesByAutomationId("danger", null, null) } returns listOf(
            QuickReplyEntity(
                id = 1,
                setId = 1,
                label = "Send",
                script = "/send hello",
                automationId = "danger",
                allowAutoRun = true,
                canSendMessages = true
            )
        )

        val result = useCase("danger")

        assertTrue(result.actions.isEmpty())
        assertEquals(1, result.blockedReasons.size)
        assertTrue(result.blockedReasons.single().contains("auto-run"))
    }

    @Test
    fun `trigger skips replies that require confirmation`() = runTest {
        coEvery { repository.getRepliesByAutomationId("confirm", null, null) } returns listOf(
            QuickReplyEntity(
                id = 1,
                setId = 1,
                label = "Confirm",
                script = "/echo hidden",
                automationId = "confirm",
                requiresConfirmation = true,
                allowAutoRun = true
            )
        )

        val result = useCase("confirm")

        assertTrue(result.actions.isEmpty())
        assertTrue(result.echoes.isEmpty())
        assertEquals(listOf("Confirmation is required"), result.blockedReasons)
        assertEquals("Confirmation is required", result.executions.single().skippedReason)
    }

    @Test
    fun `blank automation id does not query repository`() = runTest {
        val result = useCase("   ")

        assertFalse(result.hasMatches)
        assertTrue(result.executions.isEmpty())
        coVerify(exactly = 0) { repository.getRepliesByAutomationId(any(), any(), any()) }
    }
}
