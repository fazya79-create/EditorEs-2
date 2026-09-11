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
import com.itsaky.androidide.ai.prefs.AiPreferences
import com.itsaky.androidide.backend.proot.ProotConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class RunShellTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Run a non-interactive shell command inside the project directory using the " +
        "IDE's Ubuntu environment. Standard output and standard error are captured and returned " +
        "together with the exit code.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "command": {
            "type": "string",
            "description": "The shell command to run. It is executed with bash -c."
          }
        },
        "required": ["command"]
      }
    """.trimIndent(),
    mutating = true
  )

  override fun describe(arguments: JsonObject): String =
    "Run: ${arguments.get("command")?.asStringOrNull() ?: "<missing command>"}"

  override suspend fun execute(context: Context, arguments: JsonObject): String =
    withContext(Dispatchers.IO) {
      val command = arguments.get("command")?.asStringOrNull()
        ?: throw ToolException("Missing required argument 'command'.")
      if (command.isBlank()) {
        throw ToolException("The 'command' argument must not be empty.")
      }

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

      var process: Process? = null
      try {
        process = start(applicationContext, projectDir, command)
        val output = process.inputStream.bufferedReader().use { reader ->
          val builder = StringBuilder()
          while (true) {
            val line = reader.readLine() ?: break
            if (builder.length < MAX_OUTPUT) {
              builder.append(line).append('\n')
            }
          }
          builder.toString()
        }

        val timeout = AiPreferences.shellTimeoutSeconds.coerceAtLeast(1)
        if (!process.waitFor(timeout.toLong(), TimeUnit.SECONDS)) {
          process.destroy()
          throw ToolException("The command timed out after $timeout seconds.")
        }

        format(process.exitValue(), output)
      } catch (err: CancellationException) {
        process?.destroy()
        throw err
      } catch (err: InterruptedException) {
        process?.destroy()
        Thread.currentThread().interrupt()
        throw CancellationException(err.message)
      } finally {
        if (process?.isAlive == true) {
          process.destroy()
        }
      }
    }

  private fun start(context: Context, projectDir: File, command: String): Process {
    val guestProject = projectDir.absolutePath
    ProotConfig.prepareStorageMounts(context)
    runCatching {
      File(ProotConfig.rootfsDir(context), guestProject.trimStart('/')).mkdirs()
    }

    val args = ProotConfig.commandArgs(
      context = context,
      script = command,
      guestCwd = guestProject,
      binds = listOf("$guestProject:$guestProject")
    )

    val builder = ProcessBuilder(args)
    builder.redirectErrorStream(true)
    builder.environment().putAll(ProotConfig.prootEnvMap(context))
    return builder.start()
  }

  private fun format(exitCode: Int, output: String): String {
    val trimmed = if (output.length >= MAX_OUTPUT) {
      output.take(MAX_OUTPUT) + "\n... [output truncated]"
    } else {
      output
    }

    return buildString {
      append("exit code: ").append(exitCode).append('\n')
      if (trimmed.isBlank()) {
        append("(no output)")
      } else {
        append(trimmed.trimEnd())
      }
    }
  }

  companion object {

    const val NAME = "run_shell"

    private const val MAX_OUTPUT = 50_000
  }
}
