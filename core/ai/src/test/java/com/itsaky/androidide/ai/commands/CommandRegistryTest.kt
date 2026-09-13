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

package com.itsaky.androidide.ai.commands

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CommandRegistryTest {

  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `the built-in commands are available without any project files`() {
    val names = CommandRegistry.load(null).all().map { it.name }

    assertThat(names).containsAtLeast("commit", "review", "explain", "test")
  }

  @Test
  fun `a project command file is loaded with its front matter description`() {
    val project = folder.newFolder()
    writeCommand(
      project,
      "deploy.md",
      """
        ---
        description: ship it
        ---

        Deploy the app for ${'$'}ARGUMENTS.
      """.trimIndent()
    )

    val command = CommandRegistry.load(project).find("deploy")

    assertThat(command).isNotNull()
    assertThat(command!!.description).isEqualTo("ship it")
    assertThat(command.template).isEqualTo("Deploy the app for \$ARGUMENTS.")
  }

  @Test
  fun `a project command overrides a built-in of the same name`() {
    val project = folder.newFolder()
    writeCommand(project, "commit.md", "Use the house commit format.")

    val command = CommandRegistry.load(project).find("commit")

    assertThat(command?.template).isEqualTo("Use the house commit format.")
    assertThat(command?.builtIn).isFalse()
  }

  @Test
  fun `resolving a known command expands its template`() {
    val project = folder.newFolder()
    writeCommand(project, "audit.md", "Audit \$ARGUMENTS now.")

    val resolution = CommandRegistry.load(project).resolve("/audit the parser")

    assertThat(resolution).isInstanceOf(CommandResolution.Expanded::class.java)
    assertThat((resolution as CommandResolution.Expanded).prompt)
      .isEqualTo("Audit the parser now.")
  }

  @Test
  fun `an unknown command reports the available names instead of being sent`() {
    val resolution = CommandRegistry.load(null).resolve("/nope")

    assertThat(resolution).isInstanceOf(CommandResolution.Unknown::class.java)
    assertThat((resolution as CommandResolution.Unknown).name).isEqualTo("nope")
    assertThat(resolution.available).contains("commit")
  }

  @Test
  fun `plain text is not resolved as a command`() {
    assertThat(CommandRegistry.load(null).resolve("explain the parser"))
      .isEqualTo(CommandResolution.NotACommand)
  }

  @Test
  fun `an empty command file is ignored`() {
    val project = folder.newFolder()
    writeCommand(project, "blank.md", "---\ndescription: nothing\n---\n\n   ")

    assertThat(CommandRegistry.load(project).find("blank")).isNull()
  }

  private fun writeCommand(project: File, name: String, text: String) {
    val dir = File(project, CommandRegistry.COMMANDS_DIR)
    dir.mkdirs()
    File(dir, name).writeText(text)
  }
}
