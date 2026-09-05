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

package com.itsaky.androidide.projects

import androidx.annotation.RestrictTo
import com.itsaky.androidide.utils.ServiceLoader
import java.io.File

interface IProjectManager {

  companion object {

    private var projectManager: IProjectManager? = null

    @JvmStatic
    fun getInstance(): IProjectManager {
      return projectManager ?: ServiceLoader.load(IProjectManager::class.java).findFirstOrThrow()
        .also {
          projectManager = it
        }
    }
  }

  val projectDirPath: String
    get() = projectDir.path

  val projectDir: File

  val projectInitialized: Boolean

  fun openProject(directory: File)

  fun openProject(path: String) = openProject(File(path))

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
  suspend fun setupProject()

  fun getWorkspace(): IWorkspace?

  fun requireWorkspace(): IWorkspace = getWorkspace() ?: throw IWorkspace.NotConfiguredException()

  fun notifyFileCreated(file: File)

  fun notifyFileDeleted(file: File)

  fun notifyFileRenamed(from: File, to: File)

  fun destroy()
}
