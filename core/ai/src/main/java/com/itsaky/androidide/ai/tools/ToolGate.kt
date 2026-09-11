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

package com.itsaky.androidide.ai.tools

import android.content.Context
import com.itsaky.androidide.ai.model.ToolCall
import com.itsaky.androidide.ai.model.ToolResult
import com.itsaky.androidide.ai.prefs.AiPreferences
import org.slf4j.LoggerFactory

enum class ApprovalDecision {
  APPROVED,
  REJECTED
}

data class ToolApprovalRequest(
  val call: ToolCall,
  val summary: String,
  val mutating: Boolean
)

fun interface ToolApprover {

  suspend fun requestApproval(request: ToolApprovalRequest): ApprovalDecision
}

class ToolGate(private val context: Context, private val approver: ToolApprover) {

  suspend fun run(call: ToolCall): ToolResult {
    val tool = ToolRegistry.find(call.name)
      ?: return failure(call, "Unknown tool '${call.name}'.")

    val arguments = parseArguments(call.argumentsJson)
    val summary = runCatching { tool.describe(arguments) }.getOrElse { call.name }

    if (tool.spec.mutating && !AiPreferences.yoloMode) {
      val decision = approver.requestApproval(
        ToolApprovalRequest(call = call, summary = summary, mutating = true)
      )
      if (decision == ApprovalDecision.REJECTED) {
        return failure(call, "The user rejected this tool call.")
      }
    }

    return try {
      ToolResult(
        callId = call.id,
        name = call.name,
        content = tool.execute(context, arguments)
      )
    } catch (err: ToolException) {
      failure(call, err.message ?: "The tool call failed.")
    } catch (err: Throwable) {
      log.error("Tool '{}' failed", call.name, err)
      failure(call, err.message ?: "The tool call failed unexpectedly.")
    }
  }

  fun summarize(call: ToolCall): String {
    val tool = ToolRegistry.find(call.name) ?: return call.name
    return runCatching { tool.describe(parseArguments(call.argumentsJson)) }
      .getOrElse { call.name }
  }

  fun isMutating(call: ToolCall): Boolean =
    ToolRegistry.find(call.name)?.spec?.mutating ?: true

  private fun failure(call: ToolCall, message: String) = ToolResult(
    callId = call.id,
    name = call.name,
    content = message,
    isError = true
  )

  companion object {

    private val log = LoggerFactory.getLogger(ToolGate::class.java)
  }
}
