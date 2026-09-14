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

package com.itsaky.androidide.ai.ui

import android.view.View
import com.itsaky.androidide.ai.agent.SubagentSession
import com.itsaky.androidide.ai.agent.SubagentStatus
import com.itsaky.androidide.ai.databinding.LayoutAiSubagentsBinding
import com.itsaky.androidide.resources.R

class SubagentPanel(
  private val binding: LayoutAiSubagentsBinding,
  private val activityAdapter: SubagentActivityAdapter
) {

  private var sessions: List<SubagentSession> = emptyList()
  private var selectedId: Long? = null

  fun submit(updated: List<SubagentSession>) {
    sessions = updated
    syncTabs()

    val selected = updated.firstOrNull { it.id == selectedId } ?: updated.firstOrNull()
    selectedId = selected?.id

    if (selected == null) {
      binding.subagentTask.visibility = View.GONE
      binding.subagentMeta.visibility = View.GONE
      binding.subagentsEmpty.visibility = View.VISIBLE
      binding.subagentsEmpty.setText(R.string.msg_ai_subagents_empty)
      activityAdapter.submitList(emptyList())
      return
    }

    render(selected)
  }

  fun select(id: Long) {
    if (selectedId == id) {
      return
    }
    selectedId = id
    sessions.firstOrNull { it.id == id }?.let(::render)
  }

  private fun render(session: SubagentSession) {
    val context = binding.root.context

    binding.subagentTask.visibility = View.VISIBLE
    binding.subagentTask.text = session.prompt.trim().ifEmpty { session.description }

    val status = context.getString(
      when {
        session.awaitingApproval -> R.string.msg_ai_awaiting_approval
        session.status == SubagentStatus.RUNNING -> R.string.msg_ai_subagent_state_running
        session.status == SubagentStatus.SUCCEEDED -> R.string.msg_ai_subagent_state_succeeded
        else -> R.string.msg_ai_subagent_state_failed
      }
    )
    val scope = context.getString(R.string.msg_ai_subagent_scope, session.scope.wireValue)
    val calls = context.resources.getQuantityString(
      R.plurals.msg_ai_subagent_tool_calls,
      session.toolCalls,
      session.toolCalls
    )

    binding.subagentMeta.visibility = View.VISIBLE
    binding.subagentMeta.text = listOf(status, scope, calls).joinToString(" · ")

    val atBottom = !binding.subagentActivity.canScrollVertically(1)
    activityAdapter.submitList(session.activity) {
      if (atBottom && session.activity.isNotEmpty()) {
        binding.subagentActivity.scrollToPosition(session.activity.size - 1)
      }
    }

    val empty = session.activity.isEmpty()
    binding.subagentsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    if (empty) {
      binding.subagentsEmpty.setText(R.string.msg_ai_subagent_activity_empty)
    }
  }

  private fun syncTabs() {
    val tabs = binding.subagentTabs
    val existing = (0 until tabs.tabCount).mapNotNull { tabs.getTabAt(it)?.tag as? Long }

    if (existing != sessions.map { it.id }) {
      tabs.removeAllTabs()
      sessions.forEach { session ->
        val tab = tabs.newTab()
        tab.tag = session.id
        tab.text = tabLabel(session)
        tabs.addTab(tab, session.id == selectedId)
      }
      return
    }

    sessions.forEachIndexed { index, session ->
      tabs.getTabAt(index)?.let { tab ->
        val label = tabLabel(session)
        if (tab.text != label) {
          tab.text = label
        }
      }
    }
  }

  private fun tabLabel(session: SubagentSession): String {
    val marker = when (session.status) {
      SubagentStatus.RUNNING -> "\u25CF"
      SubagentStatus.SUCCEEDED -> "\u2713"
      SubagentStatus.FAILED -> "\u2717"
    }
    return "$marker ${session.description}"
  }
}
