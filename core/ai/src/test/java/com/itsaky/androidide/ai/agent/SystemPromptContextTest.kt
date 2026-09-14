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

package com.itsaky.androidide.ai.agent

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.skills.Skill
import com.itsaky.androidide.ai.tools.SkillTool
import com.itsaky.androidide.ai.tools.ToolScope
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SystemPromptContextTest {

  @get:Rule
  val temp = TemporaryFolder()

  private fun skill(name: String, description: String) = Skill(
    name = name,
    description = description,
    body = "steps",
    directory = File("/tmp/$name")
  )

  @Test
  fun `a skill is advertised by name and summary without its body`() {
    val prompt = SystemPrompt.build(
      skills = listOf(skill("pdf", "Work with PDF files."))
    )

    assertThat(prompt).contains("Skills available in this project")
    assertThat(prompt).contains("pdf: Work with PDF files.")
    assertThat(prompt).contains(SkillTool.NAME)
    assertThat(prompt).doesNotContain("steps")
  }

  @Test
  fun `no skills section appears when the project has none`() {
    val prompt = SystemPrompt.build()

    assertThat(prompt).doesNotContain("Skills available in this project")
  }

  @Test
  fun `project instructions are included and framed as repository guidance`() {
    val prompt = SystemPrompt.build(instructions = "Always run clang-format.")

    assertThat(prompt).contains("project instructions")
    assertThat(prompt).contains("Always run clang-format.")
  }

  @Test
  fun `blank project instructions add no section`() {
    val prompt = SystemPrompt.build(instructions = "   ")

    assertThat(prompt).doesNotContain("project instructions")
  }

  @Test
  fun `the open file and selection are surfaced to the model`() {
    val prompt = SystemPrompt.build(
      editor = EditorContext(
        filePath = "src/main.cpp",
        selection = EditorSelection(10, 12, "int main() {}"),
        openFiles = listOf("src/main.cpp", "src/other.cpp")
      )
    )

    assertThat(prompt).contains("src/main.cpp")
    assertThat(prompt).contains("Selected lines 10-12")
    assertThat(prompt).contains("int main() {}")
    assertThat(prompt).contains("src/other.cpp")
  }

  @Test
  fun `a single selected line is not rendered as a range`() {
    val prompt = SystemPrompt.build(
      editor = EditorContext("a.cpp", EditorSelection(7, 7, "x"))
    )

    assertThat(prompt).contains("Selected lines 7:")
  }

  @Test
  fun `no editor section appears when nothing is open`() {
    val prompt = SystemPrompt.build()

    assertThat(prompt).doesNotContain("What the user is looking at right now")
  }

  @Test
  fun `a sub-agent without the skill tool is not told about skills`() {
    val prompt = SystemPrompt.buildForSubagent(
      scope = ToolScope.READ_ONLY,
      toolNames = listOf("read_file", "grep"),
      skills = listOf(skill("pdf", "Work with PDFs."))
    )

    assertThat(prompt).doesNotContain("Skills available in this project")
  }

  @Test
  fun `a sub-agent holding the skill tool is told about skills`() {
    val prompt = SystemPrompt.buildForSubagent(
      scope = ToolScope.READ_ONLY,
      toolNames = listOf("read_file", SkillTool.NAME),
      skills = listOf(skill("pdf", "Work with PDFs."))
    )

    assertThat(prompt).contains("Skills available in this project")
    assertThat(prompt).contains("pdf")
  }

  @Test
  fun `project instructions are found under each supported file name`() {
    ProjectInstructions.FILE_NAMES.forEach { name ->
      val root = temp.newFolder(name.replace('/', '_'))
      val target = root.resolve(name)
      target.parentFile?.mkdirs()
      target.writeText("instruction for $name")

      assertThat(ProjectInstructions.load(root)).isEqualTo("instruction for $name")
    }
  }

  @Test
  fun `a project with no instruction file yields an empty string`() {
    assertThat(ProjectInstructions.load(temp.newFolder("bare"))).isEmpty()
    assertThat(ProjectInstructions.load(null)).isEmpty()
  }
}
