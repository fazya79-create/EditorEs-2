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

class TodoWriteTool(private val store: TodoStore) : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Create and maintain a task list for the current conversation. Use it when " +
        "the work needs three or more distinct steps, when the user gives you several tasks, or " +
        "when you want the user to see your plan. Call it with no arguments to read the current " +
        "list. Writing replaces the whole list, so always send every item back. Keep exactly one " +
        "item in_progress at a time and mark an item completed only once the work is actually " +
        "done and verified, never because you intend to do it. Update the list one step at a " +
        "time, as a separate call of its own: mark an item in_progress before you start it, and " +
        "call this tool again to mark it completed the moment that item is finished, before you " +
        "begin the next one. Never batch several status changes into one call at the end of the " +
        "work, and never leave a finished item sitting at in_progress while you move on: the " +
        "user watches this list to follow your progress, so a list that only changes once at " +
        "the start and once at the end is worse than no list at all.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "todos": {
            "type": "array",
            "description": "The full task list. Omit to read the current list without changing it.",
            "items": {
              "type": "object",
              "properties": {
                "id": {
                  "type": "string",
                  "description": "Stable identifier for the item, for example '1'."
                },
                "content": {
                  "type": "string",
                  "description": "What the step does, phrased as an action."
                },
                "status": {
                  "type": "string",
                  "enum": ["pending", "in_progress", "completed", "cancelled"]
                }
              },
              "required": ["id", "content", "status"]
            }
          }
        },
        "required": []
      }
    """.trimIndent(),
    mutating = false
  )

  override fun describe(arguments: JsonObject): String {
    val todos = arguments.getAsJsonArray("todos") ?: return "Read the task list"
    val done = todos.count { element ->
      element.isJsonObject &&
          TodoStatus.fromWire(element.asJsonObject.get("status")?.asStringOrNull()) ==
          TodoStatus.COMPLETED
    }
    return "Update task list ($done/${todos.size()} done)"
  }

  override suspend fun execute(context: Context, arguments: JsonObject): String {
    val todos = arguments.get("todos")
    if (todos == null || todos.isJsonNull) {
      return render(store.read())
    }
    if (!todos.isJsonArray) {
      throw ToolException("The 'todos' argument must be an array of task objects.")
    }

    val parsed = todos.asJsonArray.mapIndexedNotNull { index, element ->
      if (!element.isJsonObject) {
        return@mapIndexedNotNull null
      }
      val item = element.asJsonObject
      TodoItem(
        id = item.get("id")?.asStringOrNull()?.trim().orEmpty().ifEmpty { (index + 1).toString() },
        content = item.get("content")?.asStringOrNull().orEmpty(),
        status = TodoStatus.fromWire(item.get("status")?.asStringOrNull())
      )
    }

    return render(store.write(parsed))
  }

  private fun render(items: List<TodoItem>): String {
    if (items.isEmpty()) {
      return "The task list is empty."
    }
    return buildString {
      items.forEach { item ->
        append(item.id)
        append(". [")
        append(item.status.wireValue)
        append("] ")
        append(item.content)
        append('\n')
      }
      append('\n')
      append(items.count { it.status == TodoStatus.COMPLETED })
      append(" of ")
      append(items.size)
      append(" completed.")
    }
  }

  companion object {

    const val NAME = "todo_write"
  }
}
