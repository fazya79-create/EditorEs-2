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
class ContextCompactionEventTest {

  private class ScriptedProvider(
    private val reply: String,
    private val summaryFails: Boolean = false
  ) : LlmProvider {

    var calls = 0
      private set

    override val kind: ProviderKind = ProviderKind.OPENAI

    override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = flow {
      calls++
      val isSummaryRequest = request.systemPrompt == null
      if (isSummaryRequest && summaryFails) {
        emit(ChatStreamEvent.Failed("summary request failed"))
        return@flow
      }

      val text = if (isSummaryRequest) "compact summary notes" else reply
      emit(ChatStreamEvent.TextDelta(text))
      emit(
        ChatStreamEvent.Completed(
          message = ChatMessage.assistant(text),
          stopReason = StopReason.END_TURN,
          usage = TokenUsage(inputTokens = USED_TOKENS, outputTokens = 0)
        )
      )
    }
  }

  private fun agent(provider: LlmProvider) = ChatAgent(
    provider = provider,
    gate = ToolGate(ApplicationProvider.getApplicationContext()) { ApprovalDecision.APPROVED },
    model = "test-model",
    systemPrompt = "be helpful",
    contextWindow = CONTEXT_WINDOW,
    compactThresholdPercent = THRESHOLD_PERCENT
  )

  private fun longHistory(): List<ChatMessage> = buildList {
    repeat(HISTORY_TURNS) { index ->
      add(ChatMessage.user("question number $index"))
      add(ChatMessage.assistant("answer number $index"))
    }
  }

  @Test
  fun `the stale usage reading is retracted once the history is compacted`() {
    val provider = ScriptedProvider("the real answer")

    val events = runBlocking { agent(provider).run(longHistory()).toList() }

    val compacted = events.filterIsInstance<AgentEvent.ContextCompacted>().single()
    assertThat(compacted.replaced).isGreaterThan(0)

    val compactedIndex = events.indexOf(compacted)
    val usageAfterCompaction = events
      .drop(compactedIndex)
      .filterIsInstance<AgentEvent.UsageUpdated>()

    assertThat(usageAfterCompaction).isNotEmpty()
    assertThat(usageAfterCompaction.last().usage.isEmpty).isTrue()
  }

  @Test
  fun `a failed summarisation is reported instead of silently leaving the context full`() {
    val provider = ScriptedProvider("the real answer", summaryFails = true)

    val events = runBlocking { agent(provider).run(longHistory()).toList() }

    assertThat(events.filterIsInstance<AgentEvent.ContextCompacted>()).isEmpty()
    assertThat(events.filterIsInstance<AgentEvent.CompactionFailed>()).hasSize(1)
  }

  @Test
  fun `a failed summarisation does not abort the answer already produced`() {
    val provider = ScriptedProvider("the real answer", summaryFails = true)

    val events = runBlocking { agent(provider).run(longHistory()).toList() }

    val assistant = events.filterIsInstance<AgentEvent.AssistantMessage>().single()
    assertThat(assistant.message.text).isEqualTo("the real answer")
    assertThat(events.last()).isEqualTo(AgentEvent.TurnCompleted)
  }

  companion object {

    private const val CONTEXT_WINDOW = 1_000
    private const val THRESHOLD_PERCENT = 50
    private const val USED_TOKENS = 800
    private const val HISTORY_TURNS = 8
  }
}
