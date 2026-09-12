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

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatRole
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.model.StopReason
import com.itsaky.androidide.ai.model.ThinkingLevel
import com.itsaky.androidide.ai.model.TokenUsage
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.net.SseClient
import com.itsaky.androidide.ai.net.SseEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class GoogleProvider(private val config: ProviderConfig) : LlmProvider {

  override val kind: ProviderKind = ProviderKind.GOOGLE

  override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = callbackFlow {
    val text = StringBuilder()
    val reasoning = StringBuilder()
    val calls = mutableListOf<ToolCall>()
    var stopReason = StopReason.END_TURN
    var usage = TokenUsage()

    try {
      SseClient.post(
        url = streamUrl(request.model),
        headers = mapOf("x-goog-api-key" to config.apiKey),
        body = buildBody(request).toString()
      ).use { connection ->
        while (isActive) {
          val event = connection.next() ?: break
          if (event.data == DONE) {
            break
          }
          readUsage(event)?.let { usage = it }
          val reason = handleEvent(event, text, reasoning, calls)
          if (reason != null) {
            stopReason = reason
          }
        }
      }
    } catch (err: CancellationException) {
      throw err
    } catch (err: Throwable) {
      send(ChatStreamEvent.Failed(err.message ?: "Request failed", err))
      close()
      return@callbackFlow
    }

    if (calls.isNotEmpty() && stopReason == StopReason.END_TURN) {
      stopReason = StopReason.TOOL_USE
    }

    send(
      ChatStreamEvent.Completed(
        message = ChatMessage.assistant(text.toString(), calls.toList(), reasoning.toString()),
        stopReason = stopReason,
        usage = usage
      )
    )
    close()
  }.flowOn(Dispatchers.IO)

  private fun streamUrl(model: String): String {
    val base = config.baseUrl.trimEnd('/')
    return "$base/models/${model.removePrefix("models/")}:streamGenerateContent?alt=sse"
  }

  private fun readUsage(event: SseEvent): TokenUsage? {
    val chunk = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
      ?: return null
    val metadata = chunk.getAsJsonObject("usageMetadata") ?: return null

    val prompt = metadata.intOrZero("promptTokenCount")
    val candidates = metadata.intOrZero("candidatesTokenCount")
    val thoughts = metadata.intOrZero("thoughtsTokenCount")

    return TokenUsage(
      inputTokens = prompt,
      outputTokens = candidates + thoughts,
      cachedInputTokens = metadata.intOrZero("cachedContentTokenCount")
    )
  }

  private suspend fun ProducerScope<ChatStreamEvent>.handleEvent(
    event: SseEvent,
    text: StringBuilder,
    reasoning: StringBuilder,
    calls: MutableList<ToolCall>
  ): StopReason? {
    val chunk = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
      ?: return null

    chunk.getAsJsonObject("error")?.let { error ->
      val message = error.get("message")?.takeIf { it.isJsonPrimitive }?.asString
      send(ChatStreamEvent.Failed(message ?: "Provider returned an error"))
      return null
    }

    chunk.getAsJsonObject("promptFeedback")
      ?.get("blockReason")
      ?.takeIf { it.isJsonPrimitive }
      ?.asString
      ?.let { reason ->
        send(ChatStreamEvent.Failed("The prompt was blocked by the provider ($reason)."))
        return null
      }

    val candidate = chunk.getAsJsonArray("candidates")
      ?.firstOrNull()
      ?.takeIf { it.isJsonObject }
      ?.asJsonObject
      ?: return null

    val stopReason = candidate.get("finishReason")
      ?.takeIf { it.isJsonPrimitive }
      ?.asString
      ?.let { reason ->
        when (reason) {
          "MAX_TOKENS" -> StopReason.MAX_TOKENS
          else -> StopReason.END_TURN
        }
      }

    val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
      ?: return stopReason

    parts.forEach { element ->
      if (!element.isJsonObject) {
        return@forEach
      }

      val part = element.asJsonObject
      val call = part.getAsJsonObject("functionCall")

      if (call != null) {
        val index = calls.size
        val id = call.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
          .ifEmpty { "call_$index" }
        val name = call.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val arguments = call.get("args")
          ?.takeIf { it.isJsonObject }
          ?.toString()
          ?: "{}"
        val signature = part.get("thoughtSignature")
          ?.takeIf { it.isJsonPrimitive }
          ?.asString
          .orEmpty()

        calls += ToolCall(
          id = id,
          name = name,
          argumentsJson = arguments,
          signature = signature
        )
        send(ChatStreamEvent.ToolCallStarted(index, id, name))
        send(ChatStreamEvent.ToolCallArgumentsDelta(index, arguments))
        return@forEach
      }

      val content = part.get("text")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
      if (content.isEmpty()) {
        return@forEach
      }

      if (part.get("thought")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
        reasoning.append(content)
        send(ChatStreamEvent.ReasoningDelta(content))
      } else {
        text.append(content)
        send(ChatStreamEvent.TextDelta(content))
      }
    }

    return stopReason
  }

  private fun buildBody(request: ChatRequest): JsonObject {
    val body = JsonObject()

    request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
      val parts = JsonArray()
      parts.add(textPart(prompt))

      val instruction = JsonObject()
      instruction.add("parts", parts)
      body.add("systemInstruction", instruction)
    }

    val contents = JsonArray()
    request.messages.forEach { message -> appendMessage(contents, message) }
    body.add("contents", contents)

    val generationConfig = JsonObject()
    generationConfig.addProperty("maxOutputTokens", request.maxTokens)
    thinkingConfig(request)?.let { generationConfig.add("thinkingConfig", it) }
    body.add("generationConfig", generationConfig)

    if (request.tools.isNotEmpty()) {
      val declarations = JsonArray()
      request.tools.forEach { spec ->
        val declaration = JsonObject()
        declaration.addProperty("name", spec.name)
        declaration.addProperty("description", spec.description)
        sanitizeSchema(JsonParser.parseString(spec.parametersSchemaJson))
          ?.let { declaration.add("parameters", it) }
        declarations.add(declaration)
      }

      val tool = JsonObject()
      tool.add("functionDeclarations", declarations)

      val tools = JsonArray()
      tools.add(tool)
      body.add("tools", tools)
    }

    return body
  }

  private fun thinkingConfig(request: ChatRequest): JsonObject? {
    if (!request.thinkingLevel.isEnabled) {
      return null
    }

    val config = JsonObject()
    config.addProperty("includeThoughts", true)

    if (supportsThinkingLevel(request.model)) {
      config.addProperty("thinkingLevel", request.thinkingLevel.name)
    } else {
      config.addProperty("thinkingBudget", budgetOf(request.thinkingLevel))
    }

    return config
  }

  private fun supportsThinkingLevel(model: String): Boolean {
    val id = model.substringAfterLast('/').lowercase()
    val major = MODEL_VERSION.find(id)?.groupValues?.getOrNull(1)?.toIntOrNull()
      ?: return LATEST_ALIAS.containsMatchIn(id)
    return major >= THINKING_LEVEL_MIN_VERSION
  }

  private fun budgetOf(level: ThinkingLevel): Int = when (level) {
    ThinkingLevel.HIGH -> HIGH_THINKING_BUDGET
    ThinkingLevel.MEDIUM -> MEDIUM_THINKING_BUDGET
    else -> LOW_THINKING_BUDGET
  }

  private fun appendMessage(contents: JsonArray, message: ChatMessage) {
    when (message.role) {
      ChatRole.SYSTEM -> Unit

      ChatRole.USER -> {
        if (message.text.isEmpty()) {
          return
        }
        val parts = JsonArray()
        parts.add(textPart(message.text))
        contents.add(content(ROLE_USER, parts))
      }

      ChatRole.TOOL -> {
        if (message.toolResults.isEmpty()) {
          return
        }
        val parts = JsonArray()
        message.toolResults.forEach { result ->
          val payload = JsonObject()
          payload.addProperty(if (result.isError) "error" else "output", result.content)

          val response = JsonObject()
          response.addProperty("id", result.callId)
          response.addProperty("name", result.name)
          response.add("response", payload)

          val part = JsonObject()
          part.add("functionResponse", response)
          parts.add(part)
        }
        contents.add(content(ROLE_USER, parts))
      }

      ChatRole.ASSISTANT -> {
        val parts = JsonArray()
        if (message.text.isNotEmpty()) {
          parts.add(textPart(message.text))
        }
        message.toolCalls.forEach { toolCall ->
          val call = JsonObject()
          call.addProperty("id", toolCall.id)
          call.addProperty("name", toolCall.name)
          call.add(
            "args",
            runCatching { JsonParser.parseString(toolCall.argumentsJson) }
              .getOrNull()
              ?.takeIf { it.isJsonObject }
              ?: JsonObject()
          )

          val part = JsonObject()
          part.add("functionCall", call)
          if (toolCall.signature.isNotEmpty()) {
            part.addProperty("thoughtSignature", toolCall.signature)
          }
          parts.add(part)
        }

        if (parts.isEmpty) {
          return
        }
        contents.add(content(ROLE_MODEL, parts))
      }
    }
  }

  private fun content(role: String, parts: JsonArray): JsonObject {
    val content = JsonObject()
    content.addProperty("role", role)
    content.add("parts", parts)
    return content
  }

  private fun textPart(text: String): JsonObject {
    val part = JsonObject()
    part.addProperty("text", text)
    return part
  }

  private fun sanitizeSchema(element: JsonElement): JsonElement? {
    if (element.isJsonArray) {
      val array = JsonArray()
      element.asJsonArray.forEach { child -> sanitizeSchema(child)?.let { array.add(it) } }
      return array
    }

    if (!element.isJsonObject) {
      return element
    }

    val source = element.asJsonObject
    val sanitized = JsonObject()

    source.entrySet().forEach { (key, value) ->
      when (key) {
        "properties" -> {
          val properties = JsonObject()
          value.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (name, schema) ->
            sanitizeSchema(schema)?.let { properties.add(name, it) }
          }
          sanitized.add("properties", properties)
        }

        in SCHEMA_KEYWORDS -> sanitizeSchema(value)?.let { sanitized.add(key, it) }

        else -> Unit
      }
    }

    return sanitized
  }

  private fun JsonObject.intOrZero(name: String): Int =
    get(name)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0

  companion object {

    const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    const val DEFAULT_MODEL = "gemini-3.7-flash"

    private const val DONE = "[DONE]"

    private const val ROLE_USER = "user"
    private const val ROLE_MODEL = "model"

    private const val THINKING_LEVEL_MIN_VERSION = 3
    private const val LOW_THINKING_BUDGET = 4096
    private const val MEDIUM_THINKING_BUDGET = 8192
    private const val HIGH_THINKING_BUDGET = 16384

    private val MODEL_VERSION = Regex("gemini-(\\d+)")
    private val LATEST_ALIAS = Regex("^gemini-(flash|flash-lite|pro)-latest$")

    private val SCHEMA_KEYWORDS = setOf(
      "type",
      "description",
      "format",
      "nullable",
      "enum",
      "items",
      "required",
      "minItems",
      "maxItems"
    )
  }
}
