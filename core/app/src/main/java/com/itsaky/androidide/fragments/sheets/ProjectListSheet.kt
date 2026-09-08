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

package com.itsaky.androidide.fragments.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.itsaky.androidide.adapters.ProjectListAdapter
import com.itsaky.androidide.databinding.LayoutProjectListSheetBinding
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.Environment
import java.io.File

class ProjectListSheet : BaseBottomSheetFragment() {

  private var binding: LayoutProjectListSheetBinding? = null

  private val callback: Callback?
    get() = parentFragment as? Callback ?: activity as? Callback

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = LayoutProjectListSheetBinding.inflate(inflater, container, false)
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val binding = binding!!
    val root = Environment.PROJECTS_DIR
    val projects = root?.listFiles { file -> file.isDirectory && !file.isHidden }
      ?.sortedByDescending { it.lastModified() }
      ?: emptyList()

    binding.list.layoutManager = LinearLayoutManager(requireContext())
    binding.list.adapter = ProjectListAdapter(projects) { project ->
      dismiss()
      callback?.onProjectSelected(project)
    }

    if (projects.isEmpty()) {
      binding.empty.text = getString(R.string.msg_no_projects_found, root?.absolutePath ?: "")
      binding.empty.visibility = View.VISIBLE
      binding.list.visibility = View.GONE
    }

    binding.browse.icon.setImageDrawable(
      ContextCompat.getDrawable(requireContext(), R.drawable.ic_open_project)
    )
    binding.browse.text.setText(R.string.action_browse_project)
    binding.browse.root.setOnClickListener {
      dismiss()
      callback?.onBrowseProjects()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }

  interface Callback {
    fun onProjectSelected(project: File)
    fun onBrowseProjects()
  }
}
