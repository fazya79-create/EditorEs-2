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

import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE.FIELD
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE.OPERATOR
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE.TEXT_NORMAL
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE.forComment
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE.forKeyword
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE.forString
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle.makeStyle

class ShellAnalyzer : SimpleAnalyzeManager<Unit>() {

  override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
    val builder = MappedSpans.Builder()
    var line = 0
    var column = 0
    var index = 0

    fun span(style: Long) = builder.addIfNeeded(line, column, style)

    while (index < text.length) {
      if (delegate.isCancelled) {
        break
      }

      val ch = text[index]

      when {
        ch == '\n' -> {
          line++
          column = 0
          index++
          continue
        }

        // A '#' only opens a comment at the start of a word, so "a#b" and "${x#y}" stay plain.
        ch == '#' && (column == 0 || text[index - 1].isWhitespace()) -> {
          span(forComment())
          while (index < text.length && text[index] != '\n') {
            index++
            column++
          }
          continue
        }

        ch == '\'' -> {
          span(forString())
          index++
          column++
          while (index < text.length && text[index] != '\'') {
            if (text[index] == '\n') {
              line++
              column = 0
            } else {
              column++
            }
            index++
          }
          if (index < text.length) {
            index++
            column++
          }
          span(makeStyle(TEXT_NORMAL))
          continue
        }

        ch == '"' -> {
          span(forString())
          index++
          column++
          while (index < text.length && text[index] != '"') {
            if (text[index] == '\\' && index + 1 < text.length) {
              index++
              column++
            }
            if (index < text.length) {
              if (text[index] == '\n') {
                line++
                column = 0
              } else {
                column++
              }
              index++
            }
          }
          if (index < text.length) {
            index++
            column++
          }
          span(makeStyle(TEXT_NORMAL))
          continue
        }

        ch == '$' -> {
          span(makeStyle(FIELD))
          index++
          column++
          if (index < text.length && text[index] == '{') {
            while (index < text.length && text[index] != '}' && text[index] != '\n') {
              index++
              column++
            }
            if (index < text.length && text[index] == '}') {
              index++
              column++
            }
          } else {
            while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) {
              index++
              column++
            }
          }
          span(makeStyle(TEXT_NORMAL))
          continue
        }

        ch in OPERATOR_CHARS -> {
          span(makeStyle(OPERATOR))
          index++
          column++
          span(makeStyle(TEXT_NORMAL))
          continue
        }

        ch.isLetter() || ch == '_' -> {
          val start = index
          while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) {
            index++
          }
          val word = text.substring(start, index)
          span(if (word in KEYWORDS) forKeyword() else makeStyle(TEXT_NORMAL))
          column += index - start
          span(makeStyle(TEXT_NORMAL))
          continue
        }

        else -> {
          span(makeStyle(TEXT_NORMAL))
          index++
          column++
        }
      }
    }

    builder.determine(line)
    return Styles(builder.build())
  }

  companion object {

    private val OPERATOR_CHARS = charArrayOf('|', '&', ';', '<', '>', '=', '!', '*')

    private val KEYWORDS = hashSetOf(
      "if", "then", "else", "elif", "fi", "case", "esac", "for", "select", "while", "until",
      "do", "done", "in", "function", "time", "coproc",
      "echo", "cd", "exit", "export", "return", "source", "alias", "unalias", "set", "unset",
      "local", "readonly", "declare", "typeset", "eval", "exec", "shift", "trap", "read",
      "printf", "test", "break", "continue", "pushd", "popd", "let", "wait", "kill", "true",
      "false"
    )
  }
}
