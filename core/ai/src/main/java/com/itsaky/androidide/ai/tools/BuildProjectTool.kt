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
import com.itsaky.androidide.backend.build.BuildEvent
import com.itsaky.androidide.backend.build.BuildRequest
import com.itsaky.androidide.backend.build.BuildRunner
import com.itsaky.androidide.backend.build.RunConfigurations
import com.itsaky.androidide.backend.proot.ProotConfig
import com.itsaky.androidide.eventbus.events.file.ProjectFilesChangedEvent
import com.itsaky.androidide.preferences.internal.BackendPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

class BuildProjectTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Compile the open CMake project with the presets the IDE is configured to " +
        "use, and return the compiler output with the exit code. Use it to check that your " +
        "edits actually build before you report them as done, and to read the real compiler " +
        "errors instead of guessing at them. Prefer this over running cmake through run_shell: " +
        "it picks the same NDK toolchain, ABI and build type the IDE's own build button uses.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "clean": {
            "type": "boolean",
            "description": "Rebuild from scratch instead of reusing existing build artifacts. Slow, so leave it out unless a stale build is suspected."
          }
        },
        "required": []
      }
    """.trimIndent(),
    mutating = true
  )

  override fun describe(arguments: JsonObject): String =
    if (arguments.get("clean")?.asBooleanOrNull() == true) {
      "Rebuild the project from scratch"
    } else {
      "Build the project"
    }

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val applicationContext = context.applicationContext
      if (!ProotConfig.isInstalled(applicationContext)) {
        throw ToolException(
          "The Ubuntu environment is not installed. Install it from Preferences > Backend."
        )
      }

      val projectDir = WorkspacePaths.projectDir()
      if (!projectDir.isDirectory) {
        throw ToolException("No project directory is available.")
      }

      val clean = arguments.get("clean")?.asBooleanOrNull() == true
      val runner = BuildRunner(
        applicationContext,
        BackendPreferences.abis(),
        BackendPreferences.buildApiLevel,
        BackendPreferences.buildType()
      )

      val presets = RunConfigurations(projectDir, runner).activePresets()
      if (presets.isEmpty()) {
        throw ToolException("No CMake presets were found for this project.")
      }

      val output = StringBuilder()
      var exitCode = 0
      var failure: String? = null

      for (preset in presets) {
        output.append("> build ").append(preset).append('\n')
        val request = if (clean) BuildRequest.CleanBuild(preset) else BuildRequest.Build(preset)
        runner.run(projectDir, request) { event ->
          when (event) {
            is BuildEvent.Line -> if (output.length < MAX_OUTPUT) {
              output.append(event.text).append('\n')
            }

            is BuildEvent.Finished -> exitCode = event.exitCode
            is BuildEvent.Failed -> failure = event.message
          }
        }
        if (failure != null || exitCode != 0) {
          break
        }
      }

      EventBus.getDefault().post(ProjectFilesChangedEvent())
      format(exitCode, failure, output.toString())
    }

  private fun format(exitCode: Int, failure: String?, output: String): String {
    val trimmed = if (output.length >= MAX_OUTPUT) {
      output.take(MAX_OUTPUT) + "\n... [output truncated]"
    } else {
      output
    }

    return buildString {
      if (failure != null) {
        append("build failed: ").append(failure).append('\n')
      } else {
        append("exit code: ").append(exitCode).append('\n')
      }
      if (trimmed.isBlank()) {
        append("(no output)")
      } else {
        append(trimmed.trimEnd())
      }
    }
  }

  companion object {

    const val NAME = "build_project"

    private const val MAX_OUTPUT = 50_000
  }
}
