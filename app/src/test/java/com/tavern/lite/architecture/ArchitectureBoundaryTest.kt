package com.tavern.lite.architecture

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.fail
import org.junit.Test

class ArchitectureBoundaryTest {

    @Test
    fun `ui layer does not import direct network services or daos`() {
        val uiRoot = existingPath(
            Paths.get("src/main/java/com/tavern/lite/ui"),
            Paths.get("app/src/main/java/com/tavern/lite/ui")
        )
        val violations = mutableListOf<String>()

        Files.walk(uiRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .forEach { path ->
                    Files.readAllLines(path).forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed == DIRECT_CHAT_API_IMPORT || trimmed.startsWith(DAO_IMPORT_PREFIX)) {
                            violations += "${uiRoot.relativize(path)}:${index + 1}: $trimmed"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "UI layer must depend on UseCase/Coordinator boundaries instead of ChatApiService or DAO imports:\n" +
                    violations.joinToString(separator = "\n")
            )
        }
    }

    private fun existingPath(vararg candidates: Path): Path =
        candidates.firstOrNull { Files.exists(it) }
            ?: error("None of the expected source roots exist: ${candidates.joinToString()}")

    private companion object {
        const val DIRECT_CHAT_API_IMPORT = "import com.tavern.lite.network.ChatApiService"
        const val DAO_IMPORT_PREFIX = "import com.tavern.lite.data.db.dao."
    }
}
