package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 封装分支管理逻辑：加载、切换、创建、删除分支，书签筛选。
 */
class BranchManager(
    private val chatId: Long,
    private val chatRepository: ChatRepository,
    private val scope: CoroutineScope
) {
    private val _branchEntities = MutableStateFlow<List<BranchEntity>>(emptyList())
    val branchEntities: StateFlow<List<BranchEntity>> = _branchEntities.asStateFlow()

    private val _currentBranchId = MutableStateFlow<Long?>(null)
    val currentBranchId: StateFlow<Long?> = _currentBranchId.asStateFlow()

    private val _showBookmarksOnly = MutableStateFlow(false)
    val showBookmarksOnly: StateFlow<Boolean> = _showBookmarksOnly.asStateFlow()

    fun loadBranches() {
        scope.launch {
            val branches = chatRepository.getBranchesForChatSync(chatId)
            _branchEntities.value = branches
            val defaultBranch = branches.find { it.isDefault } ?: branches.lastOrNull()
            _currentBranchId.value = defaultBranch?.id
        }
    }

    fun switchBranch(branchId: Long) {
        scope.launch {
            chatRepository.switchBranch(chatId, branchId)
            _currentBranchId.value = branchId
        }
    }

    fun createBranch(name: String) {
        scope.launch {
            chatRepository.createBranch(chatId, name)
            loadBranches()
        }
    }

    fun createBranchFromMessage(messageId: Long, name: String) {
        scope.launch {
            chatRepository.createBranchFromMessage(chatId, messageId, name)
            loadBranches()
        }
    }

    fun deleteBranch(branch: BranchEntity) {
        scope.launch {
            chatRepository.deleteBranch(branch)
            loadBranches()
        }
    }

    fun toggleBookmarkFilter() {
        _showBookmarksOnly.value = !_showBookmarksOnly.value
    }
}
