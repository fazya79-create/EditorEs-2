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

package com.itsaky.androidide.ai.frontmatter

sealed interface YamlValue {

  data class Scalar(val text: String) : YamlValue

  data class Sequence(val items: List<YamlValue>) : YamlValue

  data class Mapping(val entries: Map<String, YamlValue>) : YamlValue

  val scalarOrNull: String?
    get() = (this as? Scalar)?.text

  val itemsOrEmpty: List<YamlValue>
    get() = when (this) {
      is Sequence -> items
      is Scalar -> if (text.isEmpty()) emptyList() else listOf(this)
      is Mapping -> emptyList()
    }

  fun get(key: String): YamlValue? = (this as? Mapping)?.entries?.get(key)

  fun flatten(): String = when (this) {
    is Scalar -> text
    is Sequence -> items.joinToString(", ") { it.flatten() }
    is Mapping -> entries.entries.joinToString(", ") { "${it.key}: ${it.value.flatten()}" }
  }
}

data class YamlDocument(val root: YamlValue.Mapping, val body: String) {

  fun value(key: String): YamlValue? = root.entries[key]

  fun string(key: String): String? = value(key)?.let {
    when (it) {
      is YamlValue.Scalar -> it.text
      else -> it.flatten()
    }
  }

  fun strings(key: String): List<String> =
    value(key)?.itemsOrEmpty?.mapNotNull { it.scalarOrNull }?.filter { it.isNotEmpty() }
      ?: emptyList()

  fun flat(): Map<String, String> = root.entries.mapValues { it.value.flatten() }
}

object YamlFrontMatter {

  private const val DELIMITER = "---"

  fun parse(text: String): YamlDocument {
    val normalized = text.replace("\r\n", "\n").removePrefix("\uFEFF")
    val lines = normalized.split('\n')

    if (lines.firstOrNull()?.trim() != DELIMITER) {
      return YamlDocument(YamlValue.Mapping(emptyMap()), normalized)
    }

    val end = lines.drop(1).indexOfFirst { it.trim() == DELIMITER || it.trim() == "..." }
    if (end < 0) {
      return YamlDocument(YamlValue.Mapping(emptyMap()), normalized)
    }

    val front = lines.subList(1, end + 1)
    val body = lines.drop(end + 2).joinToString("\n")
    val root = parseBlock(Reader(front), 0)

    val mapping = root as? YamlValue.Mapping ?: YamlValue.Mapping(emptyMap())
    return YamlDocument(mapping, body)
  }

  private class Reader(private val lines: List<String>) {

    private var cursor = 0

    fun peek(): String? {
      while (cursor < lines.size && isSkippable(lines[cursor])) {
        cursor++
      }
      return lines.getOrNull(cursor)
    }

    fun next(): String? = peek()?.also { cursor++ }

    fun peekRaw(): String? = lines.getOrNull(cursor)

    fun advance() {
      cursor++
    }

    private fun isSkippable(line: String): Boolean {
      val trimmed = line.trim()
      return trimmed.isEmpty() || trimmed.startsWith('#')
    }
  }

  private fun indentOf(line: String): Int = line.takeWhile { it == ' ' }.length

  private fun parseBlock(reader: Reader, minIndent: Int): YamlValue {
    val first = reader.peek() ?: return YamlValue.Mapping(emptyMap())
    return if (first.trim().startsWith("- ") || first.trim() == "-") {
      parseSequence(reader, indentOf(first))
    } else {
      parseMapping(reader, minIndent)
    }
  }

  private fun parseMapping(reader: Reader, minIndent: Int): YamlValue {
    val entries = LinkedHashMap<String, YamlValue>()
    var indent = -1

    while (true) {
      val line = reader.peek() ?: break
      val lineIndent = indentOf(line)
      if (lineIndent < minIndent) {
        break
      }
      if (indent < 0) {
        indent = lineIndent
      }
      if (lineIndent != indent) {
        if (lineIndent < indent) {
          break
        }
        reader.advance()
        continue
      }

      val trimmed = line.trim()
      if (trimmed.startsWith("- ")) {
        break
      }

      val separator = keySeparator(trimmed)
      if (separator <= 0) {
        reader.advance()
        continue
      }

      reader.advance()
      val key = unquote(trimmed.substring(0, separator).trim())
      val inline = trimmed.substring(separator + 1).trim()

      entries[key] = when {
        inline == "|" || inline == ">" || inline.startsWith("|") || inline.startsWith(">") ->
          YamlValue.Scalar(parseBlockScalar(reader, indent, inline))

        inline.isEmpty() -> parseNested(reader, indent)
        else -> parseInline(inline)
      }
    }

    return YamlValue.Mapping(entries)
  }

  private fun parseNested(reader: Reader, parentIndent: Int): YamlValue {
    val next = reader.peek() ?: return YamlValue.Scalar("")
    val nextIndent = indentOf(next)
    if (nextIndent <= parentIndent && !next.trim().startsWith("- ")) {
      return YamlValue.Scalar("")
    }
    if (next.trim().startsWith("- ") || next.trim() == "-") {
      return parseSequence(reader, nextIndent)
    }
    return parseMapping(reader, nextIndent)
  }

