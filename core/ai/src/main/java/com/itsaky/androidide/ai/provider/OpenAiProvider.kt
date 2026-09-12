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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatRole
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.model.StopReason
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

class OpenAiProvider(private val config: ProviderConfig) : LlmProvider {

  override val kind: ProviderKind = ProviderKind.OPENAI

  override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = callbackFlow {
    val text = StringBuilder()
    val reasoning = StringBuilder()
    val calls = sortedMapOf<Int, PartialCall>()
    var stopReason = StopReason.END_TURN
    var usage = TokenUsage()

    try {
      SseClient.post(
        url = "${config.baseUrl.trimEnd('/')}/chat/completions",
        headers = mapOf("Authorization" to "Bearer ${config.apiKey}"),
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

    val toolCalls = calls.values.map { partial ->
      ToolCall(
        id = partial.id.ifEmpty { "call_${partial.index}" },
        name = partial.name,
        argumentsJson = partial.arguments.toString().ifBlank { "{}" }
      )
    }

    if (toolCalls.isNotEmpty() && stopReason == StopReason.END_TURN) {
      stopReason = StopReason.TOOL_USE
    }

    send(
      ChatStreamEvent.Completed(
        message = ChatMessage.assistant(text.toString(), toolCalls, reasoning.toString()),
        stopReason = stopReason,
        usage = usage
      )
    )
    close()
  }.flowOn(Dispatchers.IO)

  private fun readUsage(event: SseEvent): TokenUsage? {
    val chunk = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
      ?: return null
    val usage = chunk.getAsJsonObject("usage") ?: return null
    return TokenUsage(
      inputTokens = usage.get("prompt_tokens")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
      outputTokens = usage.get("completion_tokens")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
    )
  }

  private suspend fun ProducerScope<ChatStreamEvent>.handleEvent(
    event: SseEvent,
    text: StringBuilder,
    reasoning: StringBuilder,
    calls: MutableMap<Int, PartialCall>
  ): StopReason? {
    val chunk = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
      ?: return null

    chunk.getAsJsonObject("error")?.let { error ->
      val message = error.get("message")?.takeIf { it.isJsonPrimitive }?.asString
      send(ChatStreamEvent.Failed(message ?: "Provider returned an error"))
      return null
    }

    val choice = chunk.getAsJsonArray("choices")
      ?.firstOrNull()
      ?.takeIf { it.isJsonObject }
      ?.asJsonObject
      ?: return null

    val stopReason = choice.get("finish_reason")
      ?.takeIf { it.isJsonPrimitive }
      ?.asString
      ?.let { reason ->
        when (reason) {
          "tool_calls", "function_call" -> StopReason.TOOL_USE
          "length" -> StopReason.MAX_TOKENS
          else -> StopReason.END_TURN
        }
      }

    val delta = choice.getAsJsonObject("delta") ?: return stopReason

    delta.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.let { content ->
      if (content.isNotEmpty()) {
        text.append(content)
        send(ChatStreamEvent.TextDelta(content))
      }
    }

    REASONING_FIELDS.firstNotNullOfOrNull { field ->
      delta.get(field)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
    }?.let { content ->
      reasoning.append(content)
      send(ChatStreamEvent.ReasoningDelta(content))
    }

    delta.getAsJsonArray("tool_calls")?.forEach { element ->
      if (!element.isJsonObject) {
        return@forEach
      }

      val toolCall = element.asJsonObject
      val index = toolCall.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
      val partial = calls.getOrPut(index) { PartialCall(index) }

      toolCall.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.let { partial.id = it }

      val function = toolCall.getAsJsonObject("function")

      function?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.let { name ->
        if (name.isNotEmpty() && partial.name != name) {
          partial.name = name
          send(ChatStreamEvent.ToolCallStarted(index, partial.id, name))
        }
      }

      function?.get("arguments")?.takeIf { it.isJsonPrimitive }?.asString?.let { args ->
        if (args.isNotEmpty()) {
          partial.arguments.append(args)
          send(ChatStreamEvent.ToolCallArgumentsDelta(index, args))
        }
      }
    }

    return stopReason
  }

  private fun buildBody(request: ChatRequest): JsonObject {
    val body = JsonObject()
    body.addProperty("model", request.model)
    body.addProperty("stream", true)
    body.addProperty("max_tokens", request.maxTokens)

    val streamOptions = JsonObject()
    streamOptions.addProperty("include_usage", true)
    body.add("stream_options", streamOptions)

    if (request.thinkingLevel.isEnabled) {
      body.addProperty("reasoning_effort", request.thinkingLevel.wireValue)
    }

    val messages = JsonArray()
    request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
      messages.add(textMessage("system", prompt))
    }
    request.messages.forEach { message -> appendMessage(messages, message) }
    body.add("messages", messages)

    if (request.tools.isNotEmpty()) {
      val tools = JsonArray()
      request.tools.forEach { spec ->
        val function = JsonObject()
        function.addProperty("name", spec.name)
        function.addProperty("description", spec.description)
        function.add("parameters", JsonParser.parseString(spec.parametersSchemaJson))

        val tool = JsonObject()
        tool.addProperty("type", "function")
        tool.add("function", function)
        tools.add(tool)
      }
      body.add("tools", tools)
      body.addProperty("tool_choice", "auto")
    }

    return body
  }

  private fun appendMessage(messages: JsonArray, message: ChatMessage) {
    when (message.role) {
      ChatRole.SYSTEM -> messages.add(textMessage("system", message.text))
      ChatRole.USER -> messages.add(textMessage("user", message.text))

      ChatRole.TOOL -> message.toolResults.forEach { result ->
        val entry = JsonObject()
        entry.addProperty("role", "tool")
        entry.addProperty("tool_call_id", result.callId)
        entry.addProperty("content", result.content)
        messages.add(entry)
      }

      ChatRole.ASSISTANT -> {
        val entry = JsonObject()
        entry.addProperty("role", "assistant")
        if (message.text.isNotEmpty()) {
          entry.addProperty("content", message.text)
        }
        if (message.toolCalls.isNotEmpty()) {
          val toolCalls = JsonArray()
          message.toolCalls.forEach { call ->
            val function = JsonObject()
            function.addProperty("name", call.name)
            function.addProperty("arguments", call.argumentsJson)

            val toolCall = JsonObject()
            toolCall.addProperty("id", call.id)
            toolCall.addProperty("type", "function")
            toolCall.add("function", function)
            toolCalls.add(toolCall)
          }
          entry.add("tool_calls", toolCalls)
        }
        messages.add(entry)
      }
    }
  }

  private fun textMessage(role: String, text: String): JsonObject {
    val message = JsonObject()
    message.addProperty("role", role)
    message.addProperty("content", text)
    return message
  }

  private class PartialCall(val index: Int) {
    var id: String = ""
    var name: String = ""
    val arguments = StringBuilder()
  }

  companion object {

    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "gpt-4o-mini"

    private const val DONE = "[DONE]"

    private val REASONING_FIELDS = listOf("reasoning_content", "reasoning")
  }
}
