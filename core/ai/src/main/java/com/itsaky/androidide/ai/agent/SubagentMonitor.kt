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

package com.itsaky.androidide.ai.agent

import com.itsaky.androidide.ai.tools.ToolScope

enum class SubagentStatus {
  RUNNING,
  SUCCEEDED,
  FAILED
}

enum class SubagentActivityKind {
  REASONING,
  MESSAGE,
  TOOL_STARTED,
  TOOL_FINISHED,
  NOTICE
}

data class SubagentActivity(
  val id: Long,
  val kind: SubagentActivityKind,
  val title: String = "",
  val text: String = "",
  val isError: Boolean = false
)

data class SubagentSession(
  val id: Long,
  val description: String,
  val prompt: String,
  val scope: ToolScope,
  val status: SubagentStatus = SubagentStatus.RUNNING,
  val toolCalls: Int = 0,
  val summary: String = "",
  val awaitingApproval: Boolean = false,
  val startedAt: Long = System.currentTimeMillis(),
  val activity: List<SubagentActivity> = emptyList()
)

class SubagentMonitor(private val onChanged: () -> Unit = {}) {

  private val lock = Any()
  private val sessions = LinkedHashMap<Long, SubagentSession>()
  private var nextActivityId = 0L

  fun start(id: Long, description: String, prompt: String, scope: ToolScope) {
    synchronized(lock) {
      sessions[id] = SubagentSession(
        id = id,
        description = description,
        prompt = prompt,
        scope = scope
      )
    }
    onChanged()
  }

  fun update(id: Long, transform: (SubagentSession) -> SubagentSession) {
    synchronized(lock) {
      val existing = sessions[id] ?: return
      sessions[id] = transform(existing)
    }
    onChanged()
  }

  fun record(
    id: Long,
    kind: SubagentActivityKind,
    title: String = "",
    text: String = "",
    isError: Boolean = false
  ) {
    synchronized(lock) {
      val existing = sessions[id] ?: return
      val entry = SubagentActivity(
        id = nextActivityId++,
        kind = kind,
        title = title,
        text = text,
        isError = isError
      )
      sessions[id] = existing.copy(activity = capped(existing.activity + entry))
    }
    onChanged()
  }

  fun appendStream(id: Long, kind: SubagentActivityKind, text: String) {
    synchronized(lock) {
      val existing = sessions[id] ?: return
      val last = existing.activity.lastOrNull()
      val activity = if (last != null && last.kind == kind) {
        existing.activity.dropLast(1) + last.copy(text = last.text + text)
      } else {
        capped(
          existing.activity + SubagentActivity(
            id = nextActivityId++,
            kind = kind,
            text = text
          )
        )
      }
      sessions[id] = existing.copy(activity = activity)
    }
    onChanged()
  }

  fun setAwaitingApproval(awaiting: Boolean) {
    var changed = false
    synchronized(lock) {
      sessions.keys.toList().forEach { key ->
        val session = sessions.getValue(key)
        if (session.status == SubagentStatus.RUNNING && session.awaitingApproval != awaiting) {
          sessions[key] = session.copy(awaitingApproval = awaiting)
          changed = true
        }
      }
    }
    if (changed) {
      onChanged()
    }
  }

  fun finishRunning(status: SubagentStatus, summary: String) {    var changed = false
    synchronized(lock) {
      sessions.keys.toList().forEach { key ->
        val session = sessions.getValue(key)
        if (session.status == SubagentStatus.RUNNING) {
          sessions[key] = session.copy(
            status = status,
            awaitingApproval = false,
            summary = session.summary.ifBlank { summary }
          )
          changed = true
        }
      }
    }
    if (changed) {
      onChanged()
    }
  }

  fun snapshot(): List<SubagentSession> = synchronized(lock) { sessions.values.toList() }

  fun clear() {
    val wasEmpty = synchronized(lock) {
      val empty = sessions.isEmpty()
      sessions.clear()
      empty
    }
    if (!wasEmpty) {
      onChanged()
    }
  }

  private fun capped(activity: List<SubagentActivity>): List<SubagentActivity> =
    if (activity.size <= MAX_ACTIVITY) activity else activity.takeLast(MAX_ACTIVITY)

  companion object {

    private const val MAX_ACTIVITY = 200
  }
}
