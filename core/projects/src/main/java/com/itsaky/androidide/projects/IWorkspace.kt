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

import java.io.File
import java.nio.file.Path

interface IWorkspace {

  fun getProjectDir(): File

  fun getModules(): List<CppModule>

  fun findModuleForFile(file: Path, checkExistance: Boolean = false): CppModule?

  fun findModuleForFile(file: File, checkExistance: Boolean = false): CppModule?

  fun containsSourceFile(file: Path): Boolean

  class NotConfiguredException() : RuntimeException("Workspace not configured")
}
