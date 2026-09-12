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
import com.itsaky.androidide.eventbus.events.file.ProjectFilesChangedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

class WriteFileTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Create or overwrite a text file inside the currently opened project. " +
        "Paths are relative to the project directory. Parent directories are created as needed.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "Path of the file to write, relative to the project directory."
          },
          "content": {
            "type": "string",
            "description": "The complete new content of the file."
          }
        },
        "required": ["path", "content"]
      }
    """.trimIndent(),
    mutating = true
  )

  override fun describe(arguments: JsonObject): String {
    val path = arguments.get("path")?.asStringOrNull() ?: "<missing path>"
    val length = arguments.get("content")?.asStringOrNull()?.length ?: 0
    return "Write $length characters to $path"
  }

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val path = arguments.get("path")?.asStringOrNull()
        ?: throw ToolException("Missing required argument 'path'.")
      val content = arguments.get("content")?.asStringOrNull()
        ?: throw ToolException("Missing required argument 'content'.")

      val file = WorkspacePaths.resolve(path)
      if (file.isDirectory) {
        throw ToolException("Cannot write to a directory: ${WorkspacePaths.relativize(file)}")
      }

      val existed = file.isFile
      file.parentFile?.mkdirs()
      file.writeText(content)

      EventBus.getDefault().post(ProjectFilesChangedEvent())

      val verb = if (existed) "Updated" else "Created"
      "$verb ${WorkspacePaths.relativize(file)} (${content.length} characters)."
    }

  companion object {

    const val NAME = "write_file"
  }
}
