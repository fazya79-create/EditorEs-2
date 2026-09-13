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
import com.itsaky.androidide.ai.model.ChatRole
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.model.StopReason
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.ai.tools.DispatchSubagentTool
import com.itsaky.androidide.ai.tools.TodoStore
import com.itsaky.androidide.ai.tools.ToolGate
import com.itsaky.androidide.ai.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubagentTurnTest {

  private class ScriptedProvider(private val replies: List<ChatStreamEvent.Completed>) :
    LlmProvider {

    val requests = mutableListOf<ChatRequest>()

    override val kind: ProviderKind = ProviderKind.OPENAI

    override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = flow {
      requests += request
      val index = (requests.size - 1).coerceAtMost(replies.size - 1)
      emit(replies[index])
    }
  }

  private fun completed(
    text: String,
    calls: List<ToolCall> = emptyList()
  ) = ChatStreamEvent.Completed(
    message = ChatMessage.assistant(text, calls),
    stopReason = if (calls.isEmpty()) StopReason.END_TURN else StopReason.TOOL_USE
  )

  @Test
  fun `the parent keeps talking after a sub-agent returns`() {
    val dispatchCall = ToolCall(
      id = "call_1",
      name = DispatchSubagentTool.NAME,
      argumentsJson = """{"description":"probe","prompt":"look around","tool_scope":"read_only"}"""
    )

    val provider = ScriptedProvider(
      listOf(
        completed("", listOf(dispatchCall)),
        completed("Here is what the sub-agent found.")
      )
    )

    val registry = ToolRegistry(TodoStore()) {
      SubagentOutcome(text = "the sub-agent report", toolCalls = 1)
    }
    val gate = ToolGate(
      ApplicationProvider.getApplicationContext(),
      registry
    ) { ApprovalDecision.APPROVED }

    val agent = ChatAgent(
      provider = provider,
      gate = gate,
      model = "test-model",
      systemPrompt = "be helpful",
      tools = registry.specs()
    )

    val events = runBlocking { agent.run(listOf(ChatMessage.user("go"))).toList() }

    val finished = events.filterIsInstance<AgentEvent.ToolFinished>().single()
    assertThat(finished.result.isError).isFalse()
    assertThat(finished.result.content).isEqualTo("the sub-agent report")

    val texts = events.filterIsInstance<AgentEvent.AssistantMessage>().map { it.message.text }
    assertThat(texts).contains("Here is what the sub-agent found.")

    val followUp = provider.requests.last()
    assertThat(followUp.messages.any { it.role == ChatRole.TOOL }).isTrue()
    assertThat(
      followUp.messages
        .filter { it.role == ChatRole.TOOL }
        .flatMap { it.toolResults }
        .map { it.content }
    ).contains("the sub-agent report")
  }

  @Test
  fun `a failing sub-agent still lets the parent recover`() {
    val dispatchCall = ToolCall(
      id = "call_1",
      name = DispatchSubagentTool.NAME,
      argumentsJson = """{"description":"probe","prompt":"look around"}"""
    )

    val provider = ScriptedProvider(
      listOf(
        completed("", listOf(dispatchCall)),
        completed("The sub-agent failed, so I will do it myself.")
      )
    )

    val registry = ToolRegistry(TodoStore()) {
      SubagentOutcome(text = "child exploded", toolCalls = 0, failed = true)
    }
    val gate = ToolGate(
      ApplicationProvider.getApplicationContext(),
      registry
    ) { ApprovalDecision.APPROVED }

    val agent = ChatAgent(
      provider = provider,
      gate = gate,
      model = "test-model",
      systemPrompt = "be helpful",
      tools = registry.specs()
    )

    val events = runBlocking { agent.run(listOf(ChatMessage.user("go"))).toList() }

    val finished = events.filterIsInstance<AgentEvent.ToolFinished>().single()
    assertThat(finished.result.isError).isTrue()

    val texts = events.filterIsInstance<AgentEvent.AssistantMessage>().map { it.message.text }
    assertThat(texts).contains("The sub-agent failed, so I will do it myself.")
  }
}
