package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.GroupChatRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupChatSettingsManagerTest {

    private lateinit var manager: GroupChatSettingsManager
    private val characterRepository = mockk<CharacterRepository>(relaxed = true)
    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val groupChatRepository = mockk<GroupChatRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    private val character = CharacterEntity(id = 1, name = "Alice", chattiness = 60)

    @Before
    fun setup() {
        testScope = TestScope(testDispatcher)
        manager = GroupChatSettingsManager(
            chatId = 10,
            characterRepository = characterRepository,
            chatRepository = chatRepository,
            groupChatRepository = groupChatRepository,
            scope = testScope
        )
        manager.characterProvider = { character }
    }

    // ==================== Initial state ====================

    @Test
    fun `initial characterChattiness is 50`() {
        assertEquals(50, manager.characterChattiness.value)
    }

    @Test
    fun `initial groupChattiness is 50`() {
        assertEquals(50, manager.groupChattiness.value)
    }

    @Test
    fun `initial schedulingStrategy is NATURAL`() {
        assertEquals(GroupSchedulingStrategy.NATURAL, manager.schedulingStrategy.value)
    }

    @Test
    fun `initial messageIntervalMs is 1500`() {
        assertEquals(1500L, manager.messageIntervalMs.value)
    }

    @Test
    fun `initial groupCharacterChattiness is empty`() {
        assertEquals(emptyMap<Long, Int>(), manager.groupCharacterChattiness.value)
    }

    // ==================== loadCharacterChattiness ====================

    @Test
    fun `loadCharacterChattiness updates state`() {
        manager.loadCharacterChattiness(75)
        assertEquals(75, manager.characterChattiness.value)
    }

    // ==================== loadGroupSettings ====================

    @Test
    fun `loadGroupSettings updates all group state`() {
        val chatCharacters = listOf(1L to 60, 2L to 80)
        manager.loadGroupSettings(
            groupChattiness = 70,
            schedulingStrategy = GroupSchedulingStrategy.ROUND_ROBIN,
            messageIntervalMs = 3000L,
            chatCharacters = chatCharacters
        )
        assertEquals(70, manager.groupChattiness.value)
        assertEquals(GroupSchedulingStrategy.ROUND_ROBIN, manager.schedulingStrategy.value)
        assertEquals(3000L, manager.messageIntervalMs.value)
        assertEquals(mapOf(1L to 60, 2L to 80), manager.groupCharacterChattiness.value)
    }

    // ==================== updateCharacterChattiness ====================

    @Test
    fun `updateCharacterChattiness updates state and calls repository`() {
        manager.updateCharacterChattiness(80)
        testScope.advanceUntilIdle()
        assertEquals(80, manager.characterChattiness.value)
        coVerify { characterRepository.updateCharacter(character.copy(chattiness = 80)) }
    }

    @Test
    fun `updateCharacterChattiness does nothing when characterProvider returns null`() {
        manager.characterProvider = { null }
        manager.updateCharacterChattiness(80)
        testScope.advanceUntilIdle()
        assertEquals(80, manager.characterChattiness.value)
        coVerify(exactly = 0) { characterRepository.updateCharacter(any()) }
    }

    // ==================== updateGroupChattiness ====================

    @Test
    fun `updateGroupChattiness updates state and calls repository`() {
        manager.updateGroupChattiness(90)
        testScope.advanceUntilIdle()
        assertEquals(90, manager.groupChattiness.value)
        coVerify { chatRepository.updateGroupChattiness(10, 90) }
    }

    // ==================== updateGroupCharacterChattiness ====================

    @Test
    fun `updateGroupCharacterChattiness updates map and calls repository`() {
        manager.updateGroupCharacterChattiness(1, 70)
        testScope.advanceUntilIdle()
        assertEquals(mapOf(1L to 70), manager.groupCharacterChattiness.value)
        coVerify { groupChatRepository.updateCharacterChattiness(10, 1, 70) }
    }

    @Test
    fun `updateGroupCharacterChattiness preserves other entries`() {
        manager.updateGroupCharacterChattiness(1, 70)
        manager.updateGroupCharacterChattiness(2, 85)
        testScope.advanceUntilIdle()
        assertEquals(mapOf(1L to 70, 2L to 85), manager.groupCharacterChattiness.value)
    }

    // ==================== updateSchedulingStrategy ====================

    @Test
    fun `updateSchedulingStrategy updates state and calls repository`() {
        manager.updateSchedulingStrategy(GroupSchedulingStrategy.LIST_ORDER)
        testScope.advanceUntilIdle()
        assertEquals(GroupSchedulingStrategy.LIST_ORDER, manager.schedulingStrategy.value)
        coVerify { groupChatRepository.updateSchedulingStrategy(10, "list_order") }
    }

    // ==================== updateMessageInterval ====================

    @Test
    fun `updateMessageInterval updates state and calls repository`() {
        manager.updateMessageInterval(5000L)
        testScope.advanceUntilIdle()
        assertEquals(5000L, manager.messageIntervalMs.value)
        coVerify { groupChatRepository.updateMessageInterval(10, 5000L) }
    }
}
