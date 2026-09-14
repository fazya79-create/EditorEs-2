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

object UnifiedDiff {

  private const val CONTEXT_LINES = 3
  private const val MAX_LINES = 160
  private const val MAX_LINE_CHARS = 300

  fun render(before: String, after: String): String {
    if (before == after) {
      return ""
    }

    val old = before.split('\n')
    val new = after.split('\n')

    var prefix = 0
    while (prefix < old.size && prefix < new.size && old[prefix] == new[prefix]) {
      prefix++
    }

    var suffix = 0
    while (
      suffix < old.size - prefix &&
      suffix < new.size - prefix &&
      old[old.size - 1 - suffix] == new[new.size - 1 - suffix]
    ) {
      suffix++
    }

    val removed = old.subList(prefix, old.size - suffix)
    val added = new.subList(prefix, new.size - suffix)

    val leading = old.subList(maxOf(0, prefix - CONTEXT_LINES), prefix)
    val trailing = old.subList(
      old.size - suffix,
      minOf(old.size, old.size - suffix + CONTEXT_LINES)
    )

    val lines = mutableListOf<String>()
    lines += "@@ line ${prefix + 1} @@"
    leading.forEach { lines += "  ${clip(it)}" }
    removed.forEach { lines += "- ${clip(it)}" }
    added.forEach { lines += "+ ${clip(it)}" }
    trailing.forEach { lines += "  ${clip(it)}" }

    val shown = lines.take(MAX_LINES)
    return buildString {
      append(shown.joinToString("\n"))
      if (lines.size > MAX_LINES) {
        append("\n... [${lines.size - MAX_LINES} more diff lines]")
      }
      append("\n(-")
        .append(removed.size)
        .append(" +")
        .append(added.size)
        .append(" lines)")
    }
  }

  private fun clip(line: String): String =
    if (line.length <= MAX_LINE_CHARS) line else line.take(MAX_LINE_CHARS) + "…"
}
