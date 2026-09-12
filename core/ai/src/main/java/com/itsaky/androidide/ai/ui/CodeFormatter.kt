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

package com.itsaky.androidide.ai.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

object CodeFormatter {

  private val gson = GsonBuilder().setPrettyPrinting().create()

  private val KEYWORDS = setOf(
    "abstract", "actual", "as", "break", "by", "catch", "class", "companion", "const",
    "continue", "data", "do", "else", "enum", "expect", "extends", "false", "final",
    "finally", "for", "fun", "get", "if", "implements", "import", "in", "infix", "init",
    "inline", "interface", "internal", "is", "lateinit", "new", "null", "object", "open",
    "operator", "out", "override", "package", "private", "protected", "public", "return",
    "sealed", "set", "static", "super", "suspend", "this", "throw", "true", "try", "typealias",
    "val", "var", "vararg", "void", "when", "while"
  )

  fun prettyJson(raw: String): String {
    val element = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return raw
    if (element.isJsonNull) {
      return raw
    }
    return runCatching { gson.toJson(element) }.getOrDefault(raw)
  }

  fun highlight(source: String, colors: SyntaxColors): CharSequence {
    val builder = SpannableStringBuilder(source)
    var index = 0

    while (index < source.length) {
      val ch = source[index]

      when {
        ch == '"' || ch == '\'' -> {
          val end = findStringEnd(source, index, ch)
          builder.paint(index, end, colors.string)
          index = end
        }

        ch == '/' && index + 1 < source.length && source[index + 1] == '/' -> {
          val end = source.indexOf('\n', index).let { if (it < 0) source.length else it }
          builder.paint(index, end, colors.comment)
          index = end
        }

        ch == '#' -> {
          val end = source.indexOf('\n', index).let { if (it < 0) source.length else it }
          builder.paint(index, end, colors.comment)
          index = end
        }

        ch.isDigit() && !isWordChar(source.getOrNull(index - 1)) -> {
          var end = index
          while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '.')) {
            end++
          }
          builder.paint(index, end, colors.number)
          index = end
        }

        ch.isLetter() || ch == '_' -> {
          var end = index
          while (end < source.length && isWordChar(source[end])) {
            end++
          }
          val word = source.substring(index, end)
          if (word in KEYWORDS) {
            builder.paint(index, end, colors.keyword, bold = true)
          }
          index = end
        }

        else -> index++
      }
    }

    return builder
  }

  private fun findStringEnd(source: String, start: Int, quote: Char): Int {
    var index = start + 1
    while (index < source.length) {
      when (source[index]) {
        '\\' -> index++
        quote -> return index + 1
        '\n' -> return index
      }
      index++
    }
    return source.length
  }

  private fun isWordChar(ch: Char?): Boolean =
    ch != null && (ch.isLetterOrDigit() || ch == '_')

  private fun SpannableStringBuilder.paint(
    start: Int,
    end: Int,
    color: Int,
    bold: Boolean = false
  ) {
    if (start >= end || end > length) {
      return
    }
    setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    if (bold) {
      setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
  }
}

data class SyntaxColors(
  val keyword: Int,
  val string: Int,
  val number: Int,
  val comment: Int
)
