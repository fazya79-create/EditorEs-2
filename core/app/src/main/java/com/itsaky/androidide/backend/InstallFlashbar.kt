package com.itsaky.androidide.backend

import android.app.Activity
import com.blankj.utilcode.util.ThreadUtils
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
      val view = flashbar.flashbarView
      when (phase) {
        is InstallPhase.Downloading -> {
          view.setDeterminateProgress(phase.percent)
          view.setMessage(
            "Downloading ${phase.percent}% (${"%.1f".format(phase.receivedMb)}/${"%.1f".format(phase.totalMb)} MB)")
        }
        is InstallPhase.Extracting -> {
          view.setIndeterminateProgress()
          view.setMessage("Extracting ${phase.count} files…")
        }
        is InstallPhase.Finalizing -> {
          view.setIndeterminateProgress()
          view.setMessage("Finalizing…")
        }
        is InstallPhase.Done -> {
          dismiss()
          host.flashSuccess("Installation finished")
        }
        is InstallPhase.Failed -> {
          dismiss()
          host.flashError(phase.message)
        }
        else -> Unit
      }
    }
  }

  fun dismiss() {
    ThreadUtils.runOnUiThread {
      flashbar.dismiss()
    }
  }
}
