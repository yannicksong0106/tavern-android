package com.tavern.lite.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tavern.lite.ui.screens.character.CharacterEditScreen
import com.tavern.lite.ui.screens.chat.ChatScreen
import com.tavern.lite.ui.screens.chatlist.ChatListScreen
import com.tavern.lite.ui.screens.groupchat.GroupChatCreateScreen
import com.tavern.lite.ui.screens.home.HomeScreen
import com.tavern.lite.ui.screens.memory.MemoryScreen
import com.tavern.lite.ui.screens.persona.PersonaScreen
import com.tavern.lite.ui.screens.preset.PresetScreen
import com.tavern.lite.ui.screens.quickreply.QuickReplyScreen
import com.tavern.lite.ui.screens.script.ScriptScreen
import com.tavern.lite.ui.screens.settings.ApiConfigScreen
import com.tavern.lite.ui.screens.settings.ChatStyleScreen
import com.tavern.lite.ui.screens.settings.DataManagementScreen
import com.tavern.lite.ui.screens.settings.DevLogScreen
import com.tavern.lite.ui.screens.settings.GenerationParamsScreen
import com.tavern.lite.ui.screens.settings.SettingsScreen
import com.tavern.lite.ui.screens.settings.TtsSettingsScreen
import com.tavern.lite.ui.screens.vn.VnScreen
import com.tavern.lite.ui.screens.worldbook.WorldBookEditScreen
import com.tavern.lite.ui.screens.worldbook.WorldBookListScreen

object Routes {
    const val HOME = "home"
    const val CHAT_LIST = "chat_list/{characterId}"
    const val CHAT = "chat/{characterId}/{chatId}"
    const val CHARACTER_EDIT = "character_edit?characterId={characterId}"
    const val SETTINGS = "settings"
    const val WORLD_BOOK_LIST = "world_books"
    const val WORLD_BOOK_EDIT = "world_book/{worldBookId}"
    const val MEMORY = "memory/{characterId}"
    const val SCRIPT = "script/{characterId}"
    const val PERSONA = "persona"
    const val GROUP_CHAT_CREATE = "group_chat_create"
    const val PRESET = "preset"
    const val DEV_LOG = "dev_log"
    const val MEMORY_LIBRARY = "memory_library"
    const val SETTINGS_API_CONFIG = "settings_api_config"
    const val SETTINGS_GENERATION_PARAMS = "settings_generation_params"
    const val SETTINGS_CHAT_STYLE = "settings_chat_style"
    const val SETTINGS_TTS = "settings_tts"
    const val SETTINGS_DATA_MANAGEMENT = "settings_data_management"
    const val QUICK_REPLIES = "quick_replies"
    const val VN = "vn/{characterId}/{chatId}"

    fun chatList(characterId: Long) = "chat_list/$characterId"
    fun vn(characterId: Long, chatId: Long) = "vn/$characterId/$chatId"
    fun chat(characterId: Long, chatId: Long) = "chat/$characterId/$chatId"
    fun characterEdit(characterId: Long? = null) =
        if (characterId != null) "character_edit?characterId=$characterId"
        else "character_edit"
    fun worldBookEdit(worldBookId: Long) = "world_book/$worldBookId"
    fun memory(characterId: Long) = "memory/$characterId"
    fun script(characterId: Long) = "script/$characterId"
}

@Composable
fun TavernNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(200))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onCharacterClick = { characterId ->
                    navController.navigate(Routes.chatList(characterId))
                },
                onCreateCharacter = {
                    navController.navigate(Routes.characterEdit())
                },
                onEditCharacter = { characterId ->
                    navController.navigate(Routes.characterEdit(characterId))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
                onWorldBookClick = {
                    navController.navigate(Routes.WORLD_BOOK_LIST)
                },
                onGroupChatClick = {
                    navController.navigate(Routes.GROUP_CHAT_CREATE)
                },
                onGroupChatItemClick = { chatId, primaryCharacterId ->
                    navController.navigate(Routes.chat(primaryCharacterId, chatId))
                }
            )
        }

        composable(
            Routes.CHAT_LIST,
            arguments = listOf(
                navArgument("characterId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            ChatListScreen(
                characterId = characterId,
                onChatClick = { chatId ->
                    navController.navigate(Routes.chat(characterId, chatId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.CHAT,
            arguments = listOf(
                navArgument("characterId") { type = NavType.LongType },
                navArgument("chatId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
            ChatScreen(
                characterId = characterId,
                chatId = chatId,
                onBack = { navController.popBackStack() },
                onVnMode = {
                    navController.navigate(Routes.vn(characterId, chatId))
                }
            )
        }

        composable(
            Routes.VN,
            arguments = listOf(
                navArgument("characterId") { type = NavType.LongType },
                navArgument("chatId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
            VnScreen(
                characterId = characterId,
                chatId = chatId,
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            Routes.CHARACTER_EDIT,
            arguments = listOf(
                navArgument("characterId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId")?.takeIf { it != -1L }
            CharacterEditScreen(
                characterId = characterId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onMemoryClick = { charId -> navController.navigate(Routes.memory(charId)) },
                onScriptClick = { charId -> navController.navigate(Routes.script(charId)) },
                onPresetClick = { navController.navigate(Routes.PRESET) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPersonaClick = { navController.navigate(Routes.PERSONA) },
                onDevLogClick = { navController.navigate(Routes.DEV_LOG) },
                onMemoryLibraryClick = { navController.navigate(Routes.MEMORY_LIBRARY) },
                onApiConfigClick = { navController.navigate(Routes.SETTINGS_API_CONFIG) },
                onGenerationParamsClick = { navController.navigate(Routes.SETTINGS_GENERATION_PARAMS) },
                onChatStyleClick = { navController.navigate(Routes.SETTINGS_CHAT_STYLE) },
                onTtsClick = { navController.navigate(Routes.SETTINGS_TTS) },
                onDataManagementClick = { navController.navigate(Routes.SETTINGS_DATA_MANAGEMENT) },
                onQuickRepliesClick = { navController.navigate(Routes.QUICK_REPLIES) }
            )
        }

        composable(Routes.PERSONA) {
            PersonaScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.GROUP_CHAT_CREATE) {
            GroupChatCreateScreen(
                onBack = { navController.popBackStack() },
                onGroupCreated = { chatId, primaryCharacterId ->
                    navController.navigate(Routes.chat(primaryCharacterId, chatId)) {
                        popUpTo(Routes.GROUP_CHAT_CREATE) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.WORLD_BOOK_LIST) {
            WorldBookListScreen(
                onWorldBookClick = { worldBookId ->
                    navController.navigate(Routes.worldBookEdit(worldBookId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.WORLD_BOOK_EDIT,
            arguments = listOf(
                navArgument("worldBookId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val worldBookId = backStackEntry.arguments?.getLong("worldBookId") ?: return@composable
            WorldBookEditScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.MEMORY,
            arguments = listOf(
                navArgument("characterId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            MemoryScreen(
                characterId = characterId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.SCRIPT,
            arguments = listOf(
                navArgument("characterId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            ScriptScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PRESET) {
            PresetScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DEV_LOG) {
            DevLogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MEMORY_LIBRARY) {
            MemoryScreen(
                characterId = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS_API_CONFIG) {
            ApiConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS_GENERATION_PARAMS) {
            GenerationParamsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS_CHAT_STYLE) {
            ChatStyleScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS_TTS) {
            TtsSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS_DATA_MANAGEMENT) {
            DataManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.QUICK_REPLIES) {
            QuickReplyScreen(onBack = { navController.popBackStack() })
        }
    }
}
