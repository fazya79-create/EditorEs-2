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

import com.itsaky.androidide.lookup.Lookup
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class CppModule(val dir: File, val name: String = dir.name) {

  companion object {
    val COMPLETION_MODULE_KEY = Lookup.Key<CppModule>()

    private val SOURCE_EXTENSIONS =
      setOf("c", "cc", "cpp", "cxx", "cu", "h", "hh", "hpp", "hxx")

    private const val MAX_SOURCE_FILES = 30000
  }

  fun getSourceDirectories(): Set<File> = setOf(dir)

  fun containsFile(file: File): Boolean {
    val root = runCatching { dir.canonicalPath }.getOrNull() ?: dir.absolutePath
    val path = runCatching { file.canonicalPath }.getOrNull() ?: file.absolutePath
    return path == root || path.startsWith(root + File.separatorChar)
  }

  fun findSourceRoot(file: File): File? {
    return if (containsFile(file)) dir else null
  }

  fun listSourceFiles(): List<File> {
    val result = mutableListOf<File>()
    runCatching {
      Files.walk(dir.toPath()).use { stream ->
        val iter = stream.iterator()
        while (iter.hasNext() && result.size < MAX_SOURCE_FILES) {
          val path = iter.next()
          if (path.isRegularFile() && path.extension.lowercase() in SOURCE_EXTENSIONS) {
            result.add(path.toFile())
          }
        }
      }
    }
    return result
  }
}
