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
import com.itsaky.androidide.ai.databinding.LayoutAiSkillItemBinding
import com.itsaky.androidide.ai.skills.Skill
import com.itsaky.androidide.ai.skills.SkillSource
import com.itsaky.androidide.resources.R

class AiSkillAdapter(
  private val onDelete: (Skill) -> Unit,
  private val onSelect: (Skill) -> Unit
) : ListAdapter<Skill, AiSkillAdapter.SkillViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkillViewHolder =
    SkillViewHolder(
      LayoutAiSkillItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
      onDelete,
      onSelect
    )

  override fun onBindViewHolder(holder: SkillViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class SkillViewHolder(
    private val binding: LayoutAiSkillItemBinding,
    private val onDelete: (Skill) -> Unit,
    private val onSelect: (Skill) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(skill: Skill) {
      val context = binding.root.context

      binding.skillName.text = skill.name
      binding.skillDescription.text = skill.summary

      binding.skillBadge.setText(
        when (skill.source) {
          SkillSource.BUILT_IN -> R.string.msg_ai_skill_badge_builtin
          SkillSource.INSTALLED -> R.string.msg_ai_skill_badge_installed
          SkillSource.PROJECT -> R.string.msg_ai_skill_badge_project
        }
      )

      val origin = skill.origin.takeIf { it.isNotBlank() && it != "bundled" }.orEmpty()
      binding.skillOrigin.text = origin
      binding.skillOrigin.visibility = if (origin.isEmpty()) View.GONE else View.VISIBLE

      binding.skillDelete.visibility =
        if (skill.source.canDelete) View.VISIBLE else View.INVISIBLE
      binding.skillDelete.setOnClickListener { onDelete(skill) }
      binding.root.setOnClickListener { onSelect(skill) }
    }
  }

  companion object {

    private val DIFF = object : DiffUtil.ItemCallback<Skill>() {

      override fun areItemsTheSame(oldItem: Skill, newItem: Skill): Boolean =
        oldItem.name == newItem.name && oldItem.source == newItem.source

      override fun areContentsTheSame(oldItem: Skill, newItem: Skill): Boolean = oldItem == newItem
    }
  }
}
