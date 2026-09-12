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
class InterruptedStreamTest {

  private class AbortingProvider(private val partial: String) : LlmProvider {

    val requests = mutableListOf<ChatRequest>()

    override val kind: ProviderKind = ProviderKind.OPENAI

    override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = flow {
      requests += request
      if (partial.isNotEmpty()) {
        emit(ChatStreamEvent.TextDelta(partial))
      }
      emit(
        ChatStreamEvent.Interrupted(
          message = "unexpected end of stream",
          partial = ChatMessage.assistant(partial)
        )
      )
    }
  }

  private fun agent(provider: LlmProvider) = ChatAgent(
    provider = provider,
    gate = ToolGate(ApplicationProvider.getApplicationContext()) { ApprovalDecision.APPROVED },
    model = "test-model",
    systemPrompt = "be helpful"
  )

  private val history = listOf(ChatMessage.user("explain the build"))

  @Test
  fun `an aborted stream keeps the partial reply and reports an interruption`() {
    val provider = AbortingProvider("Here is the first ha")

    val events = runBlocking { agent(provider).run(history).toList() }

    val assistant = events.filterIsInstance<AgentEvent.AssistantMessage>().single()
    assertThat(assistant.message.text).isEqualTo("Here is the first ha")

    val interrupted = events.filterIsInstance<AgentEvent.Interrupted>().single()
    assertThat(interrupted.message).isEqualTo("unexpected end of stream")
    assertThat(interrupted.partial).isEqualTo("Here is the first ha")
  }

  @Test
  fun `an interrupted turn still completes so the ui leaves the busy state`() {
    val provider = AbortingProvider("partial text")

    val events = runBlocking { agent(provider).run(history).toList() }

    assertThat(events.last()).isEqualTo(AgentEvent.TurnCompleted)
  }

  @Test
  fun `an interruption stops the tool loop instead of requesting again`() {
    val provider = AbortingProvider("partial text")

    runBlocking { agent(provider).run(history).toList() }

    assertThat(provider.requests).hasSize(1)
  }

  @Test
  fun `an abort before any text produces no empty assistant message`() {
    val provider = AbortingProvider("")

    val events = runBlocking { agent(provider).run(history).toList() }

    assertThat(events.filterIsInstance<AgentEvent.AssistantMessage>()).isEmpty()
    assertThat(events.filterIsInstance<AgentEvent.Interrupted>()).hasSize(1)
  }
}
