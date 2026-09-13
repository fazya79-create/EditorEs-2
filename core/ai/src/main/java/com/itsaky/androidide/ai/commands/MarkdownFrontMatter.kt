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

package com.itsaky.androidide.ai.commands

data class FrontMatterDocument(
  val values: Map<String, String>,
  val body: String
)

object MarkdownFrontMatter {

  private const val DELIMITER = "---"

  fun parse(text: String): FrontMatterDocument {
    val normalized = text.replace("\r\n", "\n")
    val lines = normalized.split('\n')
    if (lines.firstOrNull()?.trim() != DELIMITER) {
      return FrontMatterDocument(emptyMap(), normalized)
    }

    val end = lines.drop(1).indexOfFirst { it.trim() == DELIMITER }
    if (end < 0) {
      return FrontMatterDocument(emptyMap(), normalized)
    }

    val values = LinkedHashMap<String, String>()
    lines.subList(1, end + 1).forEach { line ->
      val separator = line.indexOf(':')
      if (separator <= 0) {
        return@forEach
      }
      val key = line.substring(0, separator).trim().lowercase()
      val value = line.substring(separator + 1).trim().trim('"', '\'')
      if (key.isNotEmpty()) {
        values[key] = value
      }
    }

    val body = lines.drop(end + 2).joinToString("\n")
    return FrontMatterDocument(values, body)
  }
}
