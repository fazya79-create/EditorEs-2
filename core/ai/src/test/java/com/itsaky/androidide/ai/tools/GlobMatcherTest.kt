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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlobMatcherTest {

  private fun matches(pattern: String, path: String) =
    GlobMatcher.toRegex(pattern).matches(path)

  @Test
  fun `a single star stays inside one path segment`() {
    assertThat(matches("*.cpp", "main.cpp")).isTrue()
    assertThat(matches("*.cpp", "src/main.cpp")).isFalse()
    assertThat(matches("src/*.cpp", "src/main.cpp")).isTrue()
    assertThat(matches("src/*.cpp", "src/core/main.cpp")).isFalse()
  }

  @Test
  fun `a double star crosses path segments`() {
    assertThat(matches("**/*.cpp", "src/core/main.cpp")).isTrue()
    assertThat(matches("src/**/*.h", "src/a/b/c/thing.h")).isTrue()
    assertThat(matches("**/*.cpp", "notes.txt")).isFalse()
  }

  @Test
  fun `a leading double star also matches a file at the top level`() {
    assertThat(matches("**/*.cpp", "main.cpp")).isTrue()
    assertThat(matches("**/CMakeLists.txt", "CMakeLists.txt")).isTrue()
  }

  @Test
  fun `a question mark matches exactly one character`() {
    assertThat(matches("file?.c", "file1.c")).isTrue()
    assertThat(matches("file?.c", "file.c")).isFalse()
    assertThat(matches("file?.c", "file12.c")).isFalse()
  }

  @Test
  fun `regex metacharacters in a pattern are matched literally`() {
    assertThat(matches("a.b", "a.b")).isTrue()
    assertThat(matches("a.b", "axb")).isFalse()
    assertThat(matches("lib(1).so", "lib(1).so")).isTrue()
    assertThat(matches("cost+.txt", "cost+.txt")).isTrue()
  }

  @Test
  fun `a leading dot slash is ignored`() {
    assertThat(matches("./src/*.c", "src/main.c")).isTrue()
  }
}
