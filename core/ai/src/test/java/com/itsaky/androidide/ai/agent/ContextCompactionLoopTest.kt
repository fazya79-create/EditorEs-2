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

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.model.StopReason
import com.itsaky.androidide.ai.model.TokenUsage
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.ai.tools.ToolGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextCompactionLoopTest {

  private class ScriptedProvider(
    private val turns: List<Turn>
  ) : LlmProvider {

    val requests = mutableListOf<ChatRequest>()
    private var index = 0

    override val kind: ProviderKind = ProviderKind.OPENAI

    override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = flow {
      requests += request
      val turn = turns.getOrElse(index) { turns.last() }
      index++

      emit(
        ChatStreamEvent.Completed(
          message = ChatMessage.assistant(turn.text, turn.toolCalls),
          stopReason = turn.stopReason,
          usage = turn.usage
        )
      )
    }
  }

  private data class Turn(
    val text: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val stopReason: StopReason = StopReason.END_TURN,
    val usage: TokenUsage = TokenUsage()
  )

  private fun gate() = ToolGate(ApplicationProvider.getApplicationContext()) {
    ApprovalDecision.APPROVED
  }

  private fun agent(
    provider: LlmProvider,
    contextWindow: Int,
    threshold: Int
  ) = ChatAgent(
    provider = provider,
    gate = gate(),
    model = "test-model",
    systemPrompt = "be helpful",
    contextWindow = contextWindow,
    compactThresholdPercent = threshold
  )

  private fun longHistory(): List<ChatMessage> =
    List(12) { ChatMessage.user("earlier message number $it") }

  private val toolCall = listOf(ToolCall("c1", "no_such_tool", "{}"))

  @Test
  fun `an over budget turn is compacted and the summary replaces the older half`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(toolCalls = toolCall, stopReason = StopReason.TOOL_USE, usage = TokenUsage(9_000, 0)),
        Turn(text = "SUMMARY OF EARLIER WORK"),
        Turn(text = "done", usage = TokenUsage(100, 10))
      )
    )

    val events = runBlocking {
      agent(provider, contextWindow = 10_000, threshold = 80).run(longHistory()).toList()
    }

    val compacted = events.filterIsInstance<AgentEvent.ContextCompacted>()
    assertThat(compacted).hasSize(1)
    assertThat(compacted.first().replaced).isGreaterThan(0)

    val history = compacted.first().history
    assertThat(ContextCompactor.isSummary(history.first())).isTrue()
    assertThat(history.first().text).contains("SUMMARY OF EARLIER WORK")
    assertThat(history.size).isLessThan(longHistory().size)
  }

  @Test
  fun `the request after compaction carries the summary instead of the old messages`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(toolCalls = toolCall, stopReason = StopReason.TOOL_USE, usage = TokenUsage(9_000, 0)),
        Turn(text = "SUMMARY OF EARLIER WORK"),
        Turn(text = "done", usage = TokenUsage(100, 10))
      )
    )

    runBlocking {
      agent(provider, contextWindow = 10_000, threshold = 80).run(longHistory()).toList()
    }

    val last = provider.requests.last()
    assertThat(last.messages.size).isLessThan(longHistory().size)
    assertThat(last.messages.any { ContextCompactor.isSummary(it) }).isTrue()
    assertThat(last.messages.none { it.text == "earlier message number 0" }).isTrue()
  }

  @Test
  fun `the summarisation request is sent without tools or a system prompt`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(toolCalls = toolCall, stopReason = StopReason.TOOL_USE, usage = TokenUsage(9_000, 0)),
        Turn(text = "SUMMARY OF EARLIER WORK"),
        Turn(text = "done")
      )
    )

    runBlocking {
      agent(provider, contextWindow = 10_000, threshold = 80).run(longHistory()).toList()
    }

    val summaryRequest = provider.requests[1]
    assertThat(summaryRequest.systemPrompt).isNull()
    assertThat(summaryRequest.tools).isEmpty()
    assertThat(summaryRequest.messages).hasSize(1)
    assertThat(summaryRequest.messages.first().text).contains("Summarise the conversation below")
  }

  @Test
  fun `a turn below the threshold is never compacted`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(toolCalls = toolCall, stopReason = StopReason.TOOL_USE, usage = TokenUsage(1_000, 0)),
        Turn(text = "done", usage = TokenUsage(1_100, 10))
      )
    )

    val events = runBlocking {
      agent(provider, contextWindow = 100_000, threshold = 80).run(longHistory()).toList()
    }

    assertThat(events.filterIsInstance<AgentEvent.ContextCompacted>()).isEmpty()
  }

  @Test
  fun `compaction is skipped when auto compact is disabled`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(toolCalls = toolCall, stopReason = StopReason.TOOL_USE, usage = TokenUsage(9_000, 0)),
        Turn(text = "done", usage = TokenUsage(9_500, 10))
      )
    )

    val events = runBlocking {
      agent(provider, contextWindow = 10_000, threshold = 0).run(longHistory()).toList()
    }

    assertThat(events.filterIsInstance<AgentEvent.ContextCompacted>()).isEmpty()
  }

  @Test
  fun `a failed summarisation leaves the history untouched`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(toolCalls = toolCall, stopReason = StopReason.TOOL_USE, usage = TokenUsage(9_000, 0)),
        Turn(text = ""),
        Turn(text = "done", usage = TokenUsage(100, 10))
      )
    )

    val events = runBlocking {
      agent(provider, contextWindow = 10_000, threshold = 80).run(longHistory()).toList()
    }

    assertThat(events.filterIsInstance<AgentEvent.ContextCompacted>()).isEmpty()

    val last = provider.requests.last()
    assertThat(last.messages.any { it.text == "earlier message number 0" }).isTrue()
  }

  @Test
  fun `an over budget turn that ends without tool calls is also compacted`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(text = "here is the answer", usage = TokenUsage(9_900, 50)),
        Turn(text = "SUMMARY OF EARLIER WORK")
      )
    )

    val events = runBlocking {
      agent(provider, contextWindow = 10_000, threshold = 80).run(longHistory()).toList()
    }

    val compacted = events.filterIsInstance<AgentEvent.ContextCompacted>()
    assertThat(compacted).hasSize(1)

    val history = compacted.first().history
    assertThat(ContextCompactor.isSummary(history.first())).isTrue()
    assertThat(history.none { it.text == "earlier message number 0" }).isTrue()
    assertThat(history.last().text).isEqualTo("here is the answer")
  }

  @Test
  fun `compaction is emitted before the turn completes`() {
    val provider = ScriptedProvider(
      listOf(
        Turn(text = "here is the answer", usage = TokenUsage(9_900, 50)),
        Turn(text = "SUMMARY OF EARLIER WORK")
      )
    )

    val events = runBlocking {
      agent(provider, contextWindow = 10_000, threshold = 80).run(longHistory()).toList()
    }

    val compactedAt = events.indexOfFirst { it is AgentEvent.ContextCompacted }
    val assistantAt = events.indexOfFirst { it is AgentEvent.AssistantMessage }
    val completedAt = events.indexOfFirst { it is AgentEvent.TurnCompleted }

    assertThat(assistantAt).isLessThan(compactedAt)
    assertThat(compactedAt).isLessThan(completedAt)
  }

  @Test
  fun `a turn that stays within budget is never compacted`() {
    val provider = ScriptedProvider(
      listOf(Turn(text = "short answer", usage = TokenUsage(100, 10)))
    )

    val events = runBlocking {
      agent(provider, contextWindow = 100_000, threshold = 80).run(longHistory()).toList()
    }

    assertThat(events.filterIsInstance<AgentEvent.ContextCompacted>()).isEmpty()
    assertThat(provider.requests).hasSize(1)
  }
}
