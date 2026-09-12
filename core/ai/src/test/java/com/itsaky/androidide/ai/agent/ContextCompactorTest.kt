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
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.model.ToolResult
import org.junit.Test

class ContextCompactorTest {

  @Test
  fun `does not compact below the threshold`() {
    assertThat(ContextCompactor.shouldCompact(79_000, 100_000, 80)).isFalse()
    assertThat(ContextCompactor.shouldCompact(80_000, 100_000, 80)).isTrue()
    assertThat(ContextCompactor.shouldCompact(900_000, 1_000_000, 80)).isTrue()
  }

  @Test
  fun `never compacts when disabled or when the window is unknown`() {
    assertThat(ContextCompactor.shouldCompact(90_000, 100_000, 0)).isFalse()
    assertThat(ContextCompactor.shouldCompact(90_000, 0, 80)).isFalse()
    assertThat(ContextCompactor.shouldCompact(0, 100_000, 80)).isFalse()
  }

  @Test
  fun `keeps short conversations intact`() {
    val history = List(4) { ChatMessage.user("m$it") }

    val (older, recent) = ContextCompactor.split(history)

    assertThat(older).isEmpty()
    assertThat(recent).hasSize(4)
  }

  @Test
  fun `split never separates a tool result from its call`() {
    val history = mutableListOf<ChatMessage>()
    repeat(6) { history += ChatMessage.user("older $it") }
    history += ChatMessage.assistant("calling", listOf(ToolCall("c1", "read_file", "{}")))
    history += ChatMessage.toolResults(listOf(ToolResult("c1", "read_file", "ok")))
    repeat(4) { history += ChatMessage.user("recent $it") }

    val (older, recent) = ContextCompactor.split(history)

    assertThat(older + recent).isEqualTo(history)
    assertThat(recent.first().role).isNotEqualTo(ChatRole.TOOL)

    recent.forEachIndexed { index, message ->
      if (message.role == ChatRole.TOOL) {
        val previous = recent[index - 1]
        assertThat(previous.toolCalls).isNotEmpty()
      }
    }
  }

  @Test
  fun `a summary is recognised and replaces the older half`() {
    val summary = ContextCompactor.asSummaryMessage("did some work")

    assertThat(ContextCompactor.isSummary(summary)).isTrue()
    assertThat(summary.role).isEqualTo(ChatRole.USER)
    assertThat(summary.text).contains("did some work")
    assertThat(ContextCompactor.isSummary(ChatMessage.user("hello"))).isFalse()
  }

  @Test
  fun `the summary request carries tool activity and file paths`() {
    val older = listOf(
      ChatMessage.user("add a button"),
      ChatMessage.assistant(
        "reading",
        listOf(ToolCall("c1", "read_file", """{"path":"app/main.kt"}"""))
      ),
      ChatMessage.toolResults(listOf(ToolResult("c1", "read_file", "fun main() {}")))
    )

    val request = ContextCompactor.summaryRequest(older)
    val text = request.single().text

    assertThat(text).contains("add a button")
    assertThat(text).contains("read_file")
    assertThat(text).contains("app/main.kt")
    assertThat(text).contains("fun main() {}")
  }

  @Test
  fun `tool output in the transcript is truncated so the summary stays small`() {
    val huge = "x".repeat(5_000)
    val older = listOf(
      ChatMessage.user("run it"),
      ChatMessage.assistant("running", listOf(ToolCall("c1", "run_shell", "{}"))),
      ChatMessage.toolResults(listOf(ToolResult("c1", "run_shell", huge)))
    )

    val text = ContextCompactor.summaryRequest(older).single().text

    assertThat(text.length).isLessThan(huge.length)
  }
}
