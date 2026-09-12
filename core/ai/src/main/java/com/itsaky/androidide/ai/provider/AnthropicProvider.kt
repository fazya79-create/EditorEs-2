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

class AnthropicProvider(private val config: ProviderConfig) : LlmProvider {

  override val kind: ProviderKind = ProviderKind.ANTHROPIC

  override fun stream(request: ChatRequest): Flow<ChatStreamEvent> = callbackFlow {
    val text = StringBuilder()
    val reasoning = StringBuilder()
    val blocks = sortedMapOf<Int, PartialBlock>()
    var stopReason = StopReason.END_TURN
    var usage = TokenUsage()

    try {
      SseClient.post(
        url = "${config.baseUrl.trimEnd('/')}/messages",
        headers = mapOf(
          "x-api-key" to config.apiKey,
          "anthropic-version" to API_VERSION
        ),
        body = buildBody(request).toString()
      ).use { connection ->
        while (isActive) {
          val event = connection.next() ?: break
          usage = mergeUsage(event, usage)
          if (event.name == EVENT_MESSAGE_STOP) {
            break
          }
          val reason = handleEvent(event, text, reasoning, blocks)
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

    val toolCalls = blocks.values
      .filter { it.kind == BlockKind.TOOL_USE }
      .map { block ->
        ToolCall(
          id = block.id.ifEmpty { "toolu_${block.index}" },
          name = block.name,
          argumentsJson = block.arguments.toString().ifBlank { "{}" }
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

  private fun mergeUsage(event: SseEvent, current: TokenUsage): TokenUsage {
    val payload = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
      ?: return current

    val usage = payload.getAsJsonObject("usage")
      ?: payload.getAsJsonObject("message")?.getAsJsonObject("usage")
      ?: return current

    val cacheRead = usage.get("cache_read_input_tokens")?.takeIf { it.isJsonPrimitive }?.asInt
    val cacheWrite = usage.get("cache_creation_input_tokens")?.takeIf { it.isJsonPrimitive }?.asInt
    val uncached = usage.get("input_tokens")?.takeIf { it.isJsonPrimitive }?.asInt
    val output = usage.get("output_tokens")?.takeIf { it.isJsonPrimitive }?.asInt

    val input = if (uncached == null) {
      null
    } else {
      uncached + (cacheRead ?: 0) + (cacheWrite ?: 0)
    }

    return TokenUsage(
      inputTokens = input ?: current.inputTokens,
      outputTokens = output ?: current.outputTokens,
      cachedInputTokens = cacheRead ?: current.cachedInputTokens
    )
  }

  private suspend fun ProducerScope<ChatStreamEvent>.handleEvent(
    event: SseEvent,
    text: StringBuilder,
    reasoning: StringBuilder,
    blocks: MutableMap<Int, PartialBlock>
  ): StopReason? {
    val payload = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
      ?: return null

    when (event.name ?: payload.get("type")?.asString) {
      EVENT_ERROR -> {
        val message = payload.getAsJsonObject("error")
          ?.get("message")
          ?.takeIf { it.isJsonPrimitive }
          ?.asString
        send(ChatStreamEvent.Failed(message ?: "Provider returned an error"))
      }

      EVENT_CONTENT_BLOCK_START -> {
        val index = payload.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: return null
        val block = payload.getAsJsonObject("content_block") ?: return null
        when (block.get("type")?.asString) {
          TYPE_TOOL_USE -> {
            val partial = PartialBlock(index, BlockKind.TOOL_USE).apply {
              id = block.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
              name = block.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            }
            blocks[index] = partial
            send(ChatStreamEvent.ToolCallStarted(index, partial.id, partial.name))
          }

          TYPE_THINKING -> blocks[index] = PartialBlock(index, BlockKind.THINKING)

          else -> blocks[index] = PartialBlock(index, BlockKind.TEXT)
        }
      }

      EVENT_CONTENT_BLOCK_DELTA -> {
        val index = payload.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: return null
        val delta = payload.getAsJsonObject("delta") ?: return null

        when (delta.get("type")?.asString) {
          TYPE_TEXT_DELTA -> {
            val content = delta.get("text")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (content.isNotEmpty()) {
              text.append(content)
              send(ChatStreamEvent.TextDelta(content))
            }
          }

          TYPE_THINKING_DELTA -> {
            val content = delta.get("thinking")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (content.isNotEmpty()) {
              reasoning.append(content)
              blocks.getOrPut(index) { PartialBlock(index, BlockKind.THINKING) }
                .arguments
                .append(content)
              send(ChatStreamEvent.ReasoningDelta(content))
            }
          }

          TYPE_SIGNATURE_DELTA -> {
            val signature = delta.get("signature")
              ?.takeIf { it.isJsonPrimitive }
              ?.asString
              .orEmpty()
            blocks.getOrPut(index) { PartialBlock(index, BlockKind.THINKING) }
              .signature = signature
          }

          TYPE_INPUT_JSON_DELTA -> {
            val json = delta.get("partial_json")
              ?.takeIf { it.isJsonPrimitive }
              ?.asString
              .orEmpty()
            if (json.isNotEmpty()) {
              blocks.getOrPut(index) { PartialBlock(index, BlockKind.TOOL_USE) }
                .arguments
                .append(json)
              send(ChatStreamEvent.ToolCallArgumentsDelta(index, json))
            }
          }
        }
      }

      EVENT_MESSAGE_DELTA -> {
        return payload.getAsJsonObject("delta")
          ?.get("stop_reason")
          ?.takeIf { it.isJsonPrimitive }
          ?.asString
          ?.let { reason ->
            when (reason) {
              "tool_use" -> StopReason.TOOL_USE
              "max_tokens" -> StopReason.MAX_TOKENS
              else -> StopReason.END_TURN
            }
          }
      }
    }

    return null
  }

  private fun buildBody(request: ChatRequest): JsonObject {
    val body = JsonObject()
    body.addProperty("model", request.model)
    body.addProperty("stream", true)
    body.addProperty("max_tokens", request.maxTokens)

    val toolsText = request.tools.joinToString("") { spec ->
      spec.name + spec.description + spec.parametersSchemaJson
    }

    request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
      if (config.promptCaching && PromptCache.isWorthCaching(toolsText, prompt)) {
        val block = JsonObject()
        block.addProperty("type", "text")
        block.addProperty("text", prompt)
        block.add("cache_control", PromptCache.ephemeral())

        val system = JsonArray()
        system.add(block)
        body.add("system", system)
      } else {
        body.addProperty("system", prompt)
      }
    }

    if (request.thinkingLevel.isEnabled) {
      val thinking = JsonObject()
      thinking.addProperty("type", "adaptive")
      body.add("thinking", thinking)

      val outputConfig = JsonObject()
      outputConfig.addProperty("effort", request.thinkingLevel.wireValue)
      body.add("output_config", outputConfig)
    }

    val messages = JsonArray()
    request.messages.forEach { message -> appendMessage(messages, message) }
    if (config.promptCaching) {
      markCacheBreakpoint(messages)
    }
    body.add("messages", messages)

    if (request.tools.isNotEmpty()) {
      val tools = JsonArray()
      request.tools.forEachIndexed { index, spec ->
        val tool = JsonObject()
        tool.addProperty("name", spec.name)
        tool.addProperty("description", spec.description)
        tool.add("input_schema", JsonParser.parseString(spec.parametersSchemaJson))
        if (config.promptCaching &&
          index == request.tools.lastIndex &&
          PromptCache.isWorthCaching(toolsText)
        ) {
          tool.add("cache_control", PromptCache.ephemeral())
        }
        tools.add(tool)
      }
      body.add("tools", tools)
    }

    return body
  }

  private fun markCacheBreakpoint(messages: JsonArray) {
    val index = messages.indexOfLast { element ->
      val entry = element.takeIf { it.isJsonObject }?.asJsonObject
      entry != null && entry.get("role")?.asString == "assistant"
    }
    if (index < 0) {
      return
    }

    val blocks = messages[index].asJsonObject.getAsJsonArray("content") ?: return
    val last = blocks.lastOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return

    val cacheable = messages.take(index + 1).sumOf { it.toString().length }
    if (cacheable < PromptCache.MIN_CACHEABLE_CHARS) {
      return
    }

    last.add("cache_control", PromptCache.ephemeral())
  }

  private fun appendMessage(messages: JsonArray, message: ChatMessage) {
    when (message.role) {
      ChatRole.SYSTEM -> Unit

      ChatRole.USER -> messages.add(textMessage("user", message.text))

      ChatRole.TOOL -> {
        if (message.toolResults.isEmpty()) {
          return
        }
        val content = JsonArray()
        message.toolResults.forEach { result ->
          val block = JsonObject()
          block.addProperty("type", TYPE_TOOL_RESULT)
          block.addProperty("tool_use_id", result.callId)
          block.addProperty("content", result.content)
          if (result.isError) {
            block.addProperty("is_error", true)
          }
          content.add(block)
        }

        val entry = JsonObject()
        entry.addProperty("role", "user")
        entry.add("content", content)
        messages.add(entry)
      }

      ChatRole.ASSISTANT -> {
        val content = JsonArray()
        if (message.text.isNotEmpty()) {
          val block = JsonObject()
          block.addProperty("type", "text")
          block.addProperty("text", message.text)
          content.add(block)
        }
        message.toolCalls.forEach { call ->
          val block = JsonObject()
          block.addProperty("type", TYPE_TOOL_USE)
          block.addProperty("id", call.id)
          block.addProperty("name", call.name)
          block.add(
            "input",
            runCatching { JsonParser.parseString(call.argumentsJson) }
              .getOrNull()
              ?.takeIf { it.isJsonObject }
              ?: JsonObject()
          )
          content.add(block)
        }

        if (content.isEmpty) {
          return
        }

        val entry = JsonObject()
        entry.addProperty("role", "assistant")
        entry.add("content", content)
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

  private class PartialBlock(val index: Int, val kind: BlockKind) {
    var id: String = ""
    var name: String = ""
    var signature: String = ""
    val arguments = StringBuilder()
  }

  private enum class BlockKind {
    TEXT,
    THINKING,
    TOOL_USE
  }

  companion object {

    const val DEFAULT_BASE_URL = "https://api.anthropic.com/v1"
    const val DEFAULT_MODEL = "claude-sonnet-4-5"

    private const val API_VERSION = "2023-06-01"

    private const val EVENT_ERROR = "error"
    private const val EVENT_CONTENT_BLOCK_START = "content_block_start"
    private const val EVENT_CONTENT_BLOCK_DELTA = "content_block_delta"
    private const val EVENT_MESSAGE_DELTA = "message_delta"
    private const val EVENT_MESSAGE_STOP = "message_stop"

    private const val TYPE_TOOL_USE = "tool_use"
    private const val TYPE_TOOL_RESULT = "tool_result"
    private const val TYPE_THINKING = "thinking"
    private const val TYPE_TEXT_DELTA = "text_delta"
    private const val TYPE_THINKING_DELTA = "thinking_delta"
    private const val TYPE_SIGNATURE_DELTA = "signature_delta"
    private const val TYPE_INPUT_JSON_DELTA = "input_json_delta"
  }
}
