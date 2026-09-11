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

class EditFileTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Replace an exact snippet of text in an existing file of the currently opened " +
        "project. The snippet given as 'old_text' must appear exactly once in the file.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "Path of the file to edit, relative to the project directory."
          },
          "old_text": {
            "type": "string",
            "description": "The exact text to replace. Must occur exactly once in the file."
          },
          "new_text": {
            "type": "string",
            "description": "The text to insert in place of 'old_text'."
          }
        },
        "required": ["path", "old_text", "new_text"]
      }
    """.trimIndent(),
    mutating = true
  )

  override fun describe(arguments: JsonObject): String =
    "Edit ${arguments.get("path")?.asStringOrNull() ?: "<missing path>"}"

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val path = arguments.get("path")?.asStringOrNull()
        ?: throw ToolException("Missing required argument 'path'.")
      val oldText = arguments.get("old_text")?.asStringOrNull()
        ?: throw ToolException("Missing required argument 'old_text'.")
      val newText = arguments.get("new_text")?.asStringOrNull()
        ?: throw ToolException("Missing required argument 'new_text'.")

      if (oldText.isEmpty()) {
        throw ToolException("The 'old_text' argument must not be empty.")
      }

      val file = WorkspacePaths.resolve(path)
      if (!file.isFile) {
        throw ToolException("No such file: ${WorkspacePaths.relativize(file)}")
      }

      val content = file.readText()
      val first = content.indexOf(oldText)
      if (first < 0) {
        throw ToolException("The given 'old_text' was not found in ${WorkspacePaths.relativize(file)}.")
      }
      if (content.indexOf(oldText, first + 1) >= 0) {
        throw ToolException(
          "The given 'old_text' occurs more than once in ${WorkspacePaths.relativize(file)}." +
              " Provide a longer, unique snippet."
        )
      }

      file.writeText(content.replaceRange(first, first + oldText.length, newText))

      "Edited ${WorkspacePaths.relativize(file)}."
    }

  companion object {

    const val NAME = "edit_file"
  }
}
