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

/**
 * Commonmark only starts a GFM table when its header row begins a new block. Models frequently
 * emit a table immediately after a paragraph line, in which case the rows are swallowed as lazy
 * continuation text and shown as literal pipes. Inserting the missing blank line restores the
 * table without altering any other markdown.
 */
object MarkdownNormalizer {

  private val DELIMITER = Regex("""^\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?$""")

  fun normalize(markdown: String): String {
    if (!markdown.contains('|')) {
      return markdown
    }

    val lines = markdown.lines()
    val result = ArrayList<String>(lines.size + 4)

    for ((index, line) in lines.withIndex()) {
      if (
        index >= 1 &&
        isDelimiter(line) &&
        isRow(lines[index - 1]) &&
        index >= 2 &&
        lines[index - 2].isNotBlank() &&
        !isRow(lines[index - 2])
      ) {
        result.add(result.size - 1, "")
      }
      result.add(line)
    }

    return result.joinToString("\n")
  }

  private fun isDelimiter(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.contains('-') && DELIMITER.matches(trimmed)
  }

  private fun isRow(line: String): Boolean = line.contains('|')
}
