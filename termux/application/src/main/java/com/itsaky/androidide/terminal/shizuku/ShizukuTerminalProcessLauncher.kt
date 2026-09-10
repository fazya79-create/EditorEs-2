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

package com.itsaky.androidide.terminal.shizuku

import android.content.Context
import com.itsaky.androidide.utils.flashError
import com.termux.R
import com.termux.shared.logger.Logger
import com.termux.terminal.TerminalProcess

class ShizukuTerminalProcessLauncher(
  private val context: Context,
  private val service: IPrivilegedPtyService
) : TerminalProcess.Launcher {

  companion object {
    private const val LOG_TAG = "ShizukuTerminalProcessLauncher"
  }

  override fun launch(
    shellPath: String,
    cwd: String,
    args: Array<String>,
    env: Array<String>,
    rows: Int,
    columns: Int
  ): TerminalProcess {
    return try {
      val pty = service.createPty(shellPath, cwd, args, env, rows, columns)
      ShizukuTerminalProcess(service, pty).also { ShizukuTerminal.onProcessStarted(it) }
    } catch (e: Exception) {
      Logger.logStackTraceWithMessage(LOG_TAG, "Failed to create privileged pty", e)
      flashError(
        context.getString(R.string.msg_shizuku_pty_failed, e.message ?: e.javaClass.simpleName)
      )
      FailedProcess
    }
  }

  private object FailedProcess : TerminalProcess {
    override fun getPid(): Int = 0
    override fun getFileDescriptor(): Int = -1
    override fun waitFor(): Int = 1
    override fun kill() {}
    override fun close() {}
    override fun getCwd(): String? = null
  }
}
