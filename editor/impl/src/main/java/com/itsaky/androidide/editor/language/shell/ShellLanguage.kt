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

package com.itsaky.androidide.editor.language.shell

import com.itsaky.androidide.editor.language.IDELanguage
import com.itsaky.androidide.editor.language.utils.CommonSymbolPairs
import io.github.rosemoe.sora.lang.Language.INTERRUPTION_LEVEL_STRONG
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.widget.SymbolPairMatch
import java.io.File

class ShellLanguage : IDELanguage() {

  private val analyzer = ShellAnalyzer()

  override fun getAnalyzeManager(): AnalyzeManager = analyzer

  override fun getInterruptionLevel(): Int = INTERRUPTION_LEVEL_STRONG

  override fun getSymbolPairs(): SymbolPairMatch = CommonSymbolPairs()

  override fun getNewlineHandlers(): Array<NewlineHandler> = emptyArray()

  override fun getIndentAdvance(line: String): Int {
    return if (opensBlock(line)) getTabSize() else 0
  }

  override fun destroy() {}

  companion object {

    private val INDENT_OPENERS = listOf("then", "do", "in", "{", "(")

    val EXTENSIONS = setOf("sh", "bash", "zsh", "ksh", "profile", "bashrc", "zshrc", "env")

    private val KNOWN_NAMES = setOf(
      ".bashrc", ".bash_profile", ".zshrc", ".profile", ".env", "gradlew"
    )

    internal fun opensBlock(line: String): Boolean {
      val trimmed = line.substringBefore('#').trim()
      return INDENT_OPENERS.any { trimmed == it || trimmed.endsWith(" $it") }
    }

    fun handles(file: File): Boolean {
      val name = file.name
      return name.substringAfterLast('.', "").lowercase() in EXTENSIONS ||
        name.lowercase() in KNOWN_NAMES
    }
  }
}
