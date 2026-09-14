/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.ai.tools

import android.content.Context
import com.google.gson.JsonObject
import com.itsaky.androidide.ai.model.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GlobTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Find files in the project by path pattern, newest first. Use it to locate " +
        "files by name or extension when you do not know where they are, for example " +
        "'**/*.cpp' or 'src/**/CMakeLists.txt'. '*' matches within one path segment, '**' " +
        "matches across segments and '?' matches a single character. This searches names only: " +
        "use grep to search file contents.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "pattern": {
            "type": "string",
            "description": "Glob pattern matched against paths relative to the project directory, for example '**/*.h'."
          },
          "path": {
            "type": "string",
            "description": "Optional subdirectory to search in, relative to the project directory. Defaults to the whole project."
          }
        },
        "required": ["pattern"]
      }
    """.trimIndent(),
    mutating = false,
    parallelSafe = true
  )

  override fun describe(arguments: JsonObject): String {
    val pattern = arguments.get("pattern")?.asStringOrNull() ?: "<missing pattern>"
    val path = arguments.get("path")?.asStringOrNull()?.takeIf { it.isNotBlank() }
    return if (path == null) "Find $pattern" else "Find $pattern in $path"
  }

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val pattern = arguments.get("pattern")?.asStringOrNull()?.trim()
      if (pattern.isNullOrEmpty()) {
        throw ToolException("Missing required argument 'pattern'.")
      }

      val root = arguments.get("path")?.asStringOrNull()?.takeIf { it.isNotBlank() }
        ?.let { WorkspacePaths.resolve(it) }
        ?: WorkspacePaths.projectDir()
      if (!root.isDirectory) {
        throw ToolException("No such directory: ${WorkspacePaths.relativize(root)}")
      }

      val regex = GlobMatcher.toRegex(pattern)
      val matches = FileWalker.walk(root).filter { regex.matches(WorkspacePaths.relativize(it)) }
        .sortedByDescending { it.lastModified() }
        .toList()

      if (matches.isEmpty()) {
        return@withContext "No files match '$pattern'."
      }

      buildString {
        matches.take(MAX_RESULTS).forEach { appendLine(WorkspacePaths.relativize(it)) }
        if (matches.size > MAX_RESULTS) {
          append("... [${matches.size - MAX_RESULTS} more matches omitted]")
        }
      }.trimEnd()
    }

  companion object {

    const val NAME = "glob"

    private const val MAX_RESULTS = 200
  }
}

object GlobMatcher {

  fun toRegex(pattern: String): Regex {
    val normalized = pattern.trim().removePrefix("./")
    val builder = StringBuilder()
    var index = 0

    while (index < normalized.length) {
      when (val char = normalized[index]) {
        '*' ->
          if (index + 1 < normalized.length && normalized[index + 1] == '*') {
            if (index + 2 < normalized.length && normalized[index + 2] == '/') {
              builder.append("(?:[^/]+/)*")
              index += 2
            } else {
              builder.append(".*")
              index++
            }
          } else {
            builder.append("[^/]*")
          }

        '?' -> builder.append("[^/]")
        '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> {
          builder.append('\\').append(char)
        }

        else -> builder.append(char)
      }
      index++
    }

    return Regex(builder.toString())
  }
}

object FileWalker {

  private val IGNORED = setOf(
    ".git", ".gradle", ".idea", "build", "node_modules", ".cxx", "out", ".androidide"
  )

  fun walk(root: File): Sequence<File> = root.walkTopDown()
    .onEnter { it.name !in IGNORED && !it.name.startsWith(".git") }
    .filter { it.isFile }
}
