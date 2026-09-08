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

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.databinding.LayoutProjectListItemBinding
import java.io.File

class ProjectListAdapter(
  private val projects: List<File>,
  private val onClick: (File) -> Unit
) : RecyclerView.Adapter<ProjectListAdapter.VH>() {

  class VH(val binding: LayoutProjectListItemBinding) : RecyclerView.ViewHolder(binding.root)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    VH(LayoutProjectListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

  override fun getItemCount(): Int = projects.size

  override fun onBindViewHolder(holder: VH, position: Int) {
    val project = projects[position]
    val binding = holder.binding
    binding.name.text = project.name
    binding.modified.text = DateUtils.getRelativeTimeSpanString(
      binding.root.context,
      project.lastModified()
    )
    binding.root.setOnClickListener { onClick(project) }
  }
}
