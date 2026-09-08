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

package com.itsaky.androidide.projects

import java.io.File

object ClangFormat {

  const val FILE_NAME = ".clang-format"

  const val KEY_BASED_ON_STYLE = "BasedOnStyle"
  const val KEY_INDENT_WIDTH = "IndentWidth"
  const val KEY_TAB_WIDTH = "TabWidth"
  const val KEY_USE_TAB = "UseTab"
  const val KEY_COLUMN_LIMIT = "ColumnLimit"

  const val DEFAULT_BASED_ON_STYLE = "LLVM"
  const val DEFAULT_INDENT_WIDTH = 4
  const val DEFAULT_TAB_WIDTH = 4
  const val DEFAULT_USE_TAB = "Never"
  const val DEFAULT_COLUMN_LIMIT = 80

  val BASED_ON_STYLES = listOf("LLVM", "Google", "Chromium", "Mozilla", "WebKit")
  val USE_TAB_VALUES = listOf("Never", "ForIndentation", "ForContinuationAndIndentation", "Always")

  fun defaultContent(): String {
    return """
      $KEY_BASED_ON_STYLE: $DEFAULT_BASED_ON_STYLE
      $KEY_INDENT_WIDTH: $DEFAULT_INDENT_WIDTH
      $KEY_TAB_WIDTH: $DEFAULT_TAB_WIDTH
      $KEY_USE_TAB: $DEFAULT_USE_TAB
      $KEY_COLUMN_LIMIT: $DEFAULT_COLUMN_LIMIT
    """.trimIndent() + "\n"
  }

  fun isCppProject(projectDir: File): Boolean = File(projectDir, "CMakeLists.txt").isFile

  fun file(projectDir: File): File = File(projectDir, FILE_NAME)

  fun ensureDefault(projectDir: File): Boolean {
    val target = file(projectDir)
    if (target.exists()) {
      return false
    }
    return runCatching { target.writeText(defaultContent()) }.isSuccess
  }

  fun readValue(projectDir: File, key: String): String? {
    val target = file(projectDir)
    if (!target.isFile) {
      return null
    }
    val regex = keyRegex(key)
    return runCatching {
      target.useLines { lines ->
        lines.firstNotNullOfOrNull { line -> regex.find(line)?.groupValues?.get(1)?.trim() }
      }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
  }

  fun writeValue(projectDir: File, key: String, value: String): Boolean {
    val target = file(projectDir)
    val content = if (target.isFile) {
      runCatching { target.readText() }.getOrNull() ?: return false
    } else {
      defaultContent()
    }
    val regex = keyRegex(key)
    val lines = content.split('\n').toMutableList()
    val index = lines.indexOfFirst { regex.containsMatchIn(it) }
    if (index >= 0) {
      lines[index] = "$key: $value"
    } else {
      while (lines.isNotEmpty() && lines.last().isBlank()) {
        lines.removeAt(lines.lastIndex)
      }
      lines.add("$key: $value")
      lines.add("")
    }
    return runCatching { target.writeText(lines.joinToString("\n")) }.isSuccess
  }

  private fun keyRegex(key: String): Regex = Regex("^${Regex.escape(key)}\\s*:\\s*([^#]*)")
}
