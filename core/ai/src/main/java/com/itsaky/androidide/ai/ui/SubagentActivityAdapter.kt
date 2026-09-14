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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.ai.agent.SubagentActivity
import com.itsaky.androidide.ai.agent.SubagentActivityKind
import com.itsaky.androidide.ai.databinding.LayoutAiSubagentActivityBinding
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.resolveAttr

class SubagentActivityAdapter :
  ListAdapter<SubagentActivity, SubagentActivityAdapter.ActivityViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder =
    ActivityViewHolder(
      LayoutAiSubagentActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

  override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class ActivityViewHolder(private val binding: LayoutAiSubagentActivityBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(activity: SubagentActivity) {
      val context = binding.root.context

      binding.activityTitle.text = when (activity.kind) {
        SubagentActivityKind.REASONING -> context.getString(R.string.title_ai_subagent_thinking)
        SubagentActivityKind.MESSAGE -> context.getString(R.string.title_ai_subagent_message)
        SubagentActivityKind.NOTICE -> context.getString(R.string.title_ai_subagent_notice)

        SubagentActivityKind.TOOL_STARTED ->
          context.getString(R.string.title_ai_subagent_tool_started, activity.title)

        SubagentActivityKind.TOOL_FINISHED ->
          context.getString(R.string.title_ai_subagent_tool_finished, activity.title)
      }

      binding.activityTitle.setTextColor(
        context.resolveAttr(
          if (activity.isError) {
            com.google.android.material.R.attr.colorError
          } else {
            com.google.android.material.R.attr.colorOnSurface
          }
        )
      )

      val text = activity.text.trim().let {
        if (it.length > MAX_TEXT) it.take(MAX_TEXT) + "\u2026" else it
      }
      binding.activityText.text = text
      binding.activityText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }
  }

  companion object {

    private const val MAX_TEXT = 4000

    private val DIFF = object : DiffUtil.ItemCallback<SubagentActivity>() {

      override fun areItemsTheSame(oldItem: SubagentActivity, newItem: SubagentActivity): Boolean =
        oldItem.id == newItem.id

      override fun areContentsTheSame(
        oldItem: SubagentActivity,
        newItem: SubagentActivity
      ): Boolean = oldItem == newItem
    }
  }
}
