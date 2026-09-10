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

import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.termux.shared.logger.Logger
import com.termux.terminal.TerminalProcess
import java.util.concurrent.atomic.AtomicBoolean

class ShizukuTerminalProcess(
  private val service: IPrivilegedPtyService,
  private val pty: PrivilegedPty
) : TerminalProcess {

  companion object {
    private const val LOG_TAG = "ShizukuTerminalProcess"
  }

  private val closed = AtomicBoolean(false)

  override fun getPid(): Int = pty.pid

  override fun getFileDescriptor(): Int = pty.master.fd

  override fun waitFor(): Int {
    return try {
      service.waitFor(pty.pid)
    } catch (e: Exception) {
      Logger.logWarn(LOG_TAG, "Privileged service is gone, waiting for pty hangup of pid ${pty.pid}")
      waitForHangup()
    }
  }

  private fun waitForHangup(): Int {
    val pollfd = StructPollfd().also {
      it.fd = pty.master.fileDescriptor
      it.events = 0
    }
    val hangup = (OsConstants.POLLHUP or OsConstants.POLLERR or OsConstants.POLLNVAL).toShort()
    while (!closed.get()) {
      try {
        Os.poll(arrayOf(pollfd), 1000)
      } catch (e: Exception) {
        break
      }
      if (pollfd.revents.toInt() and hangup.toInt() != 0) break
    }
    return -OsConstants.SIGHUP
  }

  override fun kill() {
    try {
      service.kill(pty.pid, OsConstants.SIGKILL)
    } catch (e: Exception) {
      Logger.logWarn(LOG_TAG, "Failed sending SIGKILL to privileged pid ${pty.pid}, hanging up pty: ${e.message}")
      hangup()
    }
  }

  private fun hangup() {
    if (!closed.compareAndSet(false, true)) return
    runCatching { pty.master.close() }
  }

  override fun close() {
    hangup()
    ShizukuTerminal.onProcessClosed(this)
  }

  override fun getCwd(): String? {
    return try {
      service.getCwd(pty.pid)
    } catch (e: Exception) {
      null
    }
  }
}
