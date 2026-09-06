package com.itsaky.androidide.backend

import android.app.Activity
import com.blankj.utilcode.util.ThreadUtils
import com.itsaky.androidide.backend.build.ToolchainPhase
import com.itsaky.androidide.backend.proot.InstallPhase
import com.itsaky.androidide.flashbar.Flashbar
import com.itsaky.androidide.flashbar.Flashbar.Gravity.TOP
import com.itsaky.androidide.flashbar.Flashbar.ProgressPosition.LEFT
import com.itsaky.androidide.utils.DURATION_INDEFINITE
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.itsaky.androidide.utils.flashbarBuilder
import com.itsaky.androidide.utils.showOnUiThread

class InstallFlashbar(activity: Activity, title: String) {

  private val host: Activity = activity
  private val flashbar: Flashbar = activity
    .flashbarBuilder(gravity = TOP, duration = DURATION_INDEFINITE)
    .title(title)
    .message("Starting…")
    .showProgress(LEFT)
    .build()
    .also { it.showOnUiThread() }

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
      }
    }
  }

  fun downloading(percent: Int, receivedMb: Double, totalMb: Double) {
    val view = flashbar.flashbarView
    view.setDeterminateProgress(percent)
    view.setMessage(
      "Downloading $percent% (${"%.1f".format(receivedMb)}/${"%.1f".format(totalMb)} MB)")
  }

  fun extracting(count: Int) {
    val view = flashbar.flashbarView
    view.setIndeterminateProgress()
    view.setMessage("Extracting $count files…")
  }

  fun finalizing() {
    val view = flashbar.flashbarView
    view.setIndeterminateProgress()
    view.setMessage("Finalizing…")
  }

  fun retrying(attempt: Int, reason: String, receivedMb: Double) {
    val view = flashbar.flashbarView
    view.setIndeterminateProgress()
    view.setMessage("Retry #$attempt (${"%.1f".format(receivedMb)} MB): $reason")
  }

  fun done() {
    dismiss()
    host.flashSuccess("Installation finished")
  }

  fun failed(message: String) {
    dismiss()
    host.flashError(message)
  }

  fun dismiss() {
    ThreadUtils.runOnUiThread {
      flashbar.dismiss()
    }
  }
}
