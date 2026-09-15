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
import org.junit.Test

class SystemPromptTest {

  @Test
  fun `states that the project is CMake rather than Gradle`() {
    val prompt = SystemPrompt.build()

    assertThat(prompt).contains("CMake")
    assertThat(prompt).contains("It does not build Android applications")
    assertThat(prompt).contains("CMakeLists.txt")
  }

  @Test
  fun `names the Gradle and Android files the assistant must not hunt for`() {
    val prompt = SystemPrompt.build()

    listOf(
      "build.gradle",
      "settings.gradle",
      "AndroidManifest.xml",
      "gradlew",
      "src/main/java"
    ).forEach { path ->
      assertThat(prompt).contains(path)
    }
  }

  @Test
  fun `the CMake framing reaches sub-agents too`() {
    val prompt = SystemPrompt.buildForSubagent(
      scope = com.itsaky.androidide.ai.tools.ToolScope.READ_ONLY,
      toolNames = listOf("read_file")
    )

    assertThat(prompt).contains("CMake")
    assertThat(prompt).contains("It does not build Android applications")
  }

  @Test
  fun `includes defensive reverse engineering and anti cheat guidance`() {
    val prompt = SystemPrompt.build()

    assertThat(prompt).contains("authorized reverse-engineering or security work")
    assertThat(prompt).contains("anti-cheat, integrity, and abuse-prevention")
    assertThat(prompt).contains("Do not provide instructions or code to bypass, evade, disable")
  }

  @Test
  fun `an unavailable search tool is announced instead of offered`() {
    val prompt = SystemPrompt.build(SearchAvailability.UNAVAILABLE)

    assertThat(prompt).contains("no web search tool in this conversation")
    assertThat(prompt).contains("Never claim to have searched the web")
    assertThat(prompt).doesNotContain("Use web_search when you need information")
  }

  @Test
  fun `the task list must be updated one item at a time`() {
    val prompt = SystemPrompt.build()

    assertThat(prompt).contains("update it step by step while you work, never in one batch")
    assertThat(prompt).contains("mark it completed as soon as that single item is done")
  }

  @Test
  fun `a sub-agent without the todo tool is not told to keep a task list`() {
    val prompt = SystemPrompt.buildForSubagent(
      scope = com.itsaky.androidide.ai.tools.ToolScope.READ_ONLY,
      toolNames = listOf("read_file")
    )

    assertThat(prompt).doesNotContain("update it step by step while you work")
  }

}
