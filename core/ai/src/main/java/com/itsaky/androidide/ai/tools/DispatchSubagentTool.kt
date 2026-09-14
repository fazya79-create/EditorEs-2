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
import com.google.gson.JsonObject
import com.itsaky.androidide.ai.agent.SubagentRequest
import com.itsaky.androidide.ai.agent.SubagentRunner
import com.itsaky.androidide.ai.model.ToolSpec

class DispatchSubagentTool(private val runner: SubagentRunner) : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Delegate a self-contained sub-task to a sub-agent that works in its own " +
        "conversation and reports back a single summary. Use it for focused work that would " +
        "otherwise flood this conversation, such as searching the project for every use of a " +
        "symbol or researching a question before you act on it. Do not use it for a single " +
        "file read or one shell command, which are faster to do directly. The sub-agent cannot " +
        "see this conversation, so the 'prompt' must be self-contained and must state exactly " +
        "what you want reported back.\n" +
        "Tools the sub-agent gets for each scope:\n" +
        "- read_only: ${READ_ONLY_TOOLS}. It cannot change files or run commands, so never ask " +
        "it to build, compile, test or edit anything.\n" +
        "- full: ${FULL_TOOLS}.\n" +
        "Choose read_only unless the sub-task genuinely has to change something.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "description": {
            "type": "string",
            "description": "A short label for the sub-task, three to five words."
          },
          "prompt": {
            "type": "string",
            "description": "The full, self-contained instruction for the sub-agent, including what it must report back."
          },
          "tool_scope": {
            "type": "string",
            "enum": ["read_only", "full"],
            "description": "'read_only' gives the sub-agent reading and searching only. 'full' also allows editing files and running commands."
          }
        },
        "required": ["description", "prompt"]
      }
    """.trimIndent(),
    mutating = false,
    parallelSafe = true
  )

  override fun describe(arguments: JsonObject): String {
    val description = arguments.get("description")?.asStringOrNull()?.takeIf { it.isNotBlank() }
      ?: arguments.get("prompt")?.asStringOrNull()?.take(PREVIEW_CHARS)
      ?: "<missing task>"
    val scope = ToolScope.fromWire(arguments.get("tool_scope")?.asStringOrNull())
    return "Delegate (${scope.wireValue}): $description"
  }

  override suspend fun execute(context: Context, arguments: JsonObject): String {
    val prompt = arguments.get("prompt")?.asStringOrNull()?.trim()
    if (prompt.isNullOrEmpty()) {
      throw ToolException("Missing required argument 'prompt'.")
    }

    val description = arguments.get("description")?.asStringOrNull()?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: prompt.take(PREVIEW_CHARS)

    val outcome = runner.run(
      SubagentRequest(
        description = description,
        prompt = prompt,
        scope = ToolScope.fromWire(arguments.get("tool_scope")?.asStringOrNull())
      )
    )

    if (outcome.failed) {
      throw ToolException(
        outcome.text.ifBlank { "The sub-agent stopped before producing a result." }
      )
    }

    if (outcome.text.isBlank()) {
      throw ToolException("The sub-agent finished without reporting anything.")
    }

    return outcome.text
  }

  companion object {

    const val NAME = "dispatch_subagent"

    private val BASE_TOOLS = listOf(
      ReadFileTool(),
      GlobTool(),
      GrepTool(),
      WriteFileTool(),
      EditFileTool(),
      RunShellTool(),
      WebSearchTool(),
      WebFetchTool()
    ).map { it.spec }

    private val READ_ONLY_TOOLS = names(ToolScope.READ_ONLY)
    private val FULL_TOOLS = names(ToolScope.FULL)

    private fun names(scope: ToolScope): String =
      ToolAccess.forSubagent(scope).filter(BASE_TOOLS).joinToString(", ") { it.name }

    private const val PREVIEW_CHARS = 40
  }
}
