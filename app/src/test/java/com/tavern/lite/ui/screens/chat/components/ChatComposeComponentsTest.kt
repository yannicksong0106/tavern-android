package com.tavern.lite.ui.screens.chat.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.ui.theme.TavernTheme
import io.noties.markwon.Markwon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatComposeComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun quickReplyBar_rendersRepliesAndInvokesClick() {
        val firstReply = quickReply(id = 1, label = "Say hello")
        val secondReply = quickReply(id = 2, label = "Check memory")
        var clickedReply: QuickReplyEntity? = null

        setThemedContent {
            QuickReplyBar(
                replies = listOf(firstReply, secondReply),
                onReplyClick = { clickedReply = it }
            )
        }

        composeRule.onNodeWithText("Say hello")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(firstReply, clickedReply)
    }

    @Test
    fun quickReplyConfirmDialog_rendersCopyAndInvokesActions() {
        var confirmed = false
        var dismissed = false

        setThemedContent {
            QuickReplyConfirmDialog(
                reply = quickReply(id = 3, label = "Summarize"),
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true }
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.quick_reply_confirm_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Summarize").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.quick_reply_run)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

        assertTrue(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun messageBubble_rendersUserMessageAndInvokesTap() {
        val message = MessageEntity(
            id = 10,
            chatId = 20,
            role = "user",
            content = "Hello from the player",
            createdAt = System.currentTimeMillis()
        )
        var taps = 0

        setThemedContent {
            MessageBubble(
                message = message,
                characterName = "Ari",
                markwon = Markwon.create(context),
                showTimestamp = false,
                onTap = { taps++ },
                onRegenerate = {},
                onEdit = {},
                onDelete = {}
            )
        }

        composeRule.onNodeWithText("Hello from the player").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.a11y_user_message))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, taps)
    }

    private fun quickReply(id: Long, label: String) = QuickReplyEntity(
        id = id,
        setId = 100,
        label = label,
        script = "/echo $label"
    )

    private fun setThemedContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            TavernTheme(content = content)
        }
    }
}
