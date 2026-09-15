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

import android.content.Context
import com.google.gson.JsonObject
import com.itsaky.androidide.ai.agent.DocumentSymbolEntry
import com.itsaky.androidide.ai.agent.SymbolIndexRegistry
import com.itsaky.androidide.ai.model.ToolSpec

class DocumentSymbolsTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "List the types, functions and members a C/C++ file declares, with the line " +
        "each one starts on, using the language server's index. Use it to understand a file's " +
        "shape before reading it, and to find the line a function starts on so you can pass " +
        "that position to find_references. Much cheaper than reading a large file in full.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "File to outline, relative to the project directory."
          }
        },
        "required": ["path"]
      }
    """.trimIndent(),
    mutating = false,
    parallelSafe = true
  )

  override fun describe(arguments: JsonObject): String =
    "Outline ${arguments.get("path")?.asStringOrNull() ?: "<missing path>"}"

  override suspend fun execute(context: Context, arguments: JsonObject): String {
    val index = SymbolIndexRegistry.current()
      ?: throw ToolException(
        "The C/C++ language server is not available. Open a C or C++ file in the editor first, " +
            "and make sure the NDK and CMake are installed."
      )

    val path = arguments.get("path")?.asStringOrNull()
      ?: throw ToolException("Missing required argument 'path'.")

    val file = WorkspacePaths.resolve(path)
    if (!file.isFile) {
      throw ToolException("No such file: ${WorkspacePaths.relativize(file)}")
    }

    return render(index.documentSymbols(file))
  }

  private fun render(symbols: List<DocumentSymbolEntry>): String {
    if (symbols.isEmpty()) {
      return "No symbols found. The file may be empty, or not covered by the compilation database."
    }

    val shown = symbols.take(MAX_SYMBOLS)
    return buildString {
      shown.forEach { symbol ->
        append(symbol.line).append(": ").append(symbol.kind).append(' ')
        if (symbol.container.isNotBlank()) {
          append(symbol.container).append("::")
        }
        append(symbol.name)
        if (symbol.detail.isNotBlank()) {
          append(' ').append(symbol.detail.take(MAX_DETAIL))
        }
        append('\n')
      }
      appendLine()
      append(symbols.size).append(if (symbols.size == 1) " symbol" else " symbols")
      if (symbols.size > shown.size) {
        append(" (showing the first ").append(shown.size).append(')')
      }
    }.trimEnd()
  }

  companion object {

    const val NAME = "document_symbols"

    private const val MAX_SYMBOLS = 300
    private const val MAX_DETAIL = 120
  }
}
