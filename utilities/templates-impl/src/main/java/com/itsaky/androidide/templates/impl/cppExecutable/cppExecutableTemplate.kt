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

package com.itsaky.androidide.templates.impl.cppExecutable

import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl
import java.io.File

fun cppExecutableProject() = baseProjectImpl {
  templateName = R.string.template_cpp_executable
  thumb = R.drawable.template_no_activity
  recipe = createRecipe {
    val projectDir = data.projectDir
    val projectName = data.name.replace(Regex("[^A-Za-z0-9_]"), "_")
    save(cmakeListsSrc(projectName), File(projectDir, "CMakeLists.txt"))
    save(mainCppSrc(projectName), File(projectDir, "src/main.cpp"))
    save(readmeSrc(data.name), File(projectDir, "README.md"))
  }
}

private fun cmakeListsSrc(projectName: String): String {
  return """
    cmake_minimum_required(VERSION 3.22)
    project($projectName LANGUAGES C CXX)
    set(CMAKE_CXX_STANDARD 17)
    set(CMAKE_CXX_STANDARD_REQUIRED ON)
    add_executable(app src/main.cpp)
  """.trimIndent() + "\n"
}

private fun mainCppSrc(projectName: String): String {
  return """
    #include <iostream>

    int main() {
      std::cout << "Hello from $projectName!" << std::endl;
      return 0;
    }
  """.trimIndent() + "\n"
}

private fun readmeSrc(projectName: String): String {
  return "# $projectName\n\nNative C++ project. Press Build in the editor to compile with CMake.\n"
}
