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

class ProviderRequestTest {

  private val toolSpec = ToolSpec(
    name = "read_file",
    description = "Read a file",
    parametersSchemaJson = """{"type":"object","properties":{}}""",
    mutating = false
  )

  private val history = listOf(
    ChatMessage.user("read build.gradle"),
    ChatMessage.assistant(
      "Let me look.",
      listOf(ToolCall("call_1", "read_file", """{"path":"build.gradle"}"""))
    ),
    ChatMessage.toolResults(listOf(ToolResult("call_1", "read_file", "plugins {}")))
  )

  private fun buildBody(provider: LlmProvider): JsonObject {
    val method = provider.javaClass.getDeclaredMethod("buildBody", ChatRequest::class.java)
    method.isAccessible = true
    return method.invoke(
      provider,
      ChatRequest(
        model = "test-model",
        messages = history,
        tools = listOf(toolSpec),
        systemPrompt = "be helpful"
      )
    ) as JsonObject
  }

  @Test
  fun `openai encodes tool calls and results in its own shape`() {
    val body = buildBody(
      OpenAiProvider(ProviderConfig(ProviderKind.OPENAI, "https://x/v1", "key", "test-model"))
    )

    val messages = body.getAsJsonArray("messages")
    assertThat(messages.size()).isEqualTo(4)
    assertThat(messages[0].asJsonObject.get("role").asString).isEqualTo("system")

    val assistant = messages[2].asJsonObject
    assertThat(assistant.get("role").asString).isEqualTo("assistant")
    val call = assistant.getAsJsonArray("tool_calls")[0].asJsonObject
    assertThat(call.get("type").asString).isEqualTo("function")
    assertThat(call.getAsJsonObject("function").get("name").asString).isEqualTo("read_file")

    val toolMessage = messages[3].asJsonObject
    assertThat(toolMessage.get("role").asString).isEqualTo("tool")
    assertThat(toolMessage.get("tool_call_id").asString).isEqualTo("call_1")

    val tool = body.getAsJsonArray("tools")[0].asJsonObject
    assertThat(tool.getAsJsonObject("function").has("parameters")).isTrue()
  }

  @Test
  fun `anthropic encodes system separately and tool results as user blocks`() {
    val body = buildBody(
      AnthropicProvider(
        ProviderConfig(ProviderKind.ANTHROPIC, "https://x/v1", "key", "test-model")
      )
    )

    assertThat(body.get("system").asString).isEqualTo("be helpful")

    val messages = body.getAsJsonArray("messages")
    assertThat(messages.size()).isEqualTo(3)

    val assistant = messages[1].asJsonObject
    assertThat(assistant.get("role").asString).isEqualTo("assistant")
    val blocks = assistant.getAsJsonArray("content")
    assertThat(blocks[0].asJsonObject.get("type").asString).isEqualTo("text")
    assertThat(blocks[1].asJsonObject.get("type").asString).isEqualTo("tool_use")
    assertThat(blocks[1].asJsonObject.getAsJsonObject("input").get("path").asString)
      .isEqualTo("build.gradle")

    val result = messages[2].asJsonObject
    assertThat(result.get("role").asString).isEqualTo("user")
    val resultBlock = result.getAsJsonArray("content")[0].asJsonObject
    assertThat(resultBlock.get("type").asString).isEqualTo("tool_result")
    assertThat(resultBlock.get("tool_use_id").asString).isEqualTo("call_1")

    val tool = body.getAsJsonArray("tools")[0].asJsonObject
    assertThat(tool.has("input_schema")).isTrue()
  }
}
