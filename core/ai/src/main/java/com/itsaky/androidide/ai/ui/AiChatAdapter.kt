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
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.ai.databinding.LayoutAiMessageBinding
import com.itsaky.androidide.ai.databinding.LayoutAiToolCallBinding
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.resolveAttr

class AiChatAdapter : ListAdapter<ChatEntry, RecyclerView.ViewHolder>(DIFF) {

  override fun getItemViewType(position: Int): Int = when (getItem(position)) {
    is ChatEntry.Tool -> TYPE_TOOL
    else -> TYPE_MESSAGE
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return if (viewType == TYPE_TOOL) {
      ToolViewHolder(LayoutAiToolCallBinding.inflate(inflater, parent, false))
    } else {
      MessageViewHolder(LayoutAiMessageBinding.inflate(inflater, parent, false))
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val entry = getItem(position)) {
      is ChatEntry.Tool -> (holder as ToolViewHolder).bind(entry)
      else -> (holder as MessageViewHolder).bind(entry)
    }
  }

  class MessageViewHolder(private val binding: LayoutAiMessageBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(entry: ChatEntry) {
      val context = binding.root.context
      when (entry) {
        is ChatEntry.User -> {
          binding.role.setText(R.string.title_ai_you)
          binding.message.text = entry.text
          binding.root.setCardBackgroundColor(
            context.resolveAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
          )
        }

        is ChatEntry.Assistant -> {
          binding.role.setText(R.string.title_ai_assistant)
          binding.message.text = if (entry.streaming && entry.text.isEmpty()) {
            ELLIPSIS
          } else {
            entry.text
          }
          binding.root.setCardBackgroundColor(
            context.resolveAttr(com.google.android.material.R.attr.colorSurfaceContainer)
          )
        }

        is ChatEntry.Error -> {
          binding.role.setText(R.string.title_ai_error)
          binding.message.text = entry.text
          binding.root.setCardBackgroundColor(
            context.resolveAttr(com.google.android.material.R.attr.colorErrorContainer)
          )
        }

        is ChatEntry.Tool -> Unit
      }
    }
  }

  class ToolViewHolder(private val binding: LayoutAiToolCallBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(entry: ChatEntry.Tool) {
      val context = binding.root.context
      binding.summary.text = entry.summary

      binding.status.text = when (entry.state) {
        ToolEntryState.RUNNING -> context.getString(R.string.msg_ai_tool_running)
        ToolEntryState.AWAITING_APPROVAL -> context.getString(R.string.msg_ai_awaiting_approval)
        ToolEntryState.SUCCEEDED -> entry.call.name
        ToolEntryState.FAILED -> context.getString(R.string.title_ai_error)
      }

      binding.output.text = entry.output
      binding.output.visibility = if (entry.output.isBlank()) {
        android.view.View.GONE
      } else {
        android.view.View.VISIBLE
      }
    }
  }

  companion object {

    private const val TYPE_MESSAGE = 0
    private const val TYPE_TOOL = 1

    private const val ELLIPSIS = "\u2026"

    private val DIFF = object : DiffUtil.ItemCallback<ChatEntry>() {

      override fun areItemsTheSame(oldItem: ChatEntry, newItem: ChatEntry): Boolean =
        oldItem.id == newItem.id

      override fun areContentsTheSame(oldItem: ChatEntry, newItem: ChatEntry): Boolean =
        oldItem == newItem
    }
  }
}
