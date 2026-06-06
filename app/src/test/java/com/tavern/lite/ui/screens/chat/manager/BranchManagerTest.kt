package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.repository.ChatRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BranchManagerTest {

    @MockK private lateinit var chatRepository: ChatRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var manager: BranchManager
    private val chatId = 10L

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)
        manager = BranchManager(chatId, chatRepository, CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initial state ====================

    @Test
    fun `initial branch list is empty`() {
        assertTrue(manager.branchEntities.value.isEmpty())
    }

    @Test
    fun `initial current branch id is null`() {
        assertNull(manager.currentBranchId.value)
    }

    @Test
    fun `initial bookmark filter is off`() {
        assertFalse(manager.showBookmarksOnly.value)
    }

    // ==================== loadBranches ====================

    @Test
    fun `loadBranches selects default branch`() = runTest {
        val branches = listOf(
            BranchEntity(id = 1, chatId = chatId, name = "Main", isDefault = true),
            BranchEntity(id = 2, chatId = chatId, name = "Alt", isDefault = false)
        )
        coEvery { chatRepository.getBranchesForChatSync(chatId) } returns branches

        manager.loadBranches()
        advanceUntilIdle()

        assertEquals(branches, manager.branchEntities.value)
        assertEquals(1L, manager.currentBranchId.value)
    }

    @Test
    fun `loadBranches falls back to last branch when no default`() = runTest {
        val branches = listOf(
            BranchEntity(id = 1, chatId = chatId, name = "First", isDefault = false),
            BranchEntity(id = 2, chatId = chatId, name = "Second", isDefault = false)
        )
        coEvery { chatRepository.getBranchesForChatSync(chatId) } returns branches

        manager.loadBranches()
        advanceUntilIdle()

        assertEquals(2L, manager.currentBranchId.value)
    }

    @Test
    fun `loadBranches sets null when no branches exist`() = runTest {
        coEvery { chatRepository.getBranchesForChatSync(chatId) } returns emptyList()

        manager.loadBranches()
        advanceUntilIdle()

        assertTrue(manager.branchEntities.value.isEmpty())
        assertNull(manager.currentBranchId.value)
    }

    // ==================== switchBranch ====================

    @Test
    fun `switchBranch updates current branch and calls repository`() = runTest {
        manager.switchBranch(5L)
        advanceUntilIdle()

        assertEquals(5L, manager.currentBranchId.value)
        coVerify { chatRepository.switchBranch(chatId, 5L) }
    }

    // ==================== createBranch ====================

    @Test
    fun `createBranch calls repository and reloads`() = runTest {
        val branches = listOf(BranchEntity(id = 3, chatId = chatId, name = "New Branch"))
        coEvery { chatRepository.getBranchesForChatSync(chatId) } returns branches

        manager.createBranch("New Branch")
        advanceUntilIdle()

        coVerify { chatRepository.createBranch(chatId, "New Branch") }
        coVerify { chatRepository.getBranchesForChatSync(chatId) }
        assertEquals(branches, manager.branchEntities.value)
    }

    // ==================== createBranchFromMessage ====================

    @Test
    fun `createBranchFromMessage calls repository and reloads`() = runTest {
        val branches = listOf(BranchEntity(id = 4, chatId = chatId, name = "From msg"))
        coEvery { chatRepository.getBranchesForChatSync(chatId) } returns branches

        manager.createBranchFromMessage(100L, "From msg")
        advanceUntilIdle()

        coVerify { chatRepository.createBranchFromMessage(chatId, 100L, "From msg") }
        coVerify { chatRepository.getBranchesForChatSync(chatId) }
    }

    // ==================== deleteBranch ====================

    @Test
    fun `deleteBranch calls repository and reloads`() = runTest {
        val branch = BranchEntity(id = 2, chatId = chatId, name = "To delete")
        coEvery { chatRepository.getBranchesForChatSync(chatId) } returns emptyList()

        manager.deleteBranch(branch)
        advanceUntilIdle()

        coVerify { chatRepository.deleteBranch(branch) }
        coVerify { chatRepository.getBranchesForChatSync(chatId) }
    }

    // ==================== toggleBookmarkFilter ====================

    @Test
    fun `toggleBookmarkFilter toggles state`() {
        assertFalse(manager.showBookmarksOnly.value)

        manager.toggleBookmarkFilter()
        assertTrue(manager.showBookmarksOnly.value)

        manager.toggleBookmarkFilter()
        assertFalse(manager.showBookmarksOnly.value)
    }
}
