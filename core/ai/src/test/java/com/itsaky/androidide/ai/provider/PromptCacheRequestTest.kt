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
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.model.ToolResult
import com.itsaky.androidide.ai.model.ToolSpec
import org.junit.Test

class PromptCacheRequestTest {

  private val longPrompt = "be helpful. ".repeat(PromptCache.MIN_CACHEABLE_CHARS / 8)

  private val toolSpec = ToolSpec(
    name = "read_file",
    description = "Read a file",
    parametersSchemaJson = """{"type":"object","properties":{}}""",
    mutating = false
  )

  private val history = listOf(
    ChatMessage.user("read build.gradle"),
    ChatMessage.assistant(
      longPrompt,
      listOf(ToolCall("call_1", "read_file", """{"path":"build.gradle"}"""))
    ),
    ChatMessage.toolResults(listOf(ToolResult("call_1", "read_file", "plugins {}")))
  )

  private fun anthropic(caching: Boolean) = AnthropicProvider(
    ProviderConfig(ProviderKind.ANTHROPIC, "https://x/v1", "key", "claude-sonnet-4-5", caching)
  )

  private fun openAi(caching: Boolean, baseUrl: String = OpenAiProvider.DEFAULT_BASE_URL) =
    OpenAiProvider(ProviderConfig(ProviderKind.OPENAI, baseUrl, "key", "gpt-4o-mini", caching))

  private fun buildBody(provider: LlmProvider, request: ChatRequest): JsonObject {
    val method = provider.javaClass.getDeclaredMethod("buildBody", ChatRequest::class.java)
    method.isAccessible = true
    return method.invoke(provider, request) as JsonObject
  }

  private fun request(prompt: String) = ChatRequest(
    model = "test-model",
    messages = history,
    tools = listOf(toolSpec),
    systemPrompt = prompt
  )

  @Test
  fun `anthropic marks the system prompt when the prefix is large enough`() {
    val body = buildBody(anthropic(true), request(longPrompt))

    val system = body.getAsJsonArray("system")
    assertThat(system[0].asJsonObject.getAsJsonObject("cache_control").get("type").asString)
      .isEqualTo("ephemeral")

    assertThat(body.getAsJsonArray("tools").last().asJsonObject.has("cache_control")).isFalse()
  }

  @Test
  fun `anthropic marks the last tool only when the tool definitions are large`() {
    val large = toolSpec.copy(description = "d".repeat(PromptCache.MIN_CACHEABLE_CHARS))
    val body = buildBody(
      anthropic(true),
      request(longPrompt).copy(tools = listOf(toolSpec, large))
    )

    val tools = body.getAsJsonArray("tools")
    assertThat(tools[0].asJsonObject.has("cache_control")).isFalse()
    assertThat(tools[1].asJsonObject.getAsJsonObject("cache_control").get("type").asString)
      .isEqualTo("ephemeral")
  }

  @Test
  fun `anthropic breakpoint lands on the last settled assistant turn`() {
    val messages = buildBody(anthropic(true), request(longPrompt)).getAsJsonArray("messages")

    val assistant = messages[1].asJsonObject
    assertThat(assistant.get("role").asString).isEqualTo("assistant")
    val blocks = assistant.getAsJsonArray("content")
    assertThat(blocks.last().asJsonObject.has("cache_control")).isTrue()

    assertThat(messages[0].asJsonObject.toString()).doesNotContain("cache_control")
    assertThat(messages[2].asJsonObject.toString()).doesNotContain("cache_control")
  }

  @Test
  fun `anthropic falls back to a plain system string for small prompts`() {
    val body = buildBody(anthropic(true), request("be helpful"))

    assertThat(body.get("system").isJsonPrimitive).isTrue()
    assertThat(body.getAsJsonArray("tools").last().asJsonObject.has("cache_control")).isFalse()
  }

  @Test
  fun `caching disabled leaves both provider bodies untouched`() {
    val anthropicBody = buildBody(anthropic(false), request(longPrompt))
    assertThat(anthropicBody.toString()).doesNotContain("cache_control")
    assertThat(anthropicBody.get("system").isJsonPrimitive).isTrue()

    val openAiBody = buildBody(openAi(false), request(longPrompt))
    assertThat(openAiBody.has("prompt_cache_key")).isFalse()
  }

  @Test
  fun `openai sends a stable cache key only for a large stable prefix`() {
    val first = buildBody(openAi(true), request(longPrompt)).get("prompt_cache_key").asString
    val second = buildBody(openAi(true), request(longPrompt)).get("prompt_cache_key").asString

    assertThat(first).isEqualTo(second)
    assertThat(buildBody(openAi(true), request("be helpful")).has("prompt_cache_key")).isFalse()
  }

  @Test
  fun `openai never sends the cache key to a compatible gateway`() {
    val body = buildBody(openAi(true, "https://gateway.example.com/v1"), request(longPrompt))
    assertThat(body.has("prompt_cache_key")).isFalse()
  }
}
