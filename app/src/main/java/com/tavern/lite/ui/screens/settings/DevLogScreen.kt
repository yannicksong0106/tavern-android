package com.tavern.lite.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavern.lite.R

data class VersionEntry(
    val version: String,
    val date: String,
    val summary: String,
    val details: List<String> = emptyList(),
    val lifeLog: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevLogScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dev_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.dev_log_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            getDevLogEntries().forEach { entry ->
                VersionCard(entry)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VersionCard(entry: VersionEntry) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 版本号和日期
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = entry.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 一句话总结
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // 更新详情
            if (entry.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                entry.details.forEach { detail ->
                    Text(
                        text = "· $detail",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }

            // 生活日志
            if (entry.lifeLog != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = entry.lifeLog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

private fun getDevLogEntries(): List<VersionEntry> = listOf(
    VersionEntry(
        version = "v1.2.8",
        date = "2026-05-28",
        summary = "安全收敛 + 多语言 + 聊天分支 + 自动摘要 + Web Search + VN 模式",
        details = listOf(
            "Phase S: TLS 证书固定 + Coil key 补全 + CE rethrow 全量审计",
            "Phase O3: 中/英/日/韩 四语言完整支持 + 语言切换 UI",
            "Phase P: 聊天分支系统 + 书签导航 + BranchEntity CRUD",
            "Phase Q: 三级预设 (Global/Char/Chat) + Handlebars 模板引擎",
            "Phase R: 自动摘要系统 — 长对话压缩 + 摘要注入 Prompt",
            "Phase T: Web Search — DuckDuckGo/Bing/Google 搜索引擎集成",
            "Phase U: 群聊调度增强 — 自然/列表/轮询三种策略",
            "Phase V: Visual Novel 模式 — 立绘系统 + 表情映射 + BGM + 转场动画"
        ),
        lifeLog = "日常：炸鸡太好吃了"
    ),
    VersionEntry(
        version = "v1.2.75",
        date = "2026-05-26",
        summary = "v1.3 核心体验对齐：三级预设 + 分支增强 + LaTeX + UseCase 拆分",
        details = listOf(
            "DB v21 迁移：BranchEntity 表 + PresetEntity.scope + presetId 字段",
            "SendMessageUseCase 拆分为 3 个 UseCase + MessageExecutionHelper",
            "三级预设系统：Chat > Character > Global 非空字段覆盖合并",
            "预设 UI：scope 筛选 Tab + scope 标签 + scope 选择器",
            "聊天分支/书签增强：BranchEntity CRUD + createBranchFromMessage",
            "LaTeX 渲染：JLatexMathPlugin（$...$ 行内 / $$...$$ 块级）"
        ),
        lifeLog = "开发了这么久，我都不知道哪里有bug"
    ),
    VersionEntry(
        version = "v1.2.7",
        date = "2026-05-24",
        summary = "升级稳定性保障 + 迁移兜底",
        details = listOf(
            "数据库迁移失败时自动删除重建，防止升级安装闪退",
            "新增 3 个数据库索引：chats.updated_at、memory_atoms 排序、world_book_entries 活跃状态",
            "16 处 catch 块补 Log.w 日志输出",
            "提取 PROACTIVE_TRIGGER_DELAY_MS 常量"
        ),
        lifeLog = "让我猜猜是谁还没喝过1分钱的瑞幸😎 是我"
    ),
    VersionEntry(
        version = "v1.2.6",
        date = "2026-05-24",
        summary = "DB v19 索引 + 剩余 catch 块补日志",
        details = listOf(
            "chats.updated_at、memory_atoms 排序、world_book_entries 活跃状态索引",
            "BackupManager、MemoryExtractorService、SillyTavernImporter 等 16 处 catch 补日志",
            "ChatScreen 常量提取"
        ),
        lifeLog = null
    ),
    VersionEntry(
        version = "v1.2.5",
        date = "2026-05-23",
        summary = "第五轮深度分析修复",
        details = listOf(
            "Coil AsyncImage 补 key 参数防止重组闪烁",
            "12 个 ViewModel 的 stateIn 流补 distinctUntilChanged 去重",
            "10 处 catch 块补 Log.w 日志"
        ),
        lifeLog = null
    ),
    VersionEntry(
        version = "v1.2.4",
        date = "2026-05-23",
        summary = "第四轮深度分析修复 — CancellationException + TTS 加密",
        details = listOf(
            "retryWithBackoff、SSE 解析等关键路径保护 CancellationException 不被吞",
            "TTS API Key 接入 CryptoHelper 加密存储",
            "ProGuard 补 data.store 包 keep 规则"
        ),
        lifeLog = null
    ),
    VersionEntry(
        version = "v1.2.3",
        date = "2026-05-23",
        summary = "异常处理加固 + SendMessageUseCase 单测",
        details = listOf(
            "continueGeneration/regenerate 异常不再静默吞掉，错误消息可见",
            "BackgroundProactiveWorker 流式收集加 try-catch",
            "SendMessageUseCase 单元测试 25 个用例"
        ),
        lifeLog = null
    ),
    VersionEntry(
        version = "v1.2.2",
        date = "2026-05-22",
        summary = "交互体验全面优化",
        details = listOf(
            "消息操作栏：点击消息弹出复制/编辑/朗读/置顶/删除按钮",
            "头像裁剪：编辑角色时支持圆形裁剪头像",
            "群聊优化：根据健谈度概率回复，消息间隔更自然",
            "Material You 动态主题已验证完善"
        ),
        lifeLog = null
    ),
    VersionEntry(
        version = "v1.2.1",
        date = "2026-05-22",
        summary = "稳定性修复 + 代码清理 + 测试全覆盖",
        details = listOf(
            "修复 API 流式请求的资源泄漏（OkHttp Response 未关闭）",
            "修复世界书递归匹配逻辑错误",
            "群聊支持 Author's Note 注入",
            "Gemini API 使用 systemInstruction 字段传递系统提示",
            "TTS 关闭时清理状态",
            "移除未使用的分支操作代码",
            "数据库性能优化：消息查询新增复合索引",
            "测试从 31 扩充到 178 个（16 个测试套件）"
        ),
        lifeLog = "我的生活被token掏光了一切(;´༎ຶД༎ຶ`)"
    ),
    VersionEntry(
        version = "v1.2.0",
        date = "2026-05-20",
        summary = "全量数据备份/恢复 + 消息引用回复",
        details = listOf(
            "支持导出/导入全部数据（角色、对话、消息、记忆、世界书等）",
            "长按消息可引用回复，显示引用卡片",
            "消息置顶功能",
            "从此处删除功能"
        ),
        lifeLog = "什么520？我只知道今天原神更新。谁能给我520亿 token？ʚ♡⃛ɞ(ू•ᴗ•ू❁)"
    ),
    VersionEntry(
        version = "v1.1.0",
        date = "2026-05-19",
        summary = "TTS 语音朗读 — 双引擎支持",
        details = listOf(
            "系统 TTS + OpenAI TTS 双引擎",
            "支持语速、音调调节",
            "长按消息即可朗读"
        ),
        lifeLog = "不愧是蜜桃四季春，大佬，同款"
    ),
    VersionEntry(
        version = "v1.0.7",
        date = "2026-05-18",
        summary = "全局后台主动对话",
        details = listOf(
            "角色可在后台主动发起对话",
            "通知栏直接回复",
            "API Key 加密存储（Android Keystore AES-256-GCM）"
        ),
        lifeLog = "今天满课，好累"
    ),
    VersionEntry(
        version = "v1.0.6",
        date = "2026-05-17",
        summary = "聊天界面健谈度调整",
        details = listOf(
            "单聊 + 群聊分离的健谈度控制",
            "优化消息分段和流式输出"
        ),
        lifeLog = "还是要多多学习啊，能力严重不够分＞(￣▽￣ = ￣︿￣)<裂"
    ),
    VersionEntry(
        version = "v1.0.5",
        date = "2026-05-16",
        summary = "修复思维链模型返回大量 null 字符串",
        details = listOf(
            "优化消息清理逻辑",
            "提升稳定性"
        ),
        lifeLog = "不管了，玩游戏去了ღ(✞╹◡╹✞)ற"
    ),
    VersionEntry(
        version = "v1.0.4",
        date = "2026-05-15",
        summary = "代码优化和 Bug 修复",
        details = listOf(
            "优化内存使用",
            "修复多个边界情况"
        ),
        lifeLog = "各种小问题接连冒出来，越修越累˃̣̣̥᷄⌓˂̣̣̥᷅"
    ),
    VersionEntry(
        version = "v1.0.3",
        date = "2026-05-14",
        summary = "群聊持久化 + 首页群聊显示",
        details = listOf(
            "群聊数据持久化存储",
            "首页显示已创建的群聊",
            "群聊成员管理优化"
        ),
        lifeLog = "这玩意真的能做出来吗？Bug好多"
    ),
    VersionEntry(
        version = "v1.0.2",
        date = "2026-05-13",
        summary = "主动对话系统 + 群聊功能",
        details = listOf(
            "AI 主动发起对话",
            "群聊支持 @ 提及",
            "健谈度控制",
            "活人感深度优化：多消息拆分 + 自然间隔"
        ),
        lifeLog = "吃了份塔斯汀，感觉他家的汉堡没以前大了，但是也挺好吃的"
    ),
    VersionEntry(
        version = "v1.0.1",
        date = "2026-05-12",
        summary = "语言切换修复 + 全局 i18n",
        details = listOf(
            "修复中英文语言切换竞态条件",
            "双语 strings.xml 完整覆盖",
            "README/Release 英文化"
        ),
        lifeLog = "早知道不搞了，这么多bug"
    ),
    VersionEntry(
        version = "v1.0.0-beta1",
        date = "2026-05-11",
        summary = "酒馆 AI 首个测试版发布",
        details = listOf(
            "SillyTavern 数据格式兼容",
            "角色卡导入/导出",
            "世界书系统",
            "多 API 支持（OpenAI/Claude/KoboldAI）",
            "聊天界面 + 消息管理"
        ),
        lifeLog = "今天晚上突然来了兴趣，于是把这个项目赶出来了"
    )
)
