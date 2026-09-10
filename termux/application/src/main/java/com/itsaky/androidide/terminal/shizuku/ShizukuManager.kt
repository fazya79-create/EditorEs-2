package com.itsaky.androidide.terminal.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.IOException

object ShizukuManager {

  private const val REQUEST_CODE = 12024
  private val mainHandler = Handler(Looper.getMainLooper())

  private var userService: IShizukuTerminalService? = null
  private var serviceArgs: Shizuku.UserServiceArgs? = null
  private val pendingCallbacks = mutableListOf<(IShizukuTerminalService?, Throwable?) -> Unit>()
  private var isConnecting = false

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      isConnecting = false
      val service = if (binder != null && binder.isBinderAlive) {
        IShizukuTerminalService.Stub.asInterface(binder)
      } else {
        null
      }
      userService = service
      val callbacks = ArrayList(pendingCallbacks)
      pendingCallbacks.clear()
      mainHandler.post {
        callbacks.forEach { it.invoke(service, null) }
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      isConnecting = false
      userService = null
      val callbacks = ArrayList(pendingCallbacks)
      pendingCallbacks.clear()
      mainHandler.post {
        callbacks.forEach { it.invoke(null, IOException("Service disconnected")) }
      }
    }
  }

  fun isShizukuInstalled(context: Context): Boolean {
    return runCatching {
      context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
      true
    }.getOrElse {
      isShizukuRunning()
    }
  }

  fun isShizukuRunning(): Boolean {
    return runCatching {
      Shizuku.pingBinder()
    }.getOrDefault(false)
  }

  fun isPermissionGranted(): Boolean {
    if (!isShizukuRunning()) return false
    return runCatching {
      Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
  }

  fun getUid(): Int {
    if (!isShizukuRunning()) return -1
    return runCatching { Shizuku.getUid() }.getOrDefault(-1)
  }

  fun requestPermission(onRequestResult: (Boolean) -> Unit) {
    if (!isShizukuRunning()) {
      onRequestResult(false)
      return
    }
    if (isPermissionGranted()) {
      onRequestResult(true)
      return
    }
    val listener = object : Shizuku.OnRequestPermissionResultListener {
      override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == REQUEST_CODE) {
          Shizuku.removeRequestPermissionResultListener(this)
          val granted = grantResult == PackageManager.PERMISSION_GRANTED
          mainHandler.post {
            onRequestResult(granted)
          }
        }
      }
    }
    Shizuku.addRequestPermissionResultListener(listener)
    try {
      Shizuku.requestPermission(REQUEST_CODE)
    } catch (t: Throwable) {
      Shizuku.removeRequestPermissionResultListener(listener)
      onRequestResult(false)
    }
  }

  fun getService(context: Context, callback: (IShizukuTerminalService?, Throwable?) -> Unit) {
    val current = userService
    if (current != null && current.asBinder().isBinderAlive) {
      callback(current, null)
      return
    }
    if (!isShizukuRunning() || !isPermissionGranted()) {
      callback(null, IllegalStateException("Shizuku is not running or permission is not granted"))
      return
    }
    pendingCallbacks.add(callback)
    if (isConnecting) {
      return
    }
    isConnecting = true
    try {
      val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
      val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuTerminalService::class.java.name)
      )
        .tag("shizuku_terminal_service")
        .daemon(false)
        .processNameSuffix("privileged_terminal")
        .debuggable(isDebug)
        .version(1)
      serviceArgs = args
      Shizuku.bindUserService(args, serviceConnection)
    } catch (t: Throwable) {
      isConnecting = false
      val callbacks = ArrayList(pendingCallbacks)
      pendingCallbacks.clear()
      callbacks.forEach { it.invoke(null, t) }
    }
  }

  fun unbindService() {
    val args = serviceArgs ?: return
    runCatching {
      userService?.destroy()
    }
    runCatching {
      Shizuku.unbindUserService(args, serviceConnection, true)
    }
    userService = null
    serviceArgs = null
    isConnecting = false
  }
}
