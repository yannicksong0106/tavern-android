package com.tavern.lite.ui.screens.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tavern.lite.domain.usecase.StScriptAction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickReplyResultHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `continue generation clears unsafe action state before executing`() {
        var beforeUnsafeActionCount = 0
        var continueCount = 0

        handleQuickReplyResult(
            context = context,
            result = QuickReplyUiResult(actions = listOf(StScriptAction.ContinueGeneration)),
            allowUnsafeActions = true,
            onSetInput = {},
            onSendMessage = {},
            onTriggerGeneration = {},
            onContinueGeneration = { continueCount++ },
            onBeforeUnsafeAction = { beforeUnsafeActionCount++ }
        )

        assertEquals(1, beforeUnsafeActionCount)
        assertEquals(1, continueCount)
    }

    @Test
    fun `multiple unsafe actions clear unsafe action state only once`() {
        var beforeUnsafeActionCount = 0
        val calls = mutableListOf<String>()

        handleQuickReplyResult(
            context = context,
            result = QuickReplyUiResult(
                actions = listOf(
                    StScriptAction.SendMessage("hi"),
                    StScriptAction.TriggerGeneration("next"),
                    StScriptAction.ContinueGeneration
                )
            ),
            allowUnsafeActions = true,
            onSetInput = {},
            onSendMessage = { calls += "send:$it" },
            onTriggerGeneration = { calls += "trigger:$it" },
            onContinueGeneration = { calls += "continue" },
            onBeforeUnsafeAction = { beforeUnsafeActionCount++ }
        )

        assertEquals(1, beforeUnsafeActionCount)
        assertEquals(listOf("send:hi", "trigger:next", "continue"), calls)
    }
}
