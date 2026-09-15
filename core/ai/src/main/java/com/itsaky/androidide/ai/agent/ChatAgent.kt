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
import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatStreamEvent
import com.itsaky.androidide.ai.model.StopReason
import com.itsaky.androidide.ai.model.ThinkingLevel
import com.itsaky.androidide.ai.model.TokenUsage
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.model.ToolResult
import com.itsaky.androidide.ai.model.ToolSpec
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.tools.TodoItem
import com.itsaky.androidide.ai.tools.TodoWriteTool
import com.itsaky.androidide.ai.tools.ToolGate
import com.itsaky.androidide.ai.tools.ToolRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

sealed interface AgentEvent {

  data class TextDelta(val text: String) : AgentEvent

  data class ReasoningDelta(val text: String) : AgentEvent

  data class AssistantMessage(val message: ChatMessage) : AgentEvent

  data class ToolStarted(val call: ToolCall, val summary: String) : AgentEvent

  data class ToolFinished(val call: ToolCall, val result: ToolResult) : AgentEvent

  data class TodosUpdated(val items: List<TodoItem>) : AgentEvent

  data class Failed(val message: String) : AgentEvent

  data class Reconnecting(
    val attempt: Int,
    val maxAttempts: Int,
    val delayMillis: Long,
    val offline: Boolean
  ) : AgentEvent

  data class Interrupted(val message: String, val partial: String) : AgentEvent

  data class ContextCompacted(
    val history: List<ChatMessage>,
    val replaced: Int
  ) : AgentEvent

  data object CompactionFailed : AgentEvent

  data class UsageUpdated(val usage: TokenUsage, val contextWindow: Int) : AgentEvent

  data object TurnCompleted : AgentEvent
}

