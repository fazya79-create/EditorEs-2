package com.itsaky.androidide.terminal.shizuku

import android.os.RemoteException
import com.termux.terminal.JNI
import com.termux.terminal.TerminalSession
import java.io.IOException

class ShizukuPtyProcessHandler(
  private val service: IShizukuTerminalService
) : TerminalSession.PtyProcessHandler {

  override fun createSubprocess(
    cmd: String?,
    cwd: String?,
    args: Array<out String>?,
    env: Array<out String>?,
    processId: IntArray?,
    rows: Int,
    columns: Int
  ): Int {
    val pidArr = IntArray(1)
    val pfd = try {
      service.createSubprocess(
        cmd ?: "/system/bin/sh",
        cwd,
        args,
        env,
        pidArr,
        rows,
        columns
      )
    } catch (e: RemoteException) {
      throw IOException(e.message, e)
    } ?: throw IOException("Failed to create privileged subprocess")

    if (processId != null && processId.isNotEmpty()) {
      processId[0] = pidArr[0]
    }
    return pfd.detachFd()
  }

  override fun setPtyWindowSize(fd: Int, rows: Int, columns: Int) {
    runCatching {
      JNI.setPtyWindowSize(fd, rows, columns)
    }
  }

  override fun waitFor(processId: Int): Int {
    return try {
      service.waitFor(processId)
    } catch (e: Exception) {
      -1
    }
  }

  override fun finishIfRunning(processId: Int) {
    runCatching {
      service.finishIfRunning(processId)
    }
  }

  override fun close(fd: Int) {
    runCatching {
      JNI.close(fd)
    }
  }
}