  private fun parseSequence(reader: Reader, indent: Int): YamlValue {
    val items = mutableListOf<YamlValue>()

    while (true) {
      val line = reader.peek() ?: break
      val lineIndent = indentOf(line)
      val trimmed = line.trim()
      if (lineIndent != indent || !(trimmed.startsWith("- ") || trimmed == "-")) {
        break
      }

      reader.advance()
      val inline = trimmed.removePrefix("-").trim()
      items += when {
        inline.isEmpty() -> parseNested(reader, indent)
        keySeparator(inline) > 0 -> parseInline(inline).let { value ->
          if (value is YamlValue.Scalar && keySeparator(inline) > 0) {
            val separator = keySeparator(inline)
            YamlValue.Mapping(
              linkedMapOf(
                unquote(inline.substring(0, separator).trim()) to
                    parseInline(inline.substring(separator + 1).trim())
              )
            )
          } else {
            value
          }
        }

        else -> parseInline(inline)
      }
    }

    return YamlValue.Sequence(items)
  }

  private fun parseBlockScalar(reader: Reader, parentIndent: Int, header: String): String {
    val folded = header.trimStart('|', '>').let { header.startsWith(">") }
    val collected = mutableListOf<String>()
    var blockIndent = -1

    while (true) {
      val raw = reader.peekRaw() ?: break
      if (raw.isBlank()) {
        collected += ""
        reader.advance()
        continue
      }
      val lineIndent = indentOf(raw)
      if (lineIndent <= parentIndent) {
        break
      }
      if (blockIndent < 0) {
        blockIndent = lineIndent
      }
      collected += raw.drop(minOf(blockIndent, lineIndent))
      reader.advance()
    }

    while (collected.isNotEmpty() && collected.last().isEmpty()) {
      collected.removeAt(collected.size - 1)
    }

    return if (folded) {
      collected.joinToString(" ") { it.trim() }.trim()
    } else {
      collected.joinToString("\n")
    }
  }

  private fun parseInline(value: String): YamlValue {
    val text = stripComment(value).trim()
    if (text.startsWith("[") && text.endsWith("]")) {
      val inner = text.substring(1, text.length - 1)
      val items = splitTopLevel(inner).map { parseInline(it) }
      return YamlValue.Sequence(items)
    }
    if (text.startsWith("{") && text.endsWith("}")) {
      val inner = text.substring(1, text.length - 1)
      val entries = LinkedHashMap<String, YamlValue>()
      splitTopLevel(inner).forEach { part ->
        val separator = keySeparator(part)
        if (separator > 0) {
          entries[unquote(part.substring(0, separator).trim())] =
            parseInline(part.substring(separator + 1))
        }
      }
      return YamlValue.Mapping(entries)
    }
    return YamlValue.Scalar(unquote(text))
  }

  private fun splitTopLevel(text: String): List<String> {
    val parts = mutableListOf<String>()
    val builder = StringBuilder()
    var depth = 0
    var quote = ' '

    text.forEach { char ->
      when {
        quote != ' ' -> {
          builder.append(char)
          if (char == quote) {
            quote = ' '
          }
        }

        char == '"' || char == '\'' -> {
          quote = char
          builder.append(char)
        }

        char == '[' || char == '{' -> {
          depth++
          builder.append(char)
        }

        char == ']' || char == '}' -> {
          depth--
          builder.append(char)
        }

        char == ',' && depth == 0 -> {
          parts += builder.toString()
          builder.setLength(0)
        }

        else -> builder.append(char)
      }
    }

    if (builder.isNotBlank()) {
      parts += builder.toString()
    }
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
  }

  private fun keySeparator(text: String): Int {
    var quote = ' '
    var depth = 0
    text.forEachIndexed { index, char ->
      when {
        quote != ' ' -> if (char == quote) quote = ' '
        char == '"' || char == '\'' -> quote = char
        char == '[' || char == '{' -> depth++
        char == ']' || char == '}' -> depth--
        char == ':' && depth == 0 -> {
          val next = text.getOrNull(index + 1)
          if (next == null || next == ' ') {
            return index
          }
        }
      }
    }
    return -1
  }

  private fun stripComment(text: String): String {
    var quote = ' '
    text.forEachIndexed { index, char ->
      when {
        quote != ' ' -> if (char == quote) quote = ' '
        char == '"' || char == '\'' -> quote = char
        char == '#' && (index == 0 || text[index - 1] == ' ') -> return text.substring(0, index)
      }
    }
    return text
  }

  private fun unquote(text: String): String {
    val trimmed = text.trim()
    if (trimmed.length >= 2) {
      val first = trimmed.first()
      val last = trimmed.last()
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        val inner = trimmed.substring(1, trimmed.length - 1)
        return if (first == '"') {
          inner.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
        } else {
          inner.replace("''", "'")
        }
      }
    }
    return trimmed
  }
}
