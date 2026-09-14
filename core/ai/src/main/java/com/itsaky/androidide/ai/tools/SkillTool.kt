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
import com.itsaky.androidide.ai.model.ToolSpec
import com.itsaky.androidide.ai.skills.Skill
import com.itsaky.androidide.ai.skills.SkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillTool(private val registry: () -> SkillRegistry) : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Load the full instructions for one of the skills listed in the system " +
        "prompt. A skill is a packaged procedure for a specific kind of task; the system prompt " +
        "only carries its name and description, so call this to get the actual steps before you " +
        "follow them. Pass the skill's exact name.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "description": "The exact name of the skill to load, as listed in the system prompt."
          }
        },
        "required": ["name"]
      }
    """.trimIndent(),
    mutating = false,
    parallelSafe = true
  )

  override fun describe(arguments: JsonObject): String =
    "Load skill ${arguments.get("name")?.asStringOrNull() ?: "<missing name>"}"

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val name = arguments.get("name")?.asStringOrNull()?.trim()
      if (name.isNullOrEmpty()) {
        throw ToolException("Missing required argument 'name'.")
      }

      val skills = registry()
      val skill = skills.find(name)
        ?: throw ToolException(
          if (skills.isEmpty) {
            "No skills are installed. Add one under ${SkillRegistry.SEARCH_DIRS.first()}."
          } else {
            "No skill named '$name'. Available: " +
                skills.all().joinToString(", ") { it.name }
          }
        )

      render(skill)
    }

  private fun render(skill: Skill): String = buildString {
    append("# Skill: ").append(skill.name).append('\n')
    if (skill.description.isNotBlank()) {
      append(skill.description).append('\n')
    }
    if (skill.compatibility.isNotBlank()) {
      append("Requirements: ").append(skill.compatibility).append('\n')
    }
    if (skill.allowedTools.isNotEmpty()) {
      append("Tools this skill expects: ").append(skill.allowedTools.joinToString(", ")).append('\n')
    }
    append('\n')
    append(skill.body.take(MAX_BODY))
    if (skill.body.length > MAX_BODY) {
      append("\n... [skill instructions truncated]")
    }
    append("\n\n")
    append("Base directory: ").append(skill.directory.absolutePath).append('\n')
    append(
      "Paths mentioned in these instructions, such as scripts/ or references/, are relative " +
          "to that directory. Read a bundled file with read_file before you rely on it, and " +
          "prefer running a bundled script over reimplementing what it does.\n"
    )
    if (skill.resources.isNotEmpty()) {
      append("Bundled files: ").append(skill.resources.joinToString(", "))
      append('\n')
    }
  }

  companion object {

    const val NAME = "skill"

    private const val MAX_BODY = 40_000
  }
}
