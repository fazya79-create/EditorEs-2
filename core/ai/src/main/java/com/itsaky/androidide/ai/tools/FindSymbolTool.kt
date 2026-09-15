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
import com.itsaky.androidide.ai.agent.SymbolIndexRegistry
import com.itsaky.androidide.ai.agent.SymbolLocation
import com.itsaky.androidide.ai.model.ToolSpec
import java.io.File

enum class SymbolQuery {
  DEFINITION,
  REFERENCES
}

class FindSymbolTool(private val query: SymbolQuery) : AiTool {

  override val spec = ToolSpec(
    name = if (query == SymbolQuery.DEFINITION) DEFINITION_NAME else REFERENCES_NAME,
    description = when (query) {
      SymbolQuery.DEFINITION ->
        "Resolve where a C/C++ symbol is defined, using the language server's index rather " +
            "than a text search. Point it at an occurrence of the symbol by file, line and " +
            "column and it answers with the defining file and line. Prefer this over grep for " +
            "C/C++ symbols: it understands overloads, namespaces and macros, so it does not " +
            "confuse a comment or an unrelated identifier with the real definition."

      SymbolQuery.REFERENCES ->
        "List every place a C/C++ symbol is used, using the language server's index rather " +
            "than a text search. Point it at an occurrence of the symbol by file, line and " +
            "column. Use it before renaming or changing a function's signature, to find the " +
            "call sites you have to update. Prefer this over grep for C/C++ symbols: a text " +
            "search also matches comments, strings and unrelated identifiers that merely " +
            "share the name."
    },
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "File containing an occurrence of the symbol, relative to the project directory."
          },
          "line": {
            "type": "integer",
            "description": "1-based line number of the occurrence."
          },
          "column": {
            "type": "integer",
            "description": "1-based column of the symbol on that line. Point at the symbol's name, not the surrounding punctuation."
          }
        },
        "required": ["path", "line", "column"]
      }
    """.trimIndent(),
    mutating = false,
    parallelSafe = true
  )

  override fun describe(arguments: JsonObject): String {
    val path = arguments.get("path")?.asStringOrNull() ?: "<missing path>"
    val line = arguments.get("line")?.asIntOrNull() ?: 0
    return when (query) {
      SymbolQuery.DEFINITION -> "Find definition at $path:$line"
      SymbolQuery.REFERENCES -> "Find references at $path:$line"
    }
  }

  override suspend fun execute(context: Context, arguments: JsonObject): String {
    val index = SymbolIndexRegistry.current()
      ?: throw ToolException(
        "The C/C++ language server is not available. Open a C or C++ file in the editor first, " +
            "and make sure the NDK and CMake are installed."
      )

    val path = arguments.get("path")?.asStringOrNull()
      ?: throw ToolException("Missing required argument 'path'.")
    val line = arguments.get("line")?.asIntOrNull()
      ?: throw ToolException("Missing required argument 'line'.")
    val column = arguments.get("column")?.asIntOrNull()
      ?: throw ToolException("Missing required argument 'column'.")

    val file = WorkspacePaths.resolve(path)
    if (!file.isFile) {
      throw ToolException("No such file: ${WorkspacePaths.relativize(file)}")
    }

    // The language server counts from zero; the tool's arguments are 1-based so they line up
    // with what read_file and grep report.
    val zeroLine = (line - 1).coerceAtLeast(0)
    val zeroColumn = (column - 1).coerceAtLeast(0)

    val locations = when (query) {
      SymbolQuery.DEFINITION -> index.definition(file, zeroLine, zeroColumn)
      SymbolQuery.REFERENCES -> index.references(file, zeroLine, zeroColumn)
    }

    return render(locations)
  }

  private fun render(locations: List<SymbolLocation>): String {
    if (locations.isEmpty()) {
      return when (query) {
        SymbolQuery.DEFINITION ->
          "No definition found. The symbol may be declared only in a header the compilation " +
              "database does not cover, or the position may not point at a symbol."

        SymbolQuery.REFERENCES -> "No references found."
      }
    }

    val shown = locations.take(MAX_RESULTS)
    return buildString {
      shown.forEach { location ->
        append(location.path).append(':').append(location.line).append(':')
          .append(location.column)
        if (location.preview.isNotBlank()) {
          append(": ").append(location.preview.take(MAX_PREVIEW))
        }
        append('\n')
      }
      appendLine()
      append(locations.size).append(if (locations.size == 1) " result" else " results")
      if (locations.size > shown.size) {
        append(" (showing the first ").append(shown.size).append(')')
      }
    }.trimEnd()
  }

  companion object {

    const val DEFINITION_NAME = "find_definition"
    const val REFERENCES_NAME = "find_references"

    private const val MAX_RESULTS = 100
    private const val MAX_PREVIEW = 200
  }
}
