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

package com.itsaky.androidide.editor.language.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ShellLanguageTest {

  @Test
  fun `shell scripts are recognised by extension`() {
    assertThat(ShellLanguage.handles(File("build.sh"))).isTrue()
    assertThat(ShellLanguage.handles(File("setup.bash"))).isTrue()
    assertThat(ShellLanguage.handles(File("theme.zsh"))).isTrue()
  }

  @Test
  fun `well known dotfiles without an extension are recognised`() {
    assertThat(ShellLanguage.handles(File(".bashrc"))).isTrue()
    assertThat(ShellLanguage.handles(File(".zshrc"))).isTrue()
    assertThat(ShellLanguage.handles(File("gradlew"))).isTrue()
  }

  @Test
  fun `unrelated files are left to other languages`() {
    assertThat(ShellLanguage.handles(File("main.cpp"))).isFalse()
    assertThat(ShellLanguage.handles(File("CMakeLists.txt"))).isFalse()
    assertThat(ShellLanguage.handles(File("README.md"))).isFalse()
  }

  @Test
  fun `a block opener is detected so the next line is indented`() {
    assertThat(ShellLanguage.opensBlock("if [ -f x ]; then")).isTrue()
    assertThat(ShellLanguage.opensBlock("for f in *; do")).isTrue()
    assertThat(ShellLanguage.opensBlock("greet() {")).isTrue()
    assertThat(ShellLanguage.opensBlock("echo hello")).isFalse()
  }

  @Test
  fun `a trailing comment does not hide the block opener`() {
    assertThat(ShellLanguage.opensBlock("while true; do # loop")).isTrue()
  }

  @Test
  fun `a word merely ending in a keyword does not open a block`() {
    assertThat(ShellLanguage.opensBlock("echo tendo")).isFalse()
    assertThat(ShellLanguage.opensBlock("cd /var/lib/then")).isFalse()
  }
}
