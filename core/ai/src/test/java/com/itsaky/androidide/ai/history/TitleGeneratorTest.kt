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

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.model.StopReason
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.provider.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TitleGeneratorTest {

  private class ScriptedProvider(
    private val reply: String,
    private val fail: Boolean = false
  ) : LlmProvider {

    val requests = mutableListOf<ChatRequest>()

    override val kind: ProviderKind = ProviderKind.OPENAI

    override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = flow {
      requests += request
      if (fail) {
        emit(ChatStreamEvent.Failed("boom"))
        return@flow
      }
      emit(
        ChatStreamEvent.Completed(
          message = ChatMessage.assistant(reply),
          stopReason = StopReason.END_TURN
        )
      )
    }
  }

  private val conversation = listOf(
    ChatMessage.user("why does the terminal drawer ignore taps"),
    ChatMessage.assistant("the row has a focusable child")
  )

  @Test
  fun `the generated title is used verbatim`() {
    val provider = ScriptedProvider("Terminal drawer tap handling")

    val title = runBlocking {
      TitleGenerator.generate(provider, "test-model", conversation)
    }

    assertThat(title).isEqualTo("Terminal drawer tap handling")
  }

  @Test
  fun `the title request carries the conversation and no tools`() {
    val provider = ScriptedProvider("A title")

    runBlocking { TitleGenerator.generate(provider, "test-model", conversation) }

    val request = provider.requests.single()
    assertThat(request.tools).isEmpty()
    assertThat(request.systemPrompt).isNull()
    assertThat(request.messages).hasSize(1)
    assertThat(request.messages.first().text)
      .contains("why does the terminal drawer ignore taps")
  }

  @Test
  fun `a failed generation returns null so the fallback title stays`() {
    val provider = ScriptedProvider("ignored", fail = true)

    val title = runBlocking {
      TitleGenerator.generate(provider, "test-model", conversation)
    }

    assertThat(title).isNull()
  }

  @Test
  fun `an empty conversation is never sent to the provider`() {
    val provider = ScriptedProvider("A title")

    val title = runBlocking { TitleGenerator.generate(provider, "test-model", emptyList()) }

    assertThat(title).isNull()
    assertThat(provider.requests).isEmpty()
  }

  @Test
  fun `quotes markdown headings and trailing dots are stripped`() {
    assertThat(TitleGenerator.sanitize("\"Fix the build.\"")).isEqualTo("Fix the build")
    assertThat(TitleGenerator.sanitize("# Session title")).isEqualTo("Session title")
    assertThat(TitleGenerator.sanitize("  \n Real title \n more")).isEqualTo("Real title")
  }

  @Test
  fun `a blank reply produces no title`() {
    assertThat(TitleGenerator.sanitize("   \n  ")).isNull()
  }

  @Test
  fun `an over long title is truncated`() {
    val long = "w".repeat(200)

    assertThat(TitleGenerator.sanitize(long))
      .hasLength(ChatHistoryStore.MAX_TITLE_LENGTH)
  }
}
