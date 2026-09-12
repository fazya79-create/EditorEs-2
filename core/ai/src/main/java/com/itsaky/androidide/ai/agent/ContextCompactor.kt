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
import com.itsaky.androidide.ai.model.ChatRole

object ContextCompactor {

  const val KEEP_RECENT_MESSAGES = 6

  private const val SUMMARY_PREFIX = "Summary of the earlier conversation:"

  fun shouldCompact(usedTokens: Int, contextWindow: Int, thresholdPercent: Int): Boolean {
    if (usedTokens <= 0 || contextWindow <= 0 || thresholdPercent <= 0) {
      return false
    }
    return usedTokens.toLong() * 100 >= contextWindow.toLong() * thresholdPercent
  }

  /**
   * Splits history into the part that must be summarised and the tail that is kept verbatim. The
   * split never separates an assistant message holding tool calls from the tool results that
   * answer it, because both providers reject a tool result without its originating call.
   */
  fun split(history: List<ChatMessage>): Pair<List<ChatMessage>, List<ChatMessage>> {
    if (history.size <= KEEP_RECENT_MESSAGES) {
      return emptyList<ChatMessage>() to history
    }

    var index = history.size - KEEP_RECENT_MESSAGES
    while (index < history.size && !isSafeBoundary(history, index)) {
      index++
    }

    if (index >= history.size) {
      return emptyList<ChatMessage>() to history
    }

    return history.subList(0, index).toList() to history.subList(index, history.size).toList()
  }

  fun summaryRequest(older: List<ChatMessage>): List<ChatMessage> = listOf(
    ChatMessage.user(
      buildString {
        append("Summarise the conversation below so it can replace the original messages ")
        append("without losing anything needed to continue the work.\n\n")
        append("Preserve: what the user asked for, decisions taken, files inspected or changed ")
        append("with their paths, commands run and their outcomes, and anything still pending. ")
        append("Write it as compact notes, not prose. Do not add commentary.\n\n")
        append("--- conversation ---\n")
        append(transcript(older))
      }
    )
  )

  fun asSummaryMessage(summary: String): ChatMessage =
    ChatMessage.user("$SUMMARY_PREFIX\n\n$summary")

  fun isSummary(message: ChatMessage): Boolean =
    message.role == ChatRole.USER && message.text.startsWith(SUMMARY_PREFIX)

  private fun isSafeBoundary(history: List<ChatMessage>, index: Int): Boolean {
    val message = history[index]
    if (message.role == ChatRole.TOOL) {
      return false
    }
    return message.role != ChatRole.ASSISTANT || message.toolCalls.isEmpty()
  }

  private fun transcript(messages: List<ChatMessage>): String = buildString {
    messages.forEach { message ->
      when (message.role) {
        ChatRole.USER -> appendLine("User: ${message.text}")
        ChatRole.SYSTEM -> appendLine("System: ${message.text}")

        ChatRole.ASSISTANT -> {
          if (message.text.isNotBlank()) {
            appendLine("Assistant: ${message.text}")
          }
          message.toolCalls.forEach { call ->
            appendLine("Assistant called ${call.name} with ${call.argumentsJson.take(TOOL_LIMIT)}")
          }
        }

        ChatRole.TOOL -> message.toolResults.forEach { result ->
          val status = if (result.isError) "failed" else "returned"
          appendLine("Tool ${result.name} $status: ${result.content.take(TOOL_LIMIT)}")
        }
      }
    }
  }

  private const val TOOL_LIMIT = 600
}
