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
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.model.ToolResult
import com.itsaky.androidide.ai.model.ToolSpec
import org.junit.Test

class GoogleRequestTest {

  private val toolSpec = ToolSpec(
    name = "read_file",
    description = "Read a file",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "path": {"type": "string", "description": "path", "additionalProperties": false}
        },
        "required": ["path"]
      }
    """.trimIndent(),
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

  private fun provider(model: String = "gemini-2.5-flash") = GoogleProvider(
    ProviderConfig(ProviderKind.GOOGLE, GoogleProvider.DEFAULT_BASE_URL, "key", model)
  )

  private fun buildBody(
    provider: GoogleProvider,
    request: ChatRequest
  ): JsonObject {
    val method = provider.javaClass.getDeclaredMethod("buildBody", ChatRequest::class.java)
    method.isAccessible = true
    return method.invoke(provider, request) as JsonObject
  }

  private fun defaultRequest(model: String = "gemini-2.5-flash") = ChatRequest(
    model = model,
    messages = history,
    tools = listOf(toolSpec),
    systemPrompt = "be helpful"
  )

  @Test
  fun `google encodes the system prompt as systemInstruction parts`() {
    val body = buildBody(provider(), defaultRequest())

    val parts = body.getAsJsonObject("systemInstruction").getAsJsonArray("parts")
    assertThat(parts[0].asJsonObject.get("text").asString).isEqualTo("be helpful")
    assertThat(body.has("system")).isFalse()
  }

  @Test
  fun `google uses model role and functionCall or functionResponse parts`() {
    val body = buildBody(provider(), defaultRequest())
    val contents = body.getAsJsonArray("contents")

    assertThat(contents.size()).isEqualTo(3)
    assertThat(contents[0].asJsonObject.get("role").asString).isEqualTo("user")

    val assistant = contents[1].asJsonObject
    assertThat(assistant.get("role").asString).isEqualTo("model")
    val parts = assistant.getAsJsonArray("parts")
    assertThat(parts[0].asJsonObject.get("text").asString).isEqualTo("Let me look.")
    val call = parts[1].asJsonObject.getAsJsonObject("functionCall")
    assertThat(call.get("name").asString).isEqualTo("read_file")
    assertThat(call.getAsJsonObject("args").get("path").asString).isEqualTo("build.gradle")

    val toolTurn = contents[2].asJsonObject
    assertThat(toolTurn.get("role").asString).isEqualTo("user")
    val response = toolTurn.getAsJsonArray("parts")[0].asJsonObject
      .getAsJsonObject("functionResponse")
    assertThat(response.get("id").asString).isEqualTo("call_1")
    assertThat(response.get("name").asString).isEqualTo("read_file")
    assertThat(response.getAsJsonObject("response").get("output").asString).isEqualTo("plugins {}")
  }

  @Test
  fun `google declares tools under functionDeclarations and drops unsupported keywords`() {
    val body = buildBody(provider(), defaultRequest())

    val declaration = body.getAsJsonArray("tools")[0].asJsonObject
      .getAsJsonArray("functionDeclarations")[0].asJsonObject
    assertThat(declaration.get("name").asString).isEqualTo("read_file")

    val parameters = declaration.getAsJsonObject("parameters")
    assertThat(parameters.get("type").asString).isEqualTo("object")
    assertThat(parameters.getAsJsonArray("required")[0].asString).isEqualTo("path")

    val path = parameters.getAsJsonObject("properties").getAsJsonObject("path")
    assertThat(path.get("type").asString).isEqualTo("string")
    assertThat(path.has("additionalProperties")).isFalse()
  }

  @Test
  fun `google omits thinkingConfig when thinking is off`() {
    val body = buildBody(provider(), defaultRequest())
    assertThat(body.getAsJsonObject("generationConfig").has("thinkingConfig")).isFalse()
  }

  @Test
  fun `a tool call thought signature is replayed on the functionCall part`() {
    val signed = ChatMessage.assistant(
      "",
      listOf(ToolCall("call_1", "read_file", """{"path":"a.txt"}""", "SIGNATURE-BYTES"))
    )
    val body = buildBody(
      provider(),
      defaultRequest().copy(messages = listOf(ChatMessage.user("read it"), signed))
    )

    val part = body.getAsJsonArray("contents")[1].asJsonObject
      .getAsJsonArray("parts")[0].asJsonObject
    assertThat(part.getAsJsonObject("functionCall").get("name").asString).isEqualTo("read_file")
    assertThat(part.get("thoughtSignature").asString).isEqualTo("SIGNATURE-BYTES")
  }

  @Test
  fun `a tool call without a signature omits the field entirely`() {
    val body = buildBody(provider(), defaultRequest())

    val part = body.getAsJsonArray("contents")[1].asJsonObject
      .getAsJsonArray("parts")[1].asJsonObject
    assertThat(part.has("functionCall")).isTrue()
    assertThat(part.has("thoughtSignature")).isFalse()
  }

  @Test
  fun `rolling latest aliases are treated as thinking level models`() {
    val thinking = buildBody(
      provider("gemini-flash-latest"),
      defaultRequest("gemini-flash-latest").copy(thinkingLevel = ThinkingLevel.MEDIUM)
    ).getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")

    assertThat(thinking.get("thinkingLevel").asString).isEqualTo("MEDIUM")
    assertThat(thinking.has("thinkingBudget")).isFalse()
  }

  @Test
  fun `the default model is a thinking level model`() {
    val thinking = buildBody(
      provider(GoogleProvider.DEFAULT_MODEL),
      defaultRequest(GoogleProvider.DEFAULT_MODEL).copy(thinkingLevel = ThinkingLevel.MEDIUM)
    ).getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")

    assertThat(thinking.get("thinkingLevel").asString).isEqualTo("MEDIUM")
    assertThat(thinking.has("thinkingBudget")).isFalse()
  }

  @Test
  fun `gemini 3 uses thinkingLevel while earlier models use thinkingBudget`() {
    val modern = buildBody(
      provider("gemini-3-pro-preview"),
      defaultRequest("gemini-3-pro-preview").copy(thinkingLevel = ThinkingLevel.HIGH)
    ).getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")

    assertThat(modern.get("thinkingLevel").asString).isEqualTo("HIGH")
    assertThat(modern.has("thinkingBudget")).isFalse()
    assertThat(modern.get("includeThoughts").asBoolean).isTrue()

    val legacy = buildBody(
      provider(),
      defaultRequest().copy(thinkingLevel = ThinkingLevel.LOW)
    ).getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")

    assertThat(legacy.has("thinkingLevel")).isFalse()
    assertThat(legacy.get("thinkingBudget").asInt).isGreaterThan(0)
  }
}
