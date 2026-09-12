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

package com.itsaky.androidide.ai.history

import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatRole
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.provider.LlmProvider

object TitleGenerator {

  const val MAX_TITLE_TOKENS = 64

  private const val MAX_MESSAGES = 6
  private const val EXCERPT_LIMIT = 400

  suspend fun generate(
    provider: LlmProvider,
    model: String,
    messages: List<ChatMessage>
  ): String? {
    val transcript = transcript(messages)
    if (transcript.isBlank()) {
      return null
    }

    val request = ChatRequest(
      model = model,
      messages = listOf(ChatMessage.user(prompt(transcript))),
      systemPrompt = null,
      maxTokens = MAX_TITLE_TOKENS
    )

    val text = StringBuilder()
    var failed = false

    provider.stream(request).collect { event ->
      when (event) {
        is ChatStreamEvent.Completed -> text.append(event.message.text)
        is ChatStreamEvent.Failed -> failed = true
        else -> Unit
      }
    }

    if (failed) {
      return null
    }

    return sanitize(text.toString())
  }

  fun sanitize(raw: String): String? {
    val line = raw.lineSequence()
      .map { it.trim() }
      .firstOrNull { it.isNotBlank() }
      ?: return null

    val cleaned = line
      .removeSurrounding("\"")
      .removeSurrounding("'")
      .removePrefix("#")
      .trim()
      .trimEnd('.')
      .trim()

    if (cleaned.isBlank()) {
      return null
    }

    return cleaned.take(ChatHistoryStore.MAX_TITLE_LENGTH)
  }

  private fun prompt(transcript: String): String = buildString {
    append("Write a short title for the conversation below, describing what it is about. ")
    append("Use at most 6 words, no quotes, no trailing punctuation, and no commentary. ")
    append("Reply with the title only.\n\n")
    append("--- conversation ---\n")
    append(transcript)
  }

  private fun transcript(messages: List<ChatMessage>): String = buildString {
    messages.asSequence()
      .filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
      .filter { it.text.isNotBlank() }
      .take(MAX_MESSAGES)
      .forEach { message ->
        val role = if (message.role == ChatRole.USER) "User" else "Assistant"
        appendLine("$role: ${message.text.take(EXCERPT_LIMIT)}")
      }
  }
}