class ChatAgent(
  private val provider: LlmProvider,
  private val gate: ToolGate,
  private val model: String,
  private val systemPrompt: String,
  private val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
  private val maxToolRounds: Int = DEFAULT_MAX_TOOL_ROUNDS,
  private val contextWindow: Int = 0,
  private val compactThresholdPercent: Int = 0,
  private val tools: List<ToolSpec> = ToolRegistry.specs()
) {

  fun run(history: List<ChatMessage>): Flow<AgentEvent> = flow {
    val messages = history.toMutableList()
    var round = 0

    while (true) {
      val request = ChatRequest(
        model = model,
        messages = ToolResultTrimmer.trim(messages),
        tools = tools,
        systemPrompt = systemPrompt,
        thinkingLevel = thinkingLevel
      )

      var completed: ChatMessage? = null
      var stopReason = StopReason.END_TURN
      var usage = TokenUsage()
      var failed = false
      var interrupted = false

      provider.stream(request).collect { event ->
        when (event) {
          is ChatStreamEvent.TextDelta -> emit(AgentEvent.TextDelta(event.text))

          is ChatStreamEvent.ReasoningDelta -> emit(AgentEvent.ReasoningDelta(event.text))

          is ChatStreamEvent.Completed -> {
            completed = event.message
            stopReason = event.stopReason
            usage = event.usage
          }

          is ChatStreamEvent.Failed -> {
            failed = true
            emit(AgentEvent.Failed(event.message))
          }

          is ChatStreamEvent.Interrupted -> {
            interrupted = true
            if (!event.partial.isBlank) {
              messages += event.partial
              emit(AgentEvent.AssistantMessage(event.partial))
            }
            emit(AgentEvent.Interrupted(event.message, event.partial.text))
          }

          is ChatStreamEvent.ToolCallStarted,
          is ChatStreamEvent.ToolCallArgumentsDelta -> Unit

          is ChatStreamEvent.Reconnecting -> emit(
            AgentEvent.Reconnecting(
              attempt = event.attempt,
              maxAttempts = event.maxAttempts,
              delayMillis = event.delayMillis,
              offline = event.offline
            )
          )
        }
      }

      if (failed || interrupted) {
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      val assistant = completed
      if (assistant == null) {
        emit(AgentEvent.Failed("The provider closed the stream without a response."))
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      if (!usage.isEmpty) {
        emit(AgentEvent.UsageUpdated(usage, contextWindow))
      }

      if (!assistant.isBlank) {
        messages += assistant
        emit(AgentEvent.AssistantMessage(assistant))
      }

      if (stopReason != StopReason.TOOL_USE || assistant.toolCalls.isEmpty()) {
        compactIfNeeded(usage, messages).forEach { emit(it) }
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      if (maxToolRounds in 1..round) {
        emit(AgentEvent.Failed("Stopped after $maxToolRounds tool rounds."))
        emit(AgentEvent.TurnCompleted)
        return@flow
      }

      val results = executeToolCalls(assistant.toolCalls) { emit(it) }

      messages += ChatMessage.toolResults(results)
      round++

      compactIfNeeded(usage, messages).forEach { emit(it) }
    }
  }

  private suspend fun executeToolCalls(
    calls: List<ToolCall>,
    emit: suspend (AgentEvent) -> Unit
  ): List<ToolResult> {
    val results = arrayOfNulls<ToolResult>(calls.size)
    var index = 0

    log.debug("Tool round: {}", calls.joinToString(", ") { it.name })

    while (index < calls.size) {
      val batch = parallelBatchAt(calls, index)
      val group = calls.subList(index, index + batch)

      group.forEach { call -> emit(AgentEvent.ToolStarted(call, gate.summarize(call))) }

      val completed = if (batch == 1) {
        listOf(gate.run(group.first()))
      } else {
        coroutineScope { group.map { call -> async { gate.run(call) } }.awaitAll() }
      }

      completed.forEachIndexed { offset, result ->
        results[index + offset] = result
        emit(AgentEvent.ToolFinished(group[offset], result))
      }

      if (group.indices.any { offset -> wroteTodos(group[offset], completed[offset]) }) {
        emitTodos(emit)
      }

      index += batch
    }

    return results.map { requireNotNull(it) }
  }

  private fun wroteTodos(call: ToolCall, result: ToolResult): Boolean =
    call.name == TodoWriteTool.NAME && !result.isError

  private suspend fun emitTodos(emit: suspend (AgentEvent) -> Unit) {
    val todos = gate.todos()
    log.debug(
      "Task list updated: {}",
      todos.joinToString(", ") { "${it.id}=${it.status.wireValue}" }
    )
    emit(AgentEvent.TodosUpdated(todos))
  }

  private fun parallelBatchAt(calls: List<ToolCall>, start: Int): Int {
    if (!gate.isParallelSafe(calls[start])) {
      return 1
    }
    var end = start
    while (end < calls.size && gate.isParallelSafe(calls[end])) {
      end++
    }
    return end - start
  }

  suspend fun compactNow(history: List<ChatMessage>): List<AgentEvent> {
    val messages = history.toMutableList()
    val events = compact(messages)
    return events.ifEmpty { listOf(AgentEvent.CompactionFailed) }
  }

  private suspend fun compactIfNeeded(
    usage: TokenUsage,
    messages: MutableList<ChatMessage>
  ): List<AgentEvent> {
    if (!ContextCompactor.shouldCompact(usage.total, contextWindow, compactThresholdPercent)) {
      return emptyList()
    }
    return compact(messages)
  }

  private suspend fun compact(messages: MutableList<ChatMessage>): List<AgentEvent> {
    val (older, recent) = ContextCompactor.split(messages)
    if (older.isEmpty()) {
      return emptyList()
    }

    val summary = summarise(older) ?: return listOf(AgentEvent.CompactionFailed)
    val replaced = older.size

    messages.clear()
    messages += ContextCompactor.asSummaryMessage(summary)
    messages += recent

    gate.activeTodoSummary()?.let { messages += ChatMessage.user(it) }

    // The token count that triggered compaction describes the history that was just discarded.
    // Reporting an empty reading retires it until the next response measures the new history.
    return listOf(
      AgentEvent.ContextCompacted(messages.toList(), replaced),
      AgentEvent.UsageUpdated(TokenUsage(), contextWindow)
    )
  }

  private suspend fun summarise(older: List<ChatMessage>): String? {
    val request = ChatRequest(
      model = model,
      messages = ContextCompactor.summaryRequest(older),
      systemPrompt = null,
      maxTokens = SUMMARY_MAX_TOKENS
    )

    val text = StringBuilder()
    var failed = false

    provider.stream(request).collect { event ->
      when (event) {
        is ChatStreamEvent.Completed -> text.append(event.message.text)
        is ChatStreamEvent.Failed -> failed = true
        is ChatStreamEvent.Interrupted -> failed = true
        else -> Unit
      }
    }

    return if (failed || text.isBlank()) null else text.toString()
  }

  companion object {

    const val DEFAULT_MAX_TOOL_ROUNDS = 12

    private const val SUMMARY_MAX_TOKENS = 2048

    private val log = LoggerFactory.getLogger(ChatAgent::class.java)
  }
}
