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
import com.itsaky.androidide.ai.commands.SlashCommand
import com.itsaky.androidide.ai.databinding.LayoutAiCommandItemBinding

class CommandSuggestionAdapter(private val onPick: (SlashCommand) -> Unit) :
  ListAdapter<SlashCommand, CommandSuggestionAdapter.ViewHolder>(DIFF) {

  class ViewHolder(val binding: LayoutAiCommandItemBinding) :
    RecyclerView.ViewHolder(binding.root)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
    LayoutAiCommandItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
  )

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val command = getItem(position)
    holder.binding.name.text = "/${command.name}"
    holder.binding.description.text = command.description
    holder.binding.description.visibility = if (command.description.isBlank()) {
      View.GONE
    } else {
      View.VISIBLE
    }
    holder.binding.root.setOnClickListener { onPick(command) }
  }

  companion object {

    private val DIFF = object : DiffUtil.ItemCallback<SlashCommand>() {

      override fun areItemsTheSame(oldItem: SlashCommand, newItem: SlashCommand): Boolean =
        oldItem.name == newItem.name

      override fun areContentsTheSame(oldItem: SlashCommand, newItem: SlashCommand): Boolean =
        oldItem == newItem
    }
  }
}
