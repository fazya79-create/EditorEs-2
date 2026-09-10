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
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.shell.command.environment.UnixShellEnvironment
import java.io.File

class PrivilegedShellEnvironment(private val uid: Int) : UnixShellEnvironment() {

  companion object {
    const val SYSTEM_SHELL = "/system/bin/sh"
    const val SYSTEM_BIN = "/system/bin"
    const val SHELL_TMP_DIR = "/data/local/tmp"
    private const val DEFAULT_PATH =
      "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"

    fun home(uid: Int): String = if (uid == ShizukuTerminal.UID_ROOT) "/data/local/tmp" else SHELL_TMP_DIR

    fun sanitizeWorkingDirectory(uid: Int, cwd: String?): String {
      if (cwd.isNullOrEmpty()) return home(uid)
      if (uid == ShizukuTerminal.UID_ROOT) return cwd
      val allowed = cwd == "/" ||
        cwd.startsWith("/storage/") || cwd == "/storage" ||
        cwd.startsWith("/sdcard/") || cwd == "/sdcard" ||
        cwd.startsWith("$SHELL_TMP_DIR/") || cwd == SHELL_TMP_DIR
      return if (allowed) cwd else home(uid)
    }
  }

  override fun getEnvironment(currentPackageContext: Context, isFailSafe: Boolean): HashMap<String, String> {
    val environment = HashMap<String, String>()
    environment[ENV_HOME] = home(uid)
    environment[ENV_LANG] = "en_US.UTF-8"
    environment[ENV_PATH] = System.getenv(ENV_PATH) ?: DEFAULT_PATH
    environment[ENV_TMPDIR] = SHELL_TMP_DIR
    environment[ENV_COLORTERM] = "truecolor"
    environment[ENV_TERM] = "xterm-256color"

    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_ASSETS")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_DATA")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_ROOT")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_STORAGE")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "EXTERNAL_STORAGE")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_RUNTIME_ROOT")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_ART_ROOT")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_I18N_ROOT")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_TZDATA_ROOT")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "BOOTCLASSPATH")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "DEX2OATBOOTCLASSPATH")
    ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "SYSTEMSERVERCLASSPATH")
    return environment
  }

  override fun getDefaultWorkingDirectoryPath(): String = home(uid)

  override fun getDefaultBinPath(): String = SYSTEM_BIN

  override fun setupShellCommandEnvironment(
    currentPackageContext: Context,
    executionCommand: ExecutionCommand
  ): HashMap<String, String> {
    val environment = getEnvironment(currentPackageContext, executionCommand.isFailsafe)
    val workingDirectory = executionCommand.workingDirectory
    environment[ENV_PWD] = if (!workingDirectory.isNullOrEmpty()) {
      File(workingDirectory).absolutePath
    } else {
      getDefaultWorkingDirectoryPath()
    }
    return environment
  }
}
