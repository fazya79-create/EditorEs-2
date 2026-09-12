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

package com.itsaky.androidide.ai.provider

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ThinkingLevel
import org.junit.Test

class ThinkingRequestTest {

  private fun buildBody(provider: LlmProvider, level: ThinkingLevel): JsonObject {
    val method = provider.javaClass.getDeclaredMethod("buildBody", ChatRequest::class.java)
    method.isAccessible = true
    return method.invoke(
      provider,
      ChatRequest(
        model = "test-model",
        messages = listOf(ChatMessage.user("hi")),
        thinkingLevel = level
      )
    ) as JsonObject
  }

  private fun openAi() =
    OpenAiProvider(ProviderConfig(ProviderKind.OPENAI, "https://x/v1", "key", "test-model"))

  private fun anthropic() =
    AnthropicProvider(ProviderConfig(ProviderKind.ANTHROPIC, "https://x/v1", "key", "test-model"))

  @Test
  fun `openai sends reasoning_effort only when thinking is enabled`() {
    assertThat(buildBody(openAi(), ThinkingLevel.OFF).has("reasoning_effort")).isFalse()

    val body = buildBody(openAi(), ThinkingLevel.HIGH)
    assertThat(body.get("reasoning_effort").asString).isEqualTo("high")
  }

  @Test
  fun `anthropic uses adaptive thinking with effort instead of budget_tokens`() {
    val off = buildBody(anthropic(), ThinkingLevel.OFF)
    assertThat(off.has("thinking")).isFalse()
    assertThat(off.has("output_config")).isFalse()

    val body = buildBody(anthropic(), ThinkingLevel.MEDIUM)
    assertThat(body.getAsJsonObject("thinking").get("type").asString).isEqualTo("adaptive")
    assertThat(body.getAsJsonObject("output_config").get("effort").asString).isEqualTo("medium")
    assertThat(body.getAsJsonObject("thinking").has("budget_tokens")).isFalse()
  }

  @Test
  fun `thinking wire values match the documented effort levels`() {
    assertThat(ThinkingLevel.LOW.wireValue).isEqualTo("low")
    assertThat(ThinkingLevel.MEDIUM.wireValue).isEqualTo("medium")
    assertThat(ThinkingLevel.HIGH.wireValue).isEqualTo("high")
    assertThat(ThinkingLevel.OFF.isEnabled).isFalse()
  }
}
