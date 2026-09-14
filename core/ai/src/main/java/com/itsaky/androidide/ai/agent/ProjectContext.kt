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

package com.itsaky.androidide.ai.agent

import java.io.File

data class EditorSelection(val startLine: Int, val endLine: Int, val text: String)

data class EditorContext(
  val filePath: String,
  val selection: EditorSelection? = null,
  val openFiles: List<String> = emptyList()
)

fun interface EditorContextProvider {

  fun current(): EditorContext?
}

object EditorContextRegistry {

  @Volatile
  private var provider: EditorContextProvider? = null

  fun install(value: EditorContextProvider?) {
    provider = value
  }

  fun current(): EditorContext? = runCatching { provider?.current() }.getOrNull()
}

object ProjectInstructions {

  val FILE_NAMES = listOf("AGENTS.md", "CLAUDE.md", ".androidide/AGENTS.md")

  private const val MAX_BYTES = 32L * 1024
  private const val MAX_CHARS = 24_000

  fun load(projectDir: File?): String {
    val project = projectDir ?: return ""

    val file = FILE_NAMES.asSequence()
      .map { project.resolve(it) }
      .firstOrNull { it.isFile && it.length() in 1..MAX_BYTES }
      ?: return ""

    val text = runCatching { file.readText() }.getOrNull()?.trim().orEmpty()
    if (text.isEmpty()) {
      return ""
    }

    return if (text.length > MAX_CHARS) {
      text.take(MAX_CHARS) + "\n... [instructions truncated]"
    } else {
      text
    }
  }
}
