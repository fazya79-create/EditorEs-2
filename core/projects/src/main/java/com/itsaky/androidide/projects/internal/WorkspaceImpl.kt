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

package com.itsaky.androidide.projects.internal

import com.itsaky.androidide.projects.CppModule
import com.itsaky.androidide.projects.IWorkspace
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class WorkspaceImpl(
  private val projectDir: File,
  private val modules: List<CppModule>
) : IWorkspace {

  override fun getProjectDir(): File {
    return this.projectDir
  }

  override fun getModules(): List<CppModule> {
    return this.modules.toList()
  }

  override fun findModuleForFile(file: Path, checkExistance: Boolean): CppModule? {
    return findModuleForFile(file.toFile(), checkExistance)
  }

  override fun findModuleForFile(file: File, checkExistance: Boolean): CppModule? {
    if (!file.exists() && checkExistance) {
      return null
    }

    val path = runCatching { file.canonicalPath }.getOrNull() ?: file.absolutePath
    var longestPath = ""
    var moduleWithLongestPath: CppModule? = null

    for (module in modules) {
      val moduleDir = runCatching { module.dir.canonicalPath }.getOrNull()
        ?: module.dir.absolutePath
      if ((path == moduleDir || path.startsWith(moduleDir + File.separatorChar))
        && moduleDir.length > longestPath.length
      ) {
        longestPath = moduleDir
        moduleWithLongestPath = module
      }
    }

    return moduleWithLongestPath
  }

  override fun containsSourceFile(file: Path): Boolean {
    if (!Files.exists(file)) {
      return false
    }

    return findModuleForFile(file, false)?.containsFile(file.toFile()) == true
  }
}
