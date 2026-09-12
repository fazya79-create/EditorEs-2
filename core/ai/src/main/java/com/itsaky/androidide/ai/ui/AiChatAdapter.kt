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
import com.itsaky.androidide.ai.databinding.LayoutAiInterruptedBinding
import com.itsaky.androidide.ai.databinding.LayoutAiMessageBinding
import com.itsaky.androidide.ai.databinding.LayoutAiThinkingBinding
import com.itsaky.androidide.ai.databinding.LayoutAiToolCallBinding
import com.itsaky.androidide.ai.tools.RunShellTool
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.resolveAttr

class AiChatAdapter(
  private val onToggleExpanded: (Long) -> Unit,
  private val onRetry: () -> Unit
) : ListAdapter<ChatEntry, RecyclerView.ViewHolder>(DIFF) {

  override fun getItemViewType(position: Int): Int = when (getItem(position)) {
    is ChatEntry.Tool -> TYPE_TOOL
    is ChatEntry.Thinking -> TYPE_THINKING
    is ChatEntry.Interrupted -> TYPE_INTERRUPTED
    else -> TYPE_MESSAGE
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return when (viewType) {
      TYPE_TOOL -> ToolViewHolder(
        LayoutAiToolCallBinding.inflate(inflater, parent, false),
        onToggleExpanded
      )

      TYPE_THINKING -> ThinkingViewHolder(
        LayoutAiThinkingBinding.inflate(inflater, parent, false),
        onToggleExpanded
      )

      TYPE_INTERRUPTED -> InterruptedViewHolder(
        LayoutAiInterruptedBinding.inflate(inflater, parent, false),
        onRetry
      )

      else -> MessageViewHolder(LayoutAiMessageBinding.inflate(inflater, parent, false))
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val entry = getItem(position)) {
      is ChatEntry.Tool -> (holder as ToolViewHolder).bind(entry)
      is ChatEntry.Thinking -> (holder as ThinkingViewHolder).bind(entry)
      is ChatEntry.Interrupted -> (holder as InterruptedViewHolder).bind(entry)
      else -> (holder as MessageViewHolder).bind(entry)
    }
  }

  class InterruptedViewHolder(
    private val binding: LayoutAiInterruptedBinding,
    private val onRetry: () -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(entry: ChatEntry.Interrupted) {
      val context = binding.root.context
      binding.message.text = context.getString(
        R.string.msg_ai_interrupted_detail,
        entry.text.ifBlank { context.getString(R.string.msg_ai_interrupted) }
      )
      binding.retry.setOnClickListener { onRetry() }
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
          binding.message.setTextIsSelectable(!entry.streaming)
          when {
            entry.streaming && entry.text.isEmpty() -> binding.message.text = ELLIPSIS
            entry.streaming -> binding.message.text = entry.text
            else -> MarkdownRenderer.render(binding.message, entry.text)
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

        is ChatEntry.Notice -> {
          binding.role.setText(R.string.title_ai_notice)
          binding.message.text = when (entry.kind) {
            NoticeKind.COMPACTED ->
              context.getString(R.string.msg_ai_context_compacted, entry.count)

            NoticeKind.COMPACTION_FAILED ->
              context.getString(R.string.msg_ai_context_compaction_failed)
          }
          binding.root.setCardBackgroundColor(
            context.resolveAttr(com.google.android.material.R.attr.colorSurfaceContainerLow)
          )
        }

        else -> Unit
      }
    }
  }

  class ThinkingViewHolder(
    private val binding: LayoutAiThinkingBinding,
    private val onToggleExpanded: (Long) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(entry: ChatEntry.Thinking) {
      binding.title.setText(
        if (entry.streaming) R.string.msg_ai_thinking else R.string.msg_ai_thinking_done
      )
      binding.chevron.text = if (entry.expanded) CHEVRON_UP else CHEVRON_DOWN
      binding.reasoning.text = entry.text
      binding.reasoning.visibility = if (entry.expanded && entry.text.isNotBlank()) {
        View.VISIBLE
      } else {
        View.GONE
      }
      binding.header.setOnClickListener { onToggleExpanded(entry.id) }
    }
  }

  class ToolViewHolder(
    private val binding: LayoutAiToolCallBinding,
    private val onToggleExpanded: (Long) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(entry: ChatEntry.Tool) {
      val context = binding.root.context
      binding.summary.text = entry.summary

      binding.status.text = when (entry.state) {
        ToolEntryState.RUNNING -> context.getString(R.string.msg_ai_tool_running)
        ToolEntryState.AWAITING_APPROVAL -> context.getString(R.string.msg_ai_awaiting_approval)
        ToolEntryState.SUCCEEDED -> entry.call.name
        ToolEntryState.FAILED -> context.getString(R.string.title_ai_error)
      }

      binding.stateIcon.text = when (entry.state) {
        ToolEntryState.RUNNING -> ICON_RUNNING
        ToolEntryState.AWAITING_APPROVAL -> ICON_PENDING
        ToolEntryState.SUCCEEDED -> ICON_OK
        ToolEntryState.FAILED -> ICON_FAILED
      }
      binding.stateIcon.setTextColor(
        context.resolveAttr(
          when (entry.state) {
            ToolEntryState.FAILED -> com.google.android.material.R.attr.colorError
            ToolEntryState.SUCCEEDED -> com.google.android.material.R.attr.colorPrimary
            else -> com.google.android.material.R.attr.colorOnSurfaceVariant
          }
        )
      )

      binding.chevron.text = if (entry.expanded) CHEVRON_UP else CHEVRON_DOWN
      binding.details.visibility = if (entry.expanded) View.VISIBLE else View.GONE

      if (entry.expanded) {
        val colors = syntaxColors(context)
        binding.arguments.text = CodeFormatter.highlight(
          CodeFormatter.prettyJson(entry.call.argumentsJson),
          colors
        )

        val hasOutput = entry.output.isNotBlank()
        binding.outputLabel.visibility = if (hasOutput) View.VISIBLE else View.GONE
        binding.output.visibility = if (hasOutput) View.VISIBLE else View.GONE
        if (hasOutput) {
          binding.output.text = if (entry.call.name == RunShellTool.NAME) {
            entry.output
          } else {
            CodeFormatter.highlight(CodeFormatter.prettyJson(entry.output), colors)
          }
        }
      }

      binding.header.setOnClickListener { onToggleExpanded(entry.id) }
    }

    private fun syntaxColors(context: android.content.Context) = SyntaxColors(
      keyword = context.resolveAttr(com.google.android.material.R.attr.colorPrimary),
      string = context.resolveAttr(com.google.android.material.R.attr.colorTertiary),
      number = context.resolveAttr(com.google.android.material.R.attr.colorSecondary),
      comment = context.resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
    )
  }

  companion object {

    private const val TYPE_MESSAGE = 0
    private const val TYPE_TOOL = 1
    private const val TYPE_THINKING = 2
    private const val TYPE_INTERRUPTED = 3

    private const val ELLIPSIS = "\u2026"
    private const val CHEVRON_DOWN = "\u2304"
    private const val CHEVRON_UP = "\u2303"

    private const val ICON_RUNNING = "\u25CF"
    private const val ICON_PENDING = "\u25CB"
    private const val ICON_OK = "\u2713"
    private const val ICON_FAILED = "\u2715"

    private val DIFF = object : DiffUtil.ItemCallback<ChatEntry>() {

      override fun areItemsTheSame(oldItem: ChatEntry, newItem: ChatEntry): Boolean =
        oldItem.id == newItem.id

      override fun areContentsTheSame(oldItem: ChatEntry, newItem: ChatEntry): Boolean =
        oldItem == newItem
    }
  }
}
