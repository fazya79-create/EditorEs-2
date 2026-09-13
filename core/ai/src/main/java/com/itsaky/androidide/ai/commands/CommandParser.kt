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

object CommandParser {

  private val NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
  private val ARGUMENT_PATTERN = Regex("\"([^\"]*)\"|'([^']*)'|(\\S+)")
  private val POSITIONAL_PATTERN = Regex("\\$(\\d)")

  const val ARGUMENTS_PLACEHOLDER = "\$ARGUMENTS"

  fun parse(input: String): SlashInvocation? {
    val text = input.trim()
    if (!text.startsWith('/') || text.length < 2) {
      return null
    }

    val body = text.substring(1)
    val separator = body.indexOfFirst { it.isWhitespace() }
    val name = if (separator < 0) body else body.substring(0, separator)
    if (!NAME_PATTERN.matches(name)) {
      return null
    }

    val arguments = if (separator < 0) "" else body.substring(separator + 1).trim()
    return SlashInvocation(name = name, arguments = arguments)
  }

  fun expand(template: String, arguments: String): String {
    val tokens = ARGUMENT_PATTERN.findAll(arguments)
      .map { match ->
        match.groupValues[1].ifEmpty { match.groupValues[2].ifEmpty { match.groupValues[3] } }
      }
      .toList()

    val positions = POSITIONAL_PATTERN.findAll(template)
      .mapNotNull { it.groupValues[1].toIntOrNull() }
      .filter { it >= 1 }
      .toSortedSet()

    val last = positions.maxOrNull()
    var expanded = POSITIONAL_PATTERN.replace(template) { match ->
      val position = match.groupValues[1].toIntOrNull() ?: return@replace match.value
      if (position < 1) {
        return@replace match.value
      }
      val index = position - 1
      when {
        index >= tokens.size -> ""
        position == last -> tokens.drop(index).joinToString(" ")
        else -> tokens[index]
      }
    }

    val usesArguments = template.contains(ARGUMENTS_PLACEHOLDER)
    expanded = expanded.replace(ARGUMENTS_PLACEHOLDER, arguments)

    if (positions.isEmpty() && !usesArguments && arguments.isNotBlank()) {
      expanded = "$expanded\n\n$arguments"
    }

    return expanded.trim()
  }
}
