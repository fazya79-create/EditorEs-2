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

package com.itsaky.androidide.ai.skills

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.agent.SystemPrompt
import com.itsaky.androidide.ai.tools.SkillTool
import com.itsaky.androidide.ai.tools.parseArguments
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkillToolLiveTest {

  private fun corpusSkill(): File? =
    System.getenv(CORPUS_ENV)
      .orEmpty()
      .split(File.pathSeparatorChar)
      .filter { it.isNotBlank() }
      .map { File(it) }
      .filter { it.isDirectory }
      .firstNotNullOfOrNull { root ->
        root.walkTopDown().firstOrNull { it.isFile && it.name == SkillRegistry.MANIFEST }
      }

  @Test
  fun `a real downloaded skill loads end to end through the tool`() {
    val manifest = corpusSkill()
    assumeTrue("Set $CORPUS_ENV to a skills checkout to run this", manifest != null)

    val loaded = SkillRegistry.parse(manifest!!)
    assertThat(loaded).isInstanceOf(SkillLoadResult.Loaded::class.java)
    val skill = (loaded as SkillLoadResult.Loaded).skill

    val registry = SkillRegistry(mapOf(skill.name to skill))
    val tool = SkillTool { registry }

    val rendered = runBlocking {
      tool.execute(
        ApplicationProvider.getApplicationContext(),
        parseArguments("""{"name":"${skill.name}"}""")
      )
    }

    assertThat(rendered).contains("# Skill: ${skill.name}")
    assertThat(rendered).contains(skill.body.lineSequence().first { it.isNotBlank() }.trim())
    assertThat(rendered).contains(skill.directory.absolutePath)

    val prompt = SystemPrompt.build(skills = listOf(skill))
    assertThat(prompt).contains(skill.name)
    assertThat(prompt).doesNotContain(skill.body)
  }

  @Test
  fun `asking for a skill that does not exist names the ones that do`() {
    val registry = SkillRegistry(
      mapOf(
        "pdf" to Skill("pdf", "d", "b", File("/tmp/pdf")),
        "docx" to Skill("docx", "d", "b", File("/tmp/docx"))
      )
    )
    val tool = SkillTool { registry }

    val error = runCatching {
      runBlocking {
        tool.execute(
          ApplicationProvider.getApplicationContext(),
          parseArguments("""{"name":"nope"}""")
        )
      }
    }.exceptionOrNull()

    assertThat(error).isNotNull()
    assertThat(error?.message).contains("pdf")
    assertThat(error?.message).contains("docx")
  }

  companion object {

    private const val CORPUS_ENV = "ANDROIDIDE_SKILL_CORPUS"
  }
}
