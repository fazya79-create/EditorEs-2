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
import com.itsaky.androidide.ai.model.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReadFileTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Read the contents of a text file inside the currently opened project. " +
        "Paths are relative to the project directory.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "Path of the file to read, relative to the project directory."
          }
        },
        "required": ["path"]
      }
    """.trimIndent(),
    mutating = false
  )

  override fun describe(arguments: JsonObject): String =
    "Read ${arguments.get("path")?.asStringOrNull() ?: "<missing path>"}"

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val path = arguments.get("path")?.asStringOrNull()
        ?: throw ToolException("Missing required argument 'path'.")

      val file = WorkspacePaths.resolve(path)
      if (!file.isFile) {
        throw ToolException("No such file: ${WorkspacePaths.relativize(file)}")
      }
      if (file.length() > MAX_FILE_SIZE) {
        throw ToolException(
          "File is too large to read (${file.length()} bytes, limit $MAX_FILE_SIZE)."
        )
      }

      val text = file.readText()
      if (text.length > MAX_CHARS) {
        text.take(MAX_CHARS) + "\n... [truncated, ${text.length - MAX_CHARS} characters omitted]"
      } else {
        text
      }
    }

  companion object {

    const val NAME = "read_file"

    private const val MAX_FILE_SIZE = 1L * 1024 * 1024
    private const val MAX_CHARS = 100_000
  }
}
