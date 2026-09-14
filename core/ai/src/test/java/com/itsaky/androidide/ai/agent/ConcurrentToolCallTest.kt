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
import com.itsaky.androidide.app.BaseApplication
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = BaseApplication::class)
class ConcurrentToolCallTest {

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

  private fun completed(text: String, calls: List<ToolCall> = emptyList()) =
    ChatStreamEvent.Completed(
      message = ChatMessage.assistant(text, calls),
      stopReason = if (calls.isEmpty()) StopReason.END_TURN else StopReason.TOOL_USE
    )

  private fun dispatch(id: String, description: String) = ToolCall(
    id = id,
    name = DispatchSubagentTool.NAME,
    argumentsJson =
      """{"description":"$description","prompt":"do $description","tool_scope":"read_only"}"""
  )

  private fun agentFor(
    provider: LlmProvider,
    runner: suspend (SubagentRequest) -> SubagentOutcome
  ): ChatAgent {
    val registry = ToolRegistry(TodoStore()) { request -> runner(request) }
    val gate = ToolGate(
      ApplicationProvider.getApplicationContext(),
      registry
    ) { ApprovalDecision.APPROVED }

    return ChatAgent(
      provider = provider,
      gate = gate,
      model = "test-model",
      systemPrompt = "be helpful",
      tools = registry.specs()
    )
  }

  @Test
  fun `two dispatches in one turn overlap instead of running one after the other`() {
    val provider = ScriptedProvider(
      listOf(
        completed("", listOf(dispatch("call_1", "first"), dispatch("call_2", "second"))),
        completed("both sub-agents reported")
      )
    )

    val active = AtomicInteger(0)
    val peak = AtomicInteger(0)

    val agent = agentFor(provider) { request ->
      val running = active.incrementAndGet()
      peak.updateAndGet { existing -> maxOf(existing, running) }
      delay(50)
      active.decrementAndGet()
      SubagentOutcome(text = "report for ${request.description}", toolCalls = 1)
    }

    runBlocking { agent.run(listOf(ChatMessage.user("go"))).toList() }

    assertThat(peak.get()).isEqualTo(2)
  }

  @Test
  fun `tool results keep the model's call order even when the later call finishes first`() {
    val provider = ScriptedProvider(
      listOf(
        completed("", listOf(dispatch("call_1", "slow"), dispatch("call_2", "fast"))),
        completed("done")
      )
    )

    val agent = agentFor(provider) { request ->
      if (request.description == "slow") {
        delay(80)
      }
      SubagentOutcome(text = "report for ${request.description}", toolCalls = 0)
    }

    runBlocking { agent.run(listOf(ChatMessage.user("go"))).toList() }

    val results = provider.requests.last()
      .messages
      .filter { it.role == ChatRole.TOOL }
      .flatMap { it.toolResults }

    assertThat(results.map { it.callId }).containsExactly("call_1", "call_2").inOrder()
    assertThat(results.map { it.content })
      .containsExactly("report for slow", "report for fast")
      .inOrder()
  }

  @Test
  fun `a mutating call is not overlapped with the dispatches around it`() {
    val write = ToolCall(
      id = "call_write",
      name = com.itsaky.androidide.ai.tools.WriteFileTool.NAME,
      argumentsJson = """{"path":"out.txt","content":"x"}"""
    )

    val provider = ScriptedProvider(
      listOf(
        completed("", listOf(dispatch("call_1", "first"), write, dispatch("call_2", "second"))),
        completed("done")
      )
    )

    val order = Collections.synchronizedList(mutableListOf<String>())
    val agent = agentFor(provider) { request ->
      order += "start ${request.description}"
      delay(30)
      order += "end ${request.description}"
      SubagentOutcome(text = "report", toolCalls = 0)
    }

    runBlocking { agent.run(listOf(ChatMessage.user("go"))).toList() }

    assertThat(order)
      .containsExactly("start first", "end first", "start second", "end second")
      .inOrder()
  }

  @Test
  fun `cancelling the turn stops every concurrent dispatch`() {
    val provider = ScriptedProvider(
      listOf(
        completed("", listOf(dispatch("call_1", "first"), dispatch("call_2", "second"))),
        completed("done")
      )
    )

    val started = AtomicInteger(0)
    val cancelled = AtomicInteger(0)
    val bothStarted = CompletableDeferred<Unit>()

    val agent = agentFor(provider) { _ ->
      if (started.incrementAndGet() == 2) {
        bothStarted.complete(Unit)
      }
      try {
        delay(10_000)
        SubagentOutcome(text = "never", toolCalls = 0)
      } catch (err: CancellationException) {
        cancelled.incrementAndGet()
        throw err
      }
    }

    runBlocking {
      val job = launch { agent.run(listOf(ChatMessage.user("go"))).toList() }
      bothStarted.await()
      job.cancelAndJoin()
    }

    assertThat(started.get()).isEqualTo(2)
    assertThat(cancelled.get()).isEqualTo(2)
  }
}
