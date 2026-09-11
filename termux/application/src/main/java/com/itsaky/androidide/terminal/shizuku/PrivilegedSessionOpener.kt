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

import com.itsaky.androidide.backend.proot.ProotConfig
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.shell.TermuxShellManager

class PrivilegedSessionOpener(private val activity: TermuxActivity) {

  companion object {
    private const val MAX_SESSIONS = 8
  }

  fun open(sessionName: String?, workingDirectory: String?) {
    val modes = PrivilegedSessionMode.entries
    val choices = modes.map { activity.getString(it.labelRes) }.toTypedArray<CharSequence>()
    DialogUtils.newSingleChoiceDialog(
      activity,
      activity.getString(R.string.title_privileged_session_mode),
      choices,
      0,
      true
    ) { which ->
      modes.getOrNull(which)?.let { open(it, sessionName, workingDirectory) }
    }.show()
  }

  fun open(mode: PrivilegedSessionMode, sessionName: String?, workingDirectory: String?) {
    val termuxService = activity.termuxService ?: return
    if (termuxService.termuxSessionsSize >= MAX_SESSIONS) {
      DialogUtils.newMaterialDialogBuilder(activity)
        .setTitle(R.string.title_max_terminals_reached)
        .setMessage(R.string.msg_max_terminals_reached)
        .setPositiveButton(android.R.string.ok, null)
        .show()
      return
    }
    if (mode == PrivilegedSessionMode.UBUNTU) {
      if (!(ProotConfig.isInstalled(activity) && ProotConfig.isAvailable(activity))) {
        flashError(R.string.msg_privileged_ubuntu_not_installed)
        return
      }
      val uid = ShizukuTerminal.privilegeUid()
      if (uid != null && uid != ShizukuTerminal.UID_ROOT) {
        flashError(R.string.msg_privileged_ubuntu_requires_root)
        return
      }
    }
    when (ShizukuTerminal.status(activity)) {
      ShizukuTerminal.Status.NOT_INSTALLED -> showBlocker(
        R.string.msg_shizuku_not_installed, R.string.action_shizuku_download
      ) { ShizukuTerminal.openDownloadPage(activity) }

      ShizukuTerminal.Status.NOT_RUNNING -> showBlocker(
        R.string.msg_shizuku_not_running, R.string.action_shizuku_open
      ) { ShizukuTerminal.openManager(activity) }

      ShizukuTerminal.Status.UNSUPPORTED_VERSION -> flashError(R.string.msg_shizuku_unsupported)

      ShizukuTerminal.Status.PERMISSION_DENIED -> showBlocker(
        R.string.msg_shizuku_permission_denied, R.string.action_shizuku_open
      ) { ShizukuTerminal.openManager(activity) }

      ShizukuTerminal.Status.PERMISSION_REQUIRED -> ShizukuTerminal.requestPermission { granted ->
        if (granted) {
          connectAndOpen(mode, sessionName, workingDirectory)
        } else {
          flashError(R.string.msg_shizuku_permission_not_granted)
        }
      }

      ShizukuTerminal.Status.READY -> connectAndOpen(mode, sessionName, workingDirectory)
    }
  }

  private fun connectAndOpen(mode: PrivilegedSessionMode, sessionName: String?, workingDirectory: String?) {
    ShizukuTerminal.connectService(activity) { service, error ->
      if (service == null) {
        flashError(activity.getString(R.string.msg_shizuku_service_failed, error ?: ""))
        return@connectService
      }
      if (activity.isFinishing || activity.isDestroyed) return@connectService
      createSession(service, mode, sessionName, workingDirectory)
    }
  }

  private fun createSession(
    service: IPrivilegedPtyService,
    mode: PrivilegedSessionMode,
    sessionName: String?,
    workingDirectory: String?
  ) {
    val termuxService = activity.termuxService ?: return
    val uid = runCatching { service.uid }.getOrElse { ShizukuTerminal.privilegeUid() ?: ShizukuTerminal.UID_SHELL }
    val identity = activity.getString(
      if (uid == ShizukuTerminal.UID_ROOT) R.string.privileged_session_name_root
      else R.string.privileged_session_name_shell
    )
    val name = sessionName ?: activity.getString(
      when (mode) {
        PrivilegedSessionMode.ANDROID -> R.string.privileged_session_name_android
        PrivilegedSessionMode.UBUNTU -> R.string.privileged_session_name_ubuntu
      },
      identity
    )
    val command = when (mode) {
      PrivilegedSessionMode.ANDROID -> ExecutionCommand(
        TermuxShellManager.getNextShellId(),
        PrivilegedShellEnvironment.SYSTEM_SHELL,
        null,
        null,
        PrivilegedShellEnvironment.sanitizeWorkingDirectory(
          uid, workingDirectory ?: activity.currentSession?.cwd
        ),
        Runner.TERMINAL_SESSION.runnerName,
        true
      )

      PrivilegedSessionMode.UBUNTU -> {
        ProotConfig.registerAndroidIds(activity)
        ProotConfig.writeShellProfile(activity)
        ExecutionCommand(
          TermuxShellManager.getNextShellId(),
          ProotConfig.prootBinary(activity),
          ProotConfig.prootArgs(activity).drop(1).toTypedArray(),
          null,
          PrivilegedShellEnvironment.home(uid),
          Runner.TERMINAL_SESSION.runnerName,
          true
        )
      }
    }
    command.shellName = name
    val session = termuxService.createTermuxSession(
      command,
      PrivilegedShellEnvironment(uid),
      ShizukuTerminalProcessLauncher(activity, service)
    ) ?: return
    activity.termuxTerminalSessionClient.setCurrentSession(session.terminalSession)
    activity.drawer.closeDrawers()
  }

  private fun showBlocker(messageRes: Int, actionRes: Int, action: () -> Unit) {
    DialogUtils.newMaterialDialogBuilder(activity)
      .setTitle(R.string.title_privileged_session)
      .setMessage(messageRes)
      .setPositiveButton(actionRes) { _, _ -> action() }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
