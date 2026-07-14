package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 可分享的 Quick Reply 脚本包（X5 脚本市场最小可用形态）。
 *
 * 项目无后端，"脚本市场"以本地分享格式落地：用户把一个 Quick Reply 集合导出成一段
 * 版本化 JSON，通过任意渠道（复制/文件/分享）互传，对方导入即得同一批快捷回复脚本。
 *
 * 安全边界：导出**不携带**权限标志；导入时所有权限（auto-run / 发送 / 触发生成）
 * 一律重置为关闭。外部脚本包不得声明自己可自动运行或发送消息，必须由导入者在本地
 * 逐条重新授权，防止恶意包绕过 [QuickReplyValidation] 与执行器的授权检查。
 */
@Serializable
data class QuickReplySharePackage(
    val format: String = SHARE_FORMAT,
    val version: Int = SHARE_VERSION,
    val name: String,
    val replies: List<SharedReply> = emptyList()
) {
    @Serializable
    data class SharedReply(
        val label: String,
        val script: String,
        val icon: String? = null,
        val automationId: String? = null,
        val displayOrder: Int = 0
    )

    companion object {
        const val SHARE_FORMAT = "tavern-quick-reply-pack"
        const val SHARE_VERSION = 1

        /** 单个脚本包最多导入的回复数，挡住恶意/损坏包灌库。 */
        const val MAX_REPLIES = 200

        /** 单条 label / script 长度上限，挡住超长字段撑爆 UI 与 DB。 */
        const val MAX_LABEL_LENGTH = 200
        const val MAX_SCRIPT_LENGTH = 20_000
        const val MAX_NAME_LENGTH = 200
    }
}

/**
 * Quick Reply 脚本包编解码：集合 ↔ 分享 JSON。纯逻辑，供 [QuickReplyShareCodecTest] 覆盖。
 */
class QuickReplyShareCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
) {

    /**
     * 把一个集合 + 其回复导出为分享 JSON 字符串。
     * 剔除数据库自增 id、上下文绑定（characterId/chatId）与全部权限标志。
     */
    fun export(set: QuickReplySetEntity, replies: List<QuickReplyEntity>): String {
        val pack = QuickReplySharePackage(
            name = set.name,
            replies = replies
                .sortedBy { it.displayOrder }
                .map { reply ->
                    QuickReplySharePackage.SharedReply(
                        label = reply.label,
                        script = reply.script,
                        icon = reply.icon,
                        automationId = reply.automationId?.takeIf { it.isNotBlank() },
                        displayOrder = reply.displayOrder
                    )
                }
        )
        return json.encodeToString(QuickReplySharePackage.serializer(), pack)
    }

    /**
     * 解析分享 JSON。校验格式头与版本；失败返回 [Result.failure]，不抛给调用方。
     */
    fun parse(content: String): Result<QuickReplySharePackage> {
        val pack = try {
            json.decodeFromString(QuickReplySharePackage.serializer(), content.trim())
        } catch (e: SerializationException) {
            return Result.failure(IllegalArgumentException("无法解析脚本包 JSON", e))
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("无法解析脚本包 JSON", e))
        }

        if (pack.format != QuickReplySharePackage.SHARE_FORMAT) {
            return Result.failure(IllegalArgumentException("不是有效的 Quick Reply 脚本包"))
        }
        if (pack.version > QuickReplySharePackage.SHARE_VERSION) {
            return Result.failure(
                IllegalArgumentException("脚本包版本 ${pack.version} 高于当前支持的 ${QuickReplySharePackage.SHARE_VERSION}，请升级应用")
            )
        }

        val name = pack.name.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("脚本包名称为空"))
        }

        // 逐条清洗：丢弃 label/script 为空的坏条目，单条坏不毁整包；
        // 超长字段截断而非拒绝，防御恶意包撑爆 UI/DB。
        val sanitized = pack.replies.mapNotNull { it.sanitizedOrNull() }
        if (sanitized.isEmpty()) {
            return Result.failure(IllegalArgumentException("脚本包不含任何有效的快捷回复"))
        }

        return Result.success(
            pack.copy(
                name = name.take(QuickReplySharePackage.MAX_NAME_LENGTH),
                replies = sanitized.take(QuickReplySharePackage.MAX_REPLIES)
            )
        )
    }

    /**
     * 清洗单条分享回复：label 与 script 去空白后必须非空，否则丢弃（返回 null）。
     * 超长字段截断到上限。
     */
    private fun QuickReplySharePackage.SharedReply.sanitizedOrNull(): QuickReplySharePackage.SharedReply? {
        val cleanLabel = label.trim()
        val cleanScript = script.trim()
        if (cleanLabel.isEmpty() || cleanScript.isEmpty()) return null
        return copy(
            label = cleanLabel.take(QuickReplySharePackage.MAX_LABEL_LENGTH),
            script = cleanScript.take(QuickReplySharePackage.MAX_SCRIPT_LENGTH),
            icon = icon?.trim()?.takeIf { it.isNotEmpty() },
            automationId = automationId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * 把分享包物化成待插入的实体。[setId] 由调用方在事务里回填。
     *
     * 安全：所有权限标志（allowAutoRun / canSendMessages / canTriggerGeneration）
     * 强制为 false，回复默认启用但不授权——导入者需在本地逐条重新开权限。
     */
    fun toEntities(pack: QuickReplySharePackage, setId: Long): List<QuickReplyEntity> =
        pack.replies.mapIndexed { index, shared ->
            QuickReplyEntity(
                setId = setId,
                label = shared.label,
                script = shared.script,
                icon = shared.icon,
                automationId = shared.automationId?.takeIf { it.isNotBlank() },
                enabled = true,
                requiresConfirmation = false,
                allowAutoRun = false,
                canSendMessages = false,
                canTriggerGeneration = false,
                displayOrder = if (shared.displayOrder != 0) shared.displayOrder else index
            )
        }
}
