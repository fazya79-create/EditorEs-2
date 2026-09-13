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

enum class TodoStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED;

  val wireValue: String
    get() = name.lowercase()

  companion object {

    fun fromWire(value: String?): TodoStatus =
      entries.firstOrNull { it.wireValue == value?.trim()?.lowercase() } ?: PENDING
  }
}

data class TodoItem(
  val id: String,
  val content: String,
  val status: TodoStatus
)

class TodoStore {

  private val items = mutableListOf<TodoItem>()
  private var revision = 0

  fun read(): List<TodoItem> = items.toList()

  fun currentRevision(): Int = revision

  fun isEmpty(): Boolean = items.isEmpty()

  fun write(todos: List<TodoItem>): List<TodoItem> {
    val next = normalize(todos)
    if (next != items) {
      items.clear()
      items += next
      revision++
    }
    return read()
  }

  fun clear() {
    if (items.isNotEmpty()) {
      items.clear()
      revision++
    }
  }

  fun activeSummary(): String? {
    val active = items.filter { it.status == TodoStatus.PENDING || it.status == TodoStatus.IN_PROGRESS }
    if (active.isEmpty()) {
      return null
    }
    return buildString {
      append(INJECTION_HEADER)
      active.forEach { item ->
        append('\n')
        append("- ")
        append(marker(item.status))
        append(' ')
        append(item.id)
        append(". ")
        append(item.content)
      }
    }
  }

  private fun marker(status: TodoStatus): String = when (status) {
    TodoStatus.COMPLETED -> "[x]"
    TodoStatus.IN_PROGRESS -> "[>]"
    TodoStatus.PENDING -> "[ ]"
    TodoStatus.CANCELLED -> "[~]"
  }

  private fun normalize(todos: List<TodoItem>): List<TodoItem> {
    val byId = LinkedHashMap<String, TodoItem>()
    todos.forEachIndexed { index, item ->
      val id = item.id.trim().ifEmpty { (index + 1).toString() }
      val content = item.content.trim().take(MAX_CONTENT_CHARS)
      byId[id] = TodoItem(
        id = id,
        content = content.ifEmpty { NO_DESCRIPTION },
        status = item.status
      )
    }
    return byId.values.take(MAX_ITEMS)
  }

  companion object {

    const val INJECTION_HEADER = "Your task list was preserved across context compaction:"
    const val MAX_ITEMS = 64
    const val MAX_CONTENT_CHARS = 500

    private const val NO_DESCRIPTION = "(no description)"
  }
}
