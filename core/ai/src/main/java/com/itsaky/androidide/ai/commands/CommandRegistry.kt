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

package com.itsaky.androidide.ai.commands

import java.io.File
import org.slf4j.LoggerFactory

class CommandRegistry(private val commands: Map<String, SlashCommand>) {

  fun all(): List<SlashCommand> = commands.values.sortedBy { it.name }

  fun find(name: String): SlashCommand? = commands[name.lowercase()]

  fun resolve(input: String): CommandResolution {
    val invocation = CommandParser.parse(input) ?: return CommandResolution.NotACommand
    val command = find(invocation.name)
      ?: return CommandResolution.Unknown(invocation.name, all().map { it.name })

    return CommandResolution.Expanded(
      command = command,
      prompt = CommandParser.expand(command.template, invocation.arguments)
    )
  }

  companion object {

    private val log = LoggerFactory.getLogger(CommandRegistry::class.java)

    const val COMMANDS_DIR = ".androidide/commands"

    private const val MAX_FILE_SIZE = 128L * 1024

    fun load(projectDir: File?): CommandRegistry {
      val merged = LinkedHashMap<String, SlashCommand>()
      BuiltInCommands.all().forEach { merged[it.name] = it }
      fromProject(projectDir).forEach { merged[it.name] = it }
      return CommandRegistry(merged)
    }

    private fun fromProject(projectDir: File?): List<SlashCommand> {
      val dir = projectDir?.resolve(COMMANDS_DIR)?.takeIf { it.isDirectory } ?: return emptyList()

      return dir.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
        .sortedBy { it.name }
        .mapNotNull { file ->
          runCatching { parseFile(file) }
            .onFailure { log.debug("Ignoring command file {}", file.name, it) }
            .getOrNull()
        }
    }

    private fun parseFile(file: File): SlashCommand? {
      if (file.length() > MAX_FILE_SIZE) {
        return null
      }

      val name = file.nameWithoutExtension.trim().lowercase()
      if (name.isEmpty()) {
        return null
      }

      val document = MarkdownFrontMatter.parse(file.readText())
      val template = document.body.trim()
      if (template.isEmpty()) {
        return null
      }

      return SlashCommand(
        name = name,
        description = document.values["description"].orEmpty(),
        template = template
      )
    }
  }
}
