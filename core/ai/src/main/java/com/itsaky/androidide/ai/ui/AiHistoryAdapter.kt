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

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.ai.databinding.LayoutAiHistoryItemBinding
import com.itsaky.androidide.ai.history.ChatSessionInfo
import com.itsaky.androidide.resources.R

class AiHistoryAdapter(
  private val onResume: (ChatSessionInfo) -> Unit,
  private val onDelete: (ChatSessionInfo) -> Unit
) : ListAdapter<ChatSessionInfo, AiHistoryAdapter.SessionViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder =
    SessionViewHolder(
      LayoutAiHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
      onResume,
      onDelete
    )

  override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class SessionViewHolder(
    private val binding: LayoutAiHistoryItemBinding,
    private val onResume: (ChatSessionInfo) -> Unit,
    private val onDelete: (ChatSessionInfo) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(info: ChatSessionInfo) {
      val context = binding.root.context

      binding.sessionTitle.text = info.title.ifBlank {
        context.getString(R.string.msg_ai_history_untitled)
      }

      binding.sessionSubtitle.text = DateUtils.getRelativeTimeSpanString(
        info.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
      )

      binding.root.setOnClickListener { onResume(info) }
      binding.sessionDelete.setOnClickListener { onDelete(info) }
    }
  }

  companion object {

    private val DIFF = object : DiffUtil.ItemCallback<ChatSessionInfo>() {

      override fun areItemsTheSame(oldItem: ChatSessionInfo, newItem: ChatSessionInfo): Boolean =
        oldItem.id == newItem.id

      override fun areContentsTheSame(oldItem: ChatSessionInfo, newItem: ChatSessionInfo): Boolean =
        oldItem == newItem
    }
  }
}
