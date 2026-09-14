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

class GrepTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Search the contents of the project's files with a regular expression and " +
        "return the matching paths with their line numbers. Use it to find where a symbol is " +
        "defined or used before you read whole files. Narrow the search with 'include' when " +
        "you only care about one kind of file. Use glob instead when you are looking for a " +
        "file by name rather than by content.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "pattern": {
            "type": "string",
            "description": "Regular expression to search for, for example 'void\\\\s+init'."
          },
          "path": {
            "type": "string",
            "description": "Optional subdirectory to search in, relative to the project directory. Defaults to the whole project."
          },
          "include": {
            "type": "string",
            "description": "Optional glob limiting which files are searched, for example '*.cpp' or '**/*.h'."
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
    val include = arguments.get("include")?.asStringOrNull()?.takeIf { it.isNotBlank() }
    return if (include == null) "Search $pattern" else "Search $pattern in $include"
  }

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val pattern = arguments.get("pattern")?.asStringOrNull()
      if (pattern.isNullOrEmpty()) {
        throw ToolException("Missing required argument 'pattern'.")
      }

      val regex = runCatching { Regex(pattern) }.getOrElse {
        throw ToolException("'$pattern' is not a valid regular expression: ${it.message}")
      }

      val root = arguments.get("path")?.asStringOrNull()?.takeIf { it.isNotBlank() }
        ?.let { WorkspacePaths.resolve(it) }
        ?: WorkspacePaths.projectDir()
      if (!root.isDirectory) {
        throw ToolException("No such directory: ${WorkspacePaths.relativize(root)}")
      }

      val include = arguments.get("include")?.asStringOrNull()?.takeIf { it.isNotBlank() }
        ?.let { GlobMatcher.toRegex(if (it.contains('/')) it else "**/$it") }

      val lines = mutableListOf<String>()
      var files = 0
      var truncated = false

      for (file in FileWalker.walk(root)) {
        val relative = WorkspacePaths.relativize(file)
        if (include != null && !include.matches(relative)) {
          continue
        }
        if (file.length() > MAX_FILE_SIZE || isBinary(file)) {
          continue
        }

        var matchedHere = false
        file.useLines { sequence ->
          sequence.forEachIndexed { index, line ->
            if (lines.size >= MAX_MATCHES) {
              truncated = true
              return@forEachIndexed
            }
            if (regex.containsMatchIn(line)) {
              matchedHere = true
              lines += "$relative:${index + 1}: ${line.trim().take(MAX_LINE)}"
            }
          }
        }
        if (matchedHere) {
          files++
        }
        if (truncated) {
          break
        }
      }

      if (lines.isEmpty()) {
        return@withContext "No matches for '$pattern'."
      }

      buildString {
        lines.forEach { appendLine(it) }
        appendLine()
        append("${lines.size} matches in $files files")
        if (truncated) {
          append(" (stopped at the first $MAX_MATCHES matches)")
        }
      }.trimEnd()
    }

  private fun isBinary(file: File): Boolean {
    val head = ByteArray(BINARY_PROBE)
    val read = file.inputStream().use { it.read(head) }
    if (read <= 0) {
      return false
    }
    return (0 until read).any { head[it] == 0.toByte() }
  }

  companion object {

    const val NAME = "grep"

    private const val MAX_MATCHES = 200
    private const val MAX_LINE = 300
    private const val MAX_FILE_SIZE = 2L * 1024 * 1024
    private const val BINARY_PROBE = 1024
  }
}
