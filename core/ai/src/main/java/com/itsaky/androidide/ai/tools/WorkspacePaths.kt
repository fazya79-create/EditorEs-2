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

package com.itsaky.androidide.ai.tools

import com.itsaky.androidide.projects.IProjectManager
import java.io.File

object WorkspacePaths {

  fun projectDir(): File = IProjectManager.getInstance().projectDir

  fun relativize(file: File): String {
    val root = projectDir().canonicalFile.path
    val path = file.canonicalFile.path
    return when {
      path == root -> "."
      path.startsWith("$root${File.separator}") -> path.substring(root.length + 1)
      else -> path
    }
  }

  fun resolve(path: String): File {
    if (path.isBlank()) {
      throw ToolException("The 'path' argument must not be empty.")
    }

    val root = projectDir().canonicalFile
    val candidate = File(path).let { if (it.isAbsolute) it else File(root, path) }
    val resolved = runCatching { candidate.canonicalFile }.getOrElse {
      throw ToolException("Unable to resolve path: $path")
    }

    val rootPath = root.path
    val resolvedPath = resolved.path
    if (resolvedPath != rootPath && !resolvedPath.startsWith("$rootPath${File.separator}")) {
      throw ToolException(
        "Access denied: '$path' resolves outside of the project directory ($rootPath)."
      )
    }

    return resolved
  }
}
