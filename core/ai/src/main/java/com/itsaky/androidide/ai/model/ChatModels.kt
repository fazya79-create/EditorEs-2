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

package com.itsaky.androidide.ai.model

enum class ChatRole {
  SYSTEM,
  USER,
  ASSISTANT,
  TOOL
}

data class ToolCall(
  val id: String,
  val name: String,
  val argumentsJson: String
)

data class ToolResult(
  val callId: String,
  val name: String,
  val content: String,
  val isError: Boolean = false
)

data class ChatMessage(
  val role: ChatRole,
  val text: String = "",
  val reasoning: String = "",
  val toolCalls: List<ToolCall> = emptyList(),
  val toolResults: List<ToolResult> = emptyList()
) {

  val isBlank: Boolean
    get() = text.isBlank() && toolCalls.isEmpty() && toolResults.isEmpty()

  companion object {

    @JvmStatic
    fun user(text: String) = ChatMessage(role = ChatRole.USER, text = text)

    @JvmStatic
    fun system(text: String) = ChatMessage(role = ChatRole.SYSTEM, text = text)

    @JvmStatic
    fun assistant(
      text: String,
      toolCalls: List<ToolCall> = emptyList(),
      reasoning: String = ""
    ) = ChatMessage(
      role = ChatRole.ASSISTANT,
      text = text,
      reasoning = reasoning,
      toolCalls = toolCalls
    )

    @JvmStatic
    fun toolResults(results: List<ToolResult>) =
      ChatMessage(role = ChatRole.TOOL, toolResults = results)
  }
}

data class ToolSpec(
  val name: String,
  val description: String,
  val parametersSchemaJson: String,
  val mutating: Boolean
)

enum class StopReason {
  END_TURN,
  TOOL_USE,
  MAX_TOKENS,
  ABORTED
}

data class TokenUsage(
  val inputTokens: Int = 0,
  val outputTokens: Int = 0
) {

  val total: Int
    get() = inputTokens + outputTokens

  val isEmpty: Boolean
    get() = inputTokens == 0 && outputTokens == 0
}

sealed interface ChatStreamEvent {

  data class TextDelta(val text: String) : ChatStreamEvent

  data class ReasoningDelta(val text: String) : ChatStreamEvent

  data class ToolCallStarted(val index: Int, val id: String, val name: String) : ChatStreamEvent

  data class ToolCallArgumentsDelta(val index: Int, val json: String) : ChatStreamEvent

  data class Completed(
    val message: ChatMessage,
    val stopReason: StopReason,
    val usage: TokenUsage = TokenUsage()
  ) : ChatStreamEvent

  data class Failed(val message: String, val cause: Throwable? = null) : ChatStreamEvent
}

enum class ThinkingLevel {
  OFF,
  LOW,
  MEDIUM,
  HIGH;

  val isEnabled: Boolean
    get() = this != OFF

  val wireValue: String
    get() = name.lowercase()
}

data class ChatRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val tools: List<ToolSpec> = emptyList(),
  val systemPrompt: String? = null,
  val maxTokens: Int = 4096,
  val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF
)
