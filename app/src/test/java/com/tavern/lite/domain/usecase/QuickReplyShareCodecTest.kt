package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyShareCodecTest {

    private val codec = QuickReplyShareCodec()

    private fun sampleSet() = QuickReplySetEntity(
        id = 42,
        name = "My Pack",
        scope = "character",
        characterId = 7,
        chatId = null,
        enabled = true,
        displayOrder = 3
    )

    private fun sampleReplies() = listOf(
        QuickReplyEntity(
            id = 100,
            setId = 42,
            label = "Greet",
            script = "/echo hi",
            icon = "👋",
            automationId = null,
            enabled = true,
            requiresConfirmation = false,
            allowAutoRun = true,
            canSendMessages = true,
            canTriggerGeneration = true,
            displayOrder = 1
        ),
        QuickReplyEntity(
            id = 101,
            setId = 42,
            label = "Send",
            script = "/send hello",
            automationId = "assistant_reply",
            allowAutoRun = true,
            canSendMessages = true,
            displayOrder = 0
        )
    )

    @Test
    fun `export then parse round trips name and scripts`() {
        val jsonStr = codec.export(sampleSet(), sampleReplies())
        val parsed = codec.parse(jsonStr).getOrThrow()

        assertEquals("My Pack", parsed.name)
        assertEquals(2, parsed.replies.size)
        // 导出按 displayOrder 排序：Send(0) 在前，Greet(1) 在后
        assertEquals("Send", parsed.replies[0].label)
        assertEquals("/send hello", parsed.replies[0].script)
        assertEquals("Greet", parsed.replies[1].label)
        assertEquals("👋", parsed.replies[1].icon)
    }

    @Test
    fun `export drops db ids context binding and permission flags`() {
        val jsonStr = codec.export(sampleSet(), sampleReplies())

        // 分享 JSON 不得含权限字段名或上下文绑定
        assertFalse("不应导出 allowAutoRun", jsonStr.contains("allowAutoRun"))
        assertFalse("不应导出 canSendMessages", jsonStr.contains("canSendMessages"))
        assertFalse("不应导出 canTriggerGeneration", jsonStr.contains("canTriggerGeneration"))
        assertFalse("不应导出 characterId", jsonStr.contains("characterId"))
        assertFalse("不应导出 setId", jsonStr.contains("setId"))
    }

    @Test
    fun `toEntities forces all permission flags off`() {
        val pack = codec.parse(codec.export(sampleSet(), sampleReplies())).getOrThrow()
        val entities = codec.toEntities(pack, setId = 99)

        assertTrue("导入实体应全部绑定到目标 set", entities.all { it.setId == 99L })
        assertTrue("导入不得开启 auto-run", entities.none { it.allowAutoRun })
        assertTrue("导入不得授权发送消息", entities.none { it.canSendMessages })
        assertTrue("导入不得授权触发生成", entities.none { it.canTriggerGeneration })
        assertTrue("导入不得预设跳过确认", entities.none { it.requiresConfirmation })
    }

    @Test
    fun `toEntities preserves scripts labels and automation id`() {
        val pack = codec.parse(codec.export(sampleSet(), sampleReplies())).getOrThrow()
        val entities = codec.toEntities(pack, setId = 5)

        val send = entities.single { it.label == "Send" }
        assertEquals("/send hello", send.script)
        assertEquals("assistant_reply", send.automationId)
    }

    @Test
    fun `parse rejects wrong format header`() {
        val bogus = """{"format":"something-else","version":1,"name":"x","replies":[{"label":"a","script":"/echo a"}]}"""
        val result = codec.parse(bogus)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("有效"))
    }

    @Test
    fun `parse rejects future version`() {
        val future = """{"format":"tavern-quick-reply-pack","version":999,"name":"x","replies":[{"label":"a","script":"/echo a"}]}"""
        val result = codec.parse(future)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("版本"))
    }

    @Test
    fun `parse rejects empty replies`() {
        val empty = """{"format":"tavern-quick-reply-pack","version":1,"name":"x","replies":[]}"""
        val result = codec.parse(empty)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("不含"))
    }

    @Test
    fun `parse rejects malformed json`() {
        val result = codec.parse("{not valid json")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("无法解析"))
    }

    @Test
    fun `parse tolerates unknown keys for forward compatibility`() {
        val withExtra = """{"format":"tavern-quick-reply-pack","version":1,"name":"x","futureField":true,"replies":[{"label":"a","script":"/echo a","futureReplyField":42}]}"""
        val result = codec.parse(withExtra)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().replies.size)
    }

    @Test
    fun `toEntities assigns sequential order when shared order is zero`() {
        val pack = QuickReplySharePackage(
            name = "seq",
            replies = listOf(
                QuickReplySharePackage.SharedReply(label = "a", script = "/echo a", displayOrder = 0),
                QuickReplySharePackage.SharedReply(label = "b", script = "/echo b", displayOrder = 0)
            )
        )
        val entities = codec.toEntities(pack, setId = 1)

        assertEquals(0, entities[0].displayOrder)
        assertEquals(1, entities[1].displayOrder)
    }

    @Test
    fun `parse rejects blank name`() {
        val blank = """{"format":"tavern-quick-reply-pack","version":1,"name":"   ","replies":[{"label":"a","script":"/echo a"}]}"""
        val result = codec.parse(blank)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("名称"))
    }

    @Test
    fun `parse trims name`() {
        val padded = """{"format":"tavern-quick-reply-pack","version":1,"name":"  My Pack  ","replies":[{"label":"a","script":"/echo a"}]}"""
        val result = codec.parse(padded)

        assertTrue(result.isSuccess)
        assertEquals("My Pack", result.getOrThrow().name)
    }

    @Test
    fun `parse drops replies with blank label or script`() {
        val mixed = """{"format":"tavern-quick-reply-pack","version":1,"name":"x","replies":[
            {"label":"good","script":"/echo ok"},
            {"label":"   ","script":"/echo blanklabel"},
            {"label":"blankscript","script":"   "}
        ]}"""
        val result = codec.parse(mixed)

        assertTrue(result.isSuccess)
        val replies = result.getOrThrow().replies
        assertEquals(1, replies.size)
        assertEquals("good", replies[0].label)
    }

    @Test
    fun `parse fails when all replies are blank`() {
        val allBlank = """{"format":"tavern-quick-reply-pack","version":1,"name":"x","replies":[
            {"label":"  ","script":"/echo a"},
            {"label":"b","script":"  "}
        ]}"""
        val result = codec.parse(allBlank)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("有效"))
    }

    @Test
    fun `parse caps reply count`() {
        val over = QuickReplySharePackage.MAX_REPLIES + 50
        val replies = (1..over).joinToString(",") { """{"label":"r$it","script":"/echo $it"}""" }
        val huge = """{"format":"tavern-quick-reply-pack","version":1,"name":"x","replies":[$replies]}"""
        val result = codec.parse(huge)

        assertTrue(result.isSuccess)
        assertEquals(QuickReplySharePackage.MAX_REPLIES, result.getOrThrow().replies.size)
    }

    @Test
    fun `parse truncates oversized label and script`() {
        val longLabel = "a".repeat(QuickReplySharePackage.MAX_LABEL_LENGTH + 100)
        val longScript = "/echo " + "b".repeat(QuickReplySharePackage.MAX_SCRIPT_LENGTH + 100)
        val big = """{"format":"tavern-quick-reply-pack","version":1,"name":"x","replies":[{"label":"$longLabel","script":"$longScript"}]}"""
        val result = codec.parse(big)

        assertTrue(result.isSuccess)
        val reply = result.getOrThrow().replies[0]
        assertEquals(QuickReplySharePackage.MAX_LABEL_LENGTH, reply.label.length)
        assertEquals(QuickReplySharePackage.MAX_SCRIPT_LENGTH, reply.script.length)
    }

    @Test
    fun `parse blanks out empty icon and automation id`() {
        val withBlanks = """{"format":"tavern-quick-reply-pack","version":1,"name":"x","replies":[{"label":"a","script":"/echo a","icon":"  ","automationId":"  "}]}"""
        val result = codec.parse(withBlanks)

        assertTrue(result.isSuccess)
        val reply = result.getOrThrow().replies[0]
        assertEquals(null, reply.icon)
        assertEquals(null, reply.automationId)
    }
}
