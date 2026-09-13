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
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRole
import com.itsaky.androidide.ai.model.ToolResult
import org.junit.Test

class ToolResultTrimmerTest {

  private fun toolMessage(content: String) =
    ChatMessage.toolResults(listOf(ToolResult("id", "read_file", content)))

  private fun big(marker: String) = marker + "x".repeat(60_000) + "END$marker"

  @Test
  fun `recent tool results are left untouched`() {
    val messages = listOf(
      ChatMessage.user("go"),
      toolMessage(big("a")),
      toolMessage(big("b")),
      toolMessage(big("c"))
    )

    val trimmed = ToolResultTrimmer.trim(messages)

    assertThat(trimmed).isEqualTo(messages)
  }

  @Test
  fun `older tool results are shortened once the window is exceeded`() {
    val messages = listOf(
      toolMessage(big("old")),
      toolMessage(big("a")),
      toolMessage(big("b")),
      toolMessage(big("c"))
    )

    val trimmed = ToolResultTrimmer.trim(messages)

    val oldest = trimmed.first().toolResults.single().content
    assertThat(oldest.length).isLessThan(big("old").length)
    assertThat(oldest).contains("characters trimmed")

    assertThat(trimmed.last().toolResults.single().content).isEqualTo(big("c"))
  }

  @Test
  fun `a shortened result keeps its head and its tail`() {
    val shortened = ToolResultTrimmer.shorten(big("m"))

    assertThat(shortened).startsWith("m")
    assertThat(shortened).endsWith("ENDm")
  }

  @Test
  fun `a small result is never altered`() {
    assertThat(ToolResultTrimmer.shorten("exit code: 0")).isEqualTo("exit code: 0")
  }

  @Test
  fun `non-tool messages are preserved exactly`() {
    val messages = listOf(
      ChatMessage.user("first"),
      toolMessage(big("old")),
      ChatMessage.assistant("thinking"),
      toolMessage(big("a")),
      toolMessage(big("b")),
      toolMessage(big("c"))
    )

    val trimmed = ToolResultTrimmer.trim(messages)

    assertThat(trimmed.filterNot { it.role == ChatRole.TOOL })
      .isEqualTo(messages.filterNot { it.role == ChatRole.TOOL })
  }
}
