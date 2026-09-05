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

package com.itsaky.androidide.projects.classpath

import com.google.common.collect.ImmutableSet
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.pathString

/** @author Akash Yadav */
class JarFsClasspathReader : IClasspathReader {

  override fun listClasses(files: Collection<File>): ImmutableSet<ClassInfo> {
    val builder = ImmutableSet.builder<ClassInfo>()
    for (path in files.map(File::toPath)) {
      if (!Files.exists(path)) {
        continue
      }

      FileSystems.newFileSystem(URI("jar:" + path.toUri()), emptyMap<String, Any>()).use { fs ->
        Files.walkFileTree(
          fs.getPath("/"),
          emptySet(),
          Int.MAX_VALUE,
          object : SimpleFileVisitor<Path>() {

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
              var name = file.pathString
              if (name.endsWith("/package-info.class") || !name.endsWith(".class")) {
                return CONTINUE
              }

              name = name.substringBeforeLast(".class")

              if (name.isBlank()) {
                return CONTINUE
              }

              if (name.startsWith('/')) {
                name = name.substring(1)
              }

              if (name.contains('/')) {
                name = name.replace('/', '.')
              }

              ClassInfo.create(name)?.also {
                builder.add(it)
              }

              return super.visitFile(file, attrs)
            }
          }
        )
      }
    }
    return builder.build()
  }
}
