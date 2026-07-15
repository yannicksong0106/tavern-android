package com.tavern.lite.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tavern.lite.data.db.RoomTransactionRunner
import com.tavern.lite.data.db.TavernDatabase
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #18 回归：createBranchFromMessage 的建分支 + 拷消息必须原子提交。
 * 用真实 in-memory Room 拿到 withTransaction 回滚语义（fake tx 无法覆盖）。
 * 委托装饰 MessageDao，arm 后下一次 insert 抛错，模拟拷贝中途失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ChatRepositoryBranchTransactionTest {

    private lateinit var db: TavernDatabase

    /** 委托装饰：arm 后下一次 insert 抛错，其余方法透传真实 DAO。 */
    private class FailingMessageDao(
        private val delegate: MessageDao
    ) : MessageDao by delegate {
        var armed = false
        override suspend fun insert(message: MessageEntity): Long {
            if (armed) throw RuntimeException("simulated disk-full mid-copy")
            return delegate.insert(message)
        }
    }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TavernDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `createBranchFromMessage rolls back branch and copied messages when a copy fails`() = runTest {
        val realMessageDao = db.messageDao()
        val failingMessageDao = FailingMessageDao(realMessageDao)
        val repo = ChatRepository(
            RoomTransactionRunner(db),
            db.chatDao(),
            failingMessageDao,
            db.branchDao()
        )

        val charId = db.characterDao().insert(CharacterEntity(name = "Alice"))
        val chatId = db.chatDao().insert(ChatEntity(characterId = charId, name = "src"))
        val m1 = realMessageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "one"))
        realMessageDao.insert(MessageEntity(chatId = chatId, role = "assistant", content = "two"))

        val branchesBefore = db.branchDao().getBranchesForChatSync(chatId).size
        val messagesBefore = realMessageDao.getAllActiveMessagesForChat(chatId).size

        // arm：下一次 insert（分支拷贝的第一条）抛错
        failingMessageDao.armed = true
        var threw = false
        try {
            repo.createBranchFromMessage(chatId, m1, "branch-x")
        } catch (e: RuntimeException) {
            threw = true
        }
        failingMessageDao.armed = false

        assertTrue("拷贝失败应向上抛出", threw)
        // 事务回滚：分支行不应残留，已拷消息不应残留
        assertEquals(branchesBefore, db.branchDao().getBranchesForChatSync(chatId).size)
        assertEquals(messagesBefore, realMessageDao.getAllActiveMessagesForChat(chatId).size)
    }
}
