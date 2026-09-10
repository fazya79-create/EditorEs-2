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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.pm.PackageInfoCompat
import com.termux.shared.logger.Logger
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui
import java.util.concurrent.CopyOnWriteArrayList

object ShizukuTerminal {

  private const val LOG_TAG = "ShizukuTerminal"
  private const val PERMISSION_REQUEST_CODE = 0x5A1
  private const val USER_SERVICE_TAG = "ide.terminal.privileged-pty"
  private const val USER_SERVICE_PROCESS_SUFFIX = "privileged_pty"
  private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
  private const val BIND_TIMEOUT_MS = 35_000L

  const val UID_ROOT = 0
  const val UID_SHELL = 2000

  enum class Status {
    NOT_INSTALLED,
    NOT_RUNNING,
    UNSUPPORTED_VERSION,
    PERMISSION_DENIED,
    PERMISSION_REQUIRED,
    READY
  }

  fun interface PermissionCallback {
    fun onResult(granted: Boolean)
  }

  fun interface ServiceCallback {
    fun onResult(service: IPrivilegedPtyService?, error: String?)
  }

  private val processes = CopyOnWriteArrayList<ShizukuTerminalProcess>()
  private val pendingPermission = CopyOnWriteArrayList<PermissionCallback>()
  private val pendingService = CopyOnWriteArrayList<ServiceCallback>()

  @Volatile
  private var service: IPrivilegedPtyService? = null

  @Volatile
  private var binding = false

  private var listenersRegistered = false

  private val mainHandler = Handler(Looper.getMainLooper())

  private val bindTimeout = Runnable {
    if (binding) {
      Logger.logWarn(LOG_TAG, "Timed out waiting for privileged pty service")
      onServiceLost("Timed out waiting for the privileged service")
    }
  }

  private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
    if (requestCode != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
    val granted = grantResult == PackageManager.PERMISSION_GRANTED
    val callbacks = pendingPermission.toList()
    pendingPermission.clear()
    callbacks.forEach { it.onResult(granted) }
  }

  private val binderDeadListener = Shizuku.OnBinderDeadListener {
    Logger.logWarn(LOG_TAG, "Shizuku binder died")
    onServiceLost("Shizuku stopped")
  }

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      mainHandler.removeCallbacks(bindTimeout)
      binding = false
      if (binder == null || !binder.pingBinder()) {
        onServiceLost("Privileged service binder is invalid")
        return
      }
      val remote = IPrivilegedPtyService.Stub.asInterface(binder)
      service = remote
      Logger.logInfo(LOG_TAG, "Privileged pty service connected")
      val callbacks = pendingService.toList()
      pendingService.clear()
      callbacks.forEach { it.onResult(remote, null) }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      Logger.logWarn(LOG_TAG, "Privileged pty service disconnected")
      onServiceLost("Privileged service died")
    }
  }

  private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
    return Shizuku.UserServiceArgs(ComponentName(context.packageName, PrivilegedPtyService::class.java.name))
      .daemon(false)
      .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
      .tag(USER_SERVICE_TAG)
      .version(versionCode)
  }

  @Synchronized
  private fun ensureListeners() {
    if (listenersRegistered) return
    Shizuku.addRequestPermissionResultListener(permissionListener)
    Shizuku.addBinderDeadListener(binderDeadListener)
    listenersRegistered = true
  }

  fun isManagerInstalled(context: Context): Boolean {
    if (Sui.isSui()) return true
    return try {
      context.packageManager.getPackageInfo(ShizukuProvider.MANAGER_APPLICATION_ID, 0)
      true
    } catch (e: PackageManager.NameNotFoundException) {
      false
    }
  }

  fun status(context: Context): Status {
    if (!Shizuku.pingBinder()) {
      return if (isManagerInstalled(context)) Status.NOT_RUNNING else Status.NOT_INSTALLED
    }
    if (Shizuku.isPreV11()) return Status.UNSUPPORTED_VERSION
    return try {
      when {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
        Shizuku.shouldShowRequestPermissionRationale() -> Status.PERMISSION_DENIED
        else -> Status.PERMISSION_REQUIRED
      }
    } catch (e: Exception) {
      Logger.logStackTraceWithMessage(LOG_TAG, "Failed to query Shizuku permission", e)
      Status.NOT_RUNNING
    }
  }

  fun isReady(context: Context): Boolean = status(context) == Status.READY

  fun privilegeUid(): Int? {
    if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return null
    return try {
      Shizuku.getUid().takeIf { it >= 0 }
    } catch (e: Exception) {
      null
    }
  }

  fun requestPermission(callback: PermissionCallback) {
    if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
      callback.onResult(false)
      return
    }
    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
      callback.onResult(true)
      return
    }
    if (Shizuku.shouldShowRequestPermissionRationale()) {
      callback.onResult(false)
      return
    }
    ensureListeners()
    pendingPermission.add(callback)
    try {
      Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    } catch (e: Exception) {
      Logger.logStackTraceWithMessage(LOG_TAG, "Failed to request Shizuku permission", e)
      pendingPermission.remove(callback)
      callback.onResult(false)
    }
  }

  fun connectService(context: Context, callback: ServiceCallback) {
    val current = service
    if (current != null && current.asBinder().pingBinder()) {
      callback.onResult(current, null)
      return
    }
    service = null
    if (!isReady(context)) {
      callback.onResult(null, "Shizuku is not available")
      return
    }
    ensureListeners()
    pendingService.add(callback)
    if (binding) return
    binding = true
    mainHandler.postDelayed(bindTimeout, BIND_TIMEOUT_MS)
    try {
      Shizuku.bindUserService(userServiceArgs(context), connection)
    } catch (e: Exception) {
      Logger.logStackTraceWithMessage(LOG_TAG, "Failed to bind privileged pty service", e)
      binding = false
      onServiceLost(e.message ?: e.javaClass.simpleName)
    }
  }

  fun peekService(): IPrivilegedPtyService? {
    val current = service ?: return null
    return if (current.asBinder().pingBinder()) current else null
  }

  fun shutdown(context: Context) {
    val hadService = service != null
    service = null
    binding = false
    if (!Shizuku.pingBinder()) return
    try {
      Shizuku.unbindUserService(userServiceArgs(context), connection, hadService)
    } catch (e: Exception) {
      Logger.logWarn(LOG_TAG, "Failed to unbind privileged pty service: ${e.message}")
    }
  }

  fun openDownloadPage(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun openManager(context: Context): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(ShizukuProvider.MANAGER_APPLICATION_ID)
      ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
  }

  internal fun onProcessStarted(process: ShizukuTerminalProcess) {
    processes.add(process)
  }

  internal fun onProcessClosed(process: ShizukuTerminalProcess) {
    processes.remove(process)
  }

  fun hasLiveProcesses(): Boolean = processes.isNotEmpty()

  private fun onServiceLost(reason: String) {
    mainHandler.removeCallbacks(bindTimeout)
    service = null
    binding = false
    val callbacks = pendingService.toList()
    pendingService.clear()
    callbacks.forEach { it.onResult(null, reason) }
  }
}
