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
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.ai.tools.ReadFileTool
import com.itsaky.androidide.ai.tools.TodoStatus
import com.itsaky.androidide.ai.tools.TodoStore
import com.itsaky.androidide.ai.tools.TodoWriteTool
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
class TodoEventTest {

  private class ScriptedProvider(private val replies: List<ChatStreamEvent.Completed>) :
    LlmProvider {

    private var served = 0

    override val kind: ProviderKind = ProviderKind.OPENAI

    override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = flow {
      emit(replies[served.coerceAtMost(replies.size - 1)])
      served++
    }
  }

  private fun completed(text: String, calls: List<ToolCall> = emptyList()) =
    ChatStreamEvent.Completed(
      message = ChatMessage.assistant(text, calls),
      stopReason = if (calls.isEmpty()) StopReason.END_TURN else StopReason.TOOL_USE
    )

  private fun todoCall(id: String, status: String) = ToolCall(
    id = id,
    name = TodoWriteTool.NAME,
    argumentsJson = """{"todos":[{"id":"1","content":"step one","status":"$status"}]}"""
  )

  private fun read(id: String) = ToolCall(
    id = id,
    name = ReadFileTool.NAME,
    argumentsJson = """{"path":"missing.txt"}"""
  )

  private fun runTurn(replies: List<ChatStreamEvent.Completed>): List<AgentEvent> {
    val registry = ToolRegistry(TodoStore())
    val gate = ToolGate(
      ApplicationProvider.getApplicationContext(),
      registry
    ) { ApprovalDecision.APPROVED }

    val agent = ChatAgent(
      provider = ScriptedProvider(replies),
      gate = gate,
      model = "test-model",
      systemPrompt = "be helpful",
      tools = registry.specs()
    )

    return runBlocking { agent.run(listOf(ChatMessage.user("go"))).toList() }
  }

  @Test
  fun `every todo write reports the list it produced, one event per call`() {
    val events = runTurn(
      listOf(
        completed("", listOf(todoCall("call_1", "in_progress"))),
        completed("", listOf(todoCall("call_2", "completed"))),
        completed("done")
      )
    )

    val updates = events.filterIsInstance<AgentEvent.TodosUpdated>()

    assertThat(updates.map { it.items.single().status })
      .containsExactly(TodoStatus.IN_PROGRESS, TodoStatus.COMPLETED)
      .inOrder()
  }

  @Test
  fun `a todo write reports once even when the round holds other calls`() {
    val events = runTurn(
      listOf(
        completed("", listOf(read("call_read"), todoCall("call_todo", "in_progress"))),
        completed("done")
      )
    )

    val updates = events.filterIsInstance<AgentEvent.TodosUpdated>()

    assertThat(updates).hasSize(1)
    assertThat(updates.single().items.single().status).isEqualTo(TodoStatus.IN_PROGRESS)
  }

  @Test
  fun `a todo write is never folded into a concurrent batch`() {
    val registry = ToolRegistry(TodoStore())
    val gate = ToolGate(
      ApplicationProvider.getApplicationContext(),
      registry
    ) { ApprovalDecision.APPROVED }

    assertThat(gate.isParallelSafe(todoCall("call_todo", "in_progress"))).isFalse()
  }

  @Test
  fun `a rejected or failed todo write reports nothing`() {
    val registry = ToolRegistry(TodoStore())
    val gate = ToolGate(
      ApplicationProvider.getApplicationContext(),
      registry
    ) { ApprovalDecision.APPROVED }

    val agent = ChatAgent(
      provider = ScriptedProvider(
        listOf(
          completed(
            "",
            listOf(
              ToolCall(
                id = "call_bad",
                name = TodoWriteTool.NAME,
                argumentsJson = """{"todos":"not an array"}"""
              )
            )
          ),
          completed("done")
        )
      ),
      gate = gate,
      model = "test-model",
      systemPrompt = "be helpful",
      tools = registry.specs()
    )

    val events = runBlocking { agent.run(listOf(ChatMessage.user("go"))).toList() }

    assertThat(events.filterIsInstance<AgentEvent.TodosUpdated>()).isEmpty()
  }
}
