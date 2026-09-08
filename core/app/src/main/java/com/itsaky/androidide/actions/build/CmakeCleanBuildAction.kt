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

package com.itsaky.androidide.actions.build

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.backend.build.BuildRequest
import com.itsaky.androidide.projects.internal.ProjectManagerImpl
import com.itsaky.androidide.resources.R

class CmakeCleanBuildAction(context: Context, order: Int) : CmakeBuildAction(context, order) {

  override val id: String = "ide.editor.build.cmake.clean"

  private val cleanIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_sweep_delete)

  init {
    label = context.getString(R.string.action_clean_build_cmake)
    icon = cleanIcon
  }

  override fun createRequest(preset: String): BuildRequest = BuildRequest.CleanBuild(preset)

  override fun prepare(data: ActionData) {
    super.prepare(data)
    val activity = data.getActivity() ?: return
    label = activity.getString(R.string.action_clean_build_cmake)
    icon = cleanIcon
    enabled = ProjectManagerImpl.getInstance().projectInitialized &&
      !activity.editorViewModel.isBuildInProgress
  }

  override fun getShowAsActionFlags(data: ActionData): Int = MenuItem.SHOW_AS_ACTION_NEVER
}
