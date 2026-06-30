package com.tavern.lite.data.importexport

import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.data.model.WorldBook
import com.tavern.lite.data.model.WorldBookEntryData
import android.util.Log
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LorebookExporter @Inject constructor(
    private val json: Json
) {
    /**
     * 导出世界书为 SillyTavern JSON 格式
     */
    fun exportToJson(
        worldBook: WorldBookEntity,
        entries: List<WorldBookEntryEntity>
    ): String {
        val entriesMap = mutableMapOf<String, WorldBookEntryData>()
        entries.forEachIndexed { index, entry ->
            val keys: List<String> = try {
                json.decodeFromString(entry.keys)
            } catch (e: Exception) {
                Log.w("LorebookExporter", "Failed to decode keys: ${e.message}", e)
                emptyList()
            }
            val secondaryKeys: List<String> = try {
                json.decodeFromString(entry.keysSecondary)
            } catch (e: Exception) {
                Log.w("LorebookExporter", "Failed to decode secondaryKeys: ${e.message}", e)
                emptyList()
            }

            entriesMap[index.toString()] = WorldBookEntryData(
                uid = entry.uid,
                key = keys,
                keysecondary = secondaryKeys,
                content = entry.content,
                comment = entry.comment,
                constant = entry.constant,
                selective = entry.selective,
                selectiveLogic = entry.selectiveLogic,
                order = entry.orderVal,
                position = entry.position,
                disable = entry.disabled,
                excludeRecursion = entry.excludeRecursion,
                preventRecursion = entry.preventRecursion,
                probability = entry.probability,
                depth = entry.depth,
                group = entry.group,
                groupOverride = entry.groupOverride,
                groupWeight = entry.groupWeight
            )
        }

        val worldBook = WorldBook(entries = entriesMap)
        return json.encodeToString(WorldBook.serializer(), worldBook)
    }

    /**
     * 从 SillyTavern JSON 格式导入世界书条目
     */
    fun importFromJson(
        jsonString: String,
        worldBookId: Long
    ): List<WorldBookEntryEntity> {
        val worldBook = json.decodeFromString(WorldBook.serializer(), jsonString)
        return worldBook.entries.map { (_, data) ->
            WorldBookEntryEntity(
                worldBookId = worldBookId,
                uid = data.uid,
                comment = data.comment,
                keys = json.encodeToString(ListSerializer(String.serializer()), data.key),
                keysSecondary = json.encodeToString(ListSerializer(String.serializer()), data.keysecondary),
                content = data.content,
                constant = data.constant,
                position = data.position,
                orderVal = data.order,
                probability = data.probability,
                depth = data.depth,
                disabled = data.disable,
                selective = data.selective,
                selectiveLogic = data.selectiveLogic,
                excludeRecursion = data.excludeRecursion,
                preventRecursion = data.preventRecursion,
                group = data.group,
                groupOverride = data.groupOverride,
                groupWeight = data.groupWeight
            )
        }
    }
}
