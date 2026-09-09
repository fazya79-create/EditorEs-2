package com.itsaky.androidide.backend

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ThreadUtils
import com.itsaky.androidide.backend.build.ToolchainPhase
import com.itsaky.androidide.backend.proot.InstallPhase
import com.itsaky.androidide.flashbar.Flashbar
import com.itsaky.androidide.flashbar.Flashbar.Gravity.TOP
import com.itsaky.androidide.flashbar.Flashbar.ProgressPosition.LEFT
import com.itsaky.androidide.flashbar.FlashbarView
import com.itsaky.androidide.utils.DURATION_INDEFINITE
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.itsaky.androidide.utils.flashbarBuilder
import com.itsaky.androidide.utils.showOnUiThread

class InstallFlashbar(context: Context, private val title: String) {

  private val app: Application = (context.applicationContext as? Application)
    ?: (ActivityUtils.getTopActivity()?.applicationContext as? Application)
    ?: throw IllegalStateException("Application context unavailable")

  private var currentActivity: Activity? = null
  private var currentFlashbar: Flashbar? = null
  private var isFinished: Boolean = false
  private var lastStateAction: ((FlashbarView) -> Unit)? = null

  private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
      attachTo(activity)
    }
    override fun onActivityPaused(activity: Activity) {
      if (currentActivity == activity) {
        detach()
      }
    }
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
      if (currentActivity == activity) {
        detach()
      }
    }
  }

  init {
    val setup = Runnable {
      app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
      val initial = (context as? Activity) ?: ActivityUtils.getTopActivity()
      initial?.let {
        if (!it.isFinishing && !it.isDestroyed) {
          attachTo(it)
        }
      }
    }
    if (ThreadUtils.isMainThread()) {
      setup.run()
    } else {
      ThreadUtils.runOnUiThread(setup)
    }
  }

  fun update(phase: InstallPhase) {
    ThreadUtils.runOnUiThread {
      when (phase) {
        is InstallPhase.Downloading -> downloading(phase.percent, phase.receivedMb, phase.totalMb)
        is InstallPhase.Extracting -> extracting(phase.count)
        is InstallPhase.Finalizing -> finalizing()
        is InstallPhase.Done -> done()
        is InstallPhase.Failed -> failed(phase.message)
        else -> Unit
      }
    }
  }

  fun update(phase: ToolchainPhase) {
    ThreadUtils.runOnUiThread {
      when (phase) {
        is ToolchainPhase.Downloading -> downloading(phase.percent, phase.receivedMb, phase.totalMb)
        is ToolchainPhase.Retrying -> retrying(phase.attempt, phase.reason, phase.receivedMb)
        is ToolchainPhase.Extracting -> extracting(phase.count)
        is ToolchainPhase.Done -> done()
        is ToolchainPhase.Failed -> failed(phase.message)
        is ToolchainPhase.Cancelled -> dismiss()
        is ToolchainPhase.Idle -> Unit
      }
    }
  }

  fun downloading(percent: Int, receivedMb: Double, totalMb: Double) {
    val action: (FlashbarView) -> Unit = { view ->
      view.setDeterminateProgress(percent)
      view.setMessage(
        "Downloading $percent% (${"%.1f".format(receivedMb)}/${"%.1f".format(totalMb)} MB)")
    }
    lastStateAction = action
    currentFlashbar?.flashbarView?.let(action)
  }

  fun extracting(count: Int) {
    val action: (FlashbarView) -> Unit = { view ->
      view.setIndeterminateProgress()
      view.setMessage("Extracting $count files…")
    }
    lastStateAction = action
    currentFlashbar?.flashbarView?.let(action)
  }

  fun finalizing() {
    val action: (FlashbarView) -> Unit = { view ->
      view.setIndeterminateProgress()
      view.setMessage("Finalizing…")
    }
    lastStateAction = action
    currentFlashbar?.flashbarView?.let(action)
  }

  fun retrying(attempt: Int, reason: String, receivedMb: Double) {
    val action: (FlashbarView) -> Unit = { view ->
      view.setIndeterminateProgress()
      view.setMessage("Retry #$attempt (${"%.1f".format(receivedMb)} MB): $reason")
    }
    lastStateAction = action
    currentFlashbar?.flashbarView?.let(action)
  }

  fun done() {
    ThreadUtils.runOnUiThread {
      if (isFinished) return@runOnUiThread
      isFinished = true
      runCatching { app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks) }
      detach()
      val top = ActivityUtils.getTopActivity() ?: currentActivity
      top?.flashSuccess("Installation finished")
    }
  }

  fun failed(message: String) {
    ThreadUtils.runOnUiThread {
      if (isFinished) return@runOnUiThread
      isFinished = true
      runCatching { app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks) }
      detach()
      val top = ActivityUtils.getTopActivity() ?: currentActivity
      top?.flashError(message)
    }
  }

  fun dismiss() {
    ThreadUtils.runOnUiThread {
      if (isFinished) return@runOnUiThread
      isFinished = true
      runCatching { app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks) }
      detach()
    }
  }

  private fun attachTo(activity: Activity) {
    if (isFinished || activity.isFinishing || activity.isDestroyed) return
    if (activity::class.java.simpleName == "CrashHandlerActivity") return
    if (currentActivity == activity && currentFlashbar != null) return
    detach()
    currentActivity = activity
    val bar = activity.flashbarBuilder(gravity = TOP, duration = DURATION_INDEFINITE)
      .title(title)
      .message("Starting…")
      .showProgress(LEFT)
      .build()
    currentFlashbar = bar
    bar.showOnUiThread()
    lastStateAction?.let { action ->
      bar.flashbarView.let(action)
    }
  }

  private fun detach() {
    runCatching { currentFlashbar?.dismiss() }
    currentFlashbar = null
    currentActivity = null
  }
}
