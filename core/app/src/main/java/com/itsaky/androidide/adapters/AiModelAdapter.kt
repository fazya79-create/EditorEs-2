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

package com.itsaky.androidide.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.ai.provider.ModelInfo
import com.itsaky.androidide.databinding.LayoutAiModelItemBinding

/** Shows the fetched model listing of an AI provider, with the selected model checked. */
class AiModelAdapter(
  private val onSelect: (ModelInfo) -> Unit
) : ListAdapter<ModelInfo, AiModelAdapter.ModelViewHolder>(DIFF) {

  var selectedModel: String = ""
    set(value) {
      if (field != value) {
        field = value
        notifyDataSetChanged()
      }
    }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder =
    ModelViewHolder(
      LayoutAiModelItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

  override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
    holder.bind(getItem(position), selectedModel, onSelect)
  }

  class ModelViewHolder(
    private val binding: LayoutAiModelItemBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(model: ModelInfo, selectedModel: String, onSelect: (ModelInfo) -> Unit) {
      binding.modelId.apply {
        text = model.id
        isChecked = model.id == selectedModel
        setOnClickListener { onSelect(model) }
      }
    }
  }

  companion object {

    private val DIFF = object : DiffUtil.ItemCallback<ModelInfo>() {

      override fun areItemsTheSame(oldItem: ModelInfo, newItem: ModelInfo): Boolean =
        oldItem.id == newItem.id

      override fun areContentsTheSame(oldItem: ModelInfo, newItem: ModelInfo): Boolean =
        oldItem == newItem
    }
  }
}
