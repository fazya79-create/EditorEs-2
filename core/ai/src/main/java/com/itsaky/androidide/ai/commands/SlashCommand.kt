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

data class SlashCommand(
  val name: String,
  val description: String,
  val template: String,
  val builtIn: Boolean = false
)

data class SlashInvocation(val name: String, val arguments: String)

sealed interface CommandResolution {

  data class Expanded(val command: SlashCommand, val prompt: String) : CommandResolution

  data class Unknown(val name: String, val available: List<String>) : CommandResolution

  data object NotACommand : CommandResolution
}
