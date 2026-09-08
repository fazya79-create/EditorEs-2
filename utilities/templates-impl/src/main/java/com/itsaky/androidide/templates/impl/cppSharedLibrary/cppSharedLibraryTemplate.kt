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

package com.itsaky.androidide.templates.impl.cppSharedLibrary

import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl
import java.io.File

fun cppSharedLibraryProject() = baseProjectImpl {
  templateName = R.string.template_cpp_shared_library
  thumb = R.drawable.template_no_activity
  recipe = createRecipe {
    val projectDir = data.projectDir
    val projectName = data.name.replace(Regex("[^A-Za-z0-9_]"), "_")
    save(cmakeListsSrc(projectName), File(projectDir, "CMakeLists.txt"))
    save(libraryHeaderSrc(projectName), File(projectDir, "include/$projectName.h"))
    save(libraryCppSrc(projectName), File(projectDir, "src/$projectName.cpp"))
    save(readmeSrc(data.name, projectName), File(projectDir, "README.md"))
  }
}

private fun cmakeListsSrc(projectName: String): String {
  return """
    cmake_minimum_required(VERSION 3.22)
    project($projectName LANGUAGES C CXX)
    set(CMAKE_CXX_STANDARD 17)
    set(CMAKE_CXX_STANDARD_REQUIRED ON)
    add_library($projectName SHARED src/$projectName.cpp)
    target_include_directories($projectName PUBLIC ${'$'}{CMAKE_CURRENT_SOURCE_DIR}/include)
  """.trimIndent() + "\n"
}

private fun libraryHeaderSrc(projectName: String): String {
  val guard = "${projectName.uppercase()}_H"
  return """
    #ifndef $guard
    #define $guard

    #ifdef __cplusplus
    extern "C" {
    #endif

    __attribute__((visibility("default")))
    const char* ${projectName}_greet(void);

    #ifdef __cplusplus
    }
    #endif

    #endif
  """.trimIndent() + "\n"
}

private fun libraryCppSrc(projectName: String): String {
  return """
    #include "$projectName.h"

    const char* ${projectName}_greet(void) {
      return "Hello from $projectName!";
    }
  """.trimIndent() + "\n"
}

private fun readmeSrc(projectName: String, targetName: String): String {
  return "# $projectName\n\nNative C++ shared library. Press Build in the editor to compile lib$targetName.so with CMake.\n"
}
