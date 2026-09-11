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

import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.model.StopReason
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.model.ToolResult
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.tools.ToolGate
import com.itsaky.androidide.ai.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface AgentEvent {

  data class TextDelta(val text: String) : AgentEvent

  data class AssistantMessage(val message: ChatMessage) : AgentEvent

  data class ToolStarted(val call: ToolCall, val summary: String) : AgentEvent

  data class ToolFinished(val call: ToolCall, val result: ToolResult) : AgentEvent

  data class Failed(val message: String) : AgentEvent

  data object TurnCompleted : AgentEvent
}

class ChatAgent(
  private val provider: LlmProvider,
  private val gate: ToolGate,
  private val model: String,
  private val systemPrompt: String,
  private val maxToolRounds: Int = DEFAULT_MAX_TOOL_ROUNDS
) {

  fun run(history: List<ChatMessage>): Flow<AgentEvent> = flow {
    val messages = history.toMutableList()

    for (round in 0..maxToolRounds) {
      val request = ChatRequest(
        model = model,
        messages = messages.toList(),
        tools = ToolRegistry.specs(),
        systemPrompt = systemPrompt
      )

      var completed: ChatMessage? = null
      var stopReason = StopReason.END_TURN
      var failed = false

      provider.stream(request).collect { event ->
        when (event) {
          is ChatStreamEvent.TextDelta -> emit(AgentEvent.TextDelta(event.text))

          is ChatStreamEvent.Completed -> {
            completed = event.message
            stopReason = event.stopReason
          }

          is ChatStreamEvent.Failed -> {
            failed = true
            emit(AgentEvent.Failed(event.message))
          }

          is ChatStreamEvent.ToolCallStarted,
          is ChatStreamEvent.ToolCallArgumentsDelta -> Unit
        }
      }

      if (failed) {
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      val assistant = completed
      if (assistant == null) {
        emit(AgentEvent.Failed("The provider closed the stream without a response."))
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      if (!assistant.isBlank) {
        messages += assistant
        emit(AgentEvent.AssistantMessage(assistant))
      }

      if (stopReason != StopReason.TOOL_USE || assistant.toolCalls.isEmpty()) {
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      if (round == maxToolRounds) {
        emit(AgentEvent.Failed("Stopped after $maxToolRounds tool rounds."))
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      val results = mutableListOf<ToolResult>()
      for (call in assistant.toolCalls) {
        emit(AgentEvent.ToolStarted(call, gate.summarize(call)))
        val result = gate.run(call)
        results += result
        emit(AgentEvent.ToolFinished(call, result))
      }

      messages += ChatMessage.toolResults(results)
    }

    emit(AgentEvent.TurnCompleted)
  }

  companion object {

    const val DEFAULT_MAX_TOOL_ROUNDS = 12
  }
}
