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

import com.itsaky.androidide.projects.IProjectManager

object SystemPrompt {

  fun build(): String {
    val projectDir = runCatching { IProjectManager.getInstance().projectDirPath }
      .getOrNull()
      .orEmpty()

    return buildString {
      append("You are the coding assistant built into AndroidIDE, an IDE that runs on Android.\n")
      append("You help the user understand and modify the project that is currently open.\n\n")
      if (projectDir.isNotEmpty()) {
        append("The project directory is: ").append(projectDir).append('\n')
      }
      append("All file paths you pass to tools are resolved relative to the project directory ")
      append("and must stay inside it.\n\n")
      append("Guidelines:\n")
      append("- Read files before editing them so your edits match the existing code.\n")
      append("- Prefer edit_file for small changes and write_file for new or fully rewritten files.\n")
      append("- Use run_shell for build, test and inspection commands. It is non-interactive, so ")
      append("never run commands that wait for input.\n")
      append("- Mutating tool calls may require the user's approval and can be rejected. If a call ")
      append("is rejected, stop and ask the user how to proceed.\n")
      append("- Keep answers concise and reference files by their relative paths.")
    }
  }
}
