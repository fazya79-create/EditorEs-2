package com.itsaky.androidide.fragments.onboarding

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.activities.OnboardingActivity
import com.itsaky.androidide.backend.proot.InstallPhase
import com.itsaky.androidide.backend.proot.ProotConfig
import com.itsaky.androidide.backend.proot.UbuntuInstaller
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackendInstallFragment : Fragment() {

  private lateinit var statusView: TextView
  private lateinit var installButton: Button

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    val layout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(64, 64, 64, 64)
    }
    val titleView = TextView(context).apply {
      text = "Ubuntu is required"
      textSize = 22f
      gravity = Gravity.CENTER
    }
    statusView = TextView(context).apply {
      text = "Press Install to download and set up the Ubuntu environment."
      gravity = Gravity.CENTER
    }
    installButton = Button(context).apply {
      text = "Install"
      setOnClickListener { startInstall() }
    }
    layout.addView(titleView)
    layout.addView(statusView)
    layout.addView(installButton)
    return layout
  }

  override fun onResume() {
    super.onResume()
    if (ProotConfig.isInstalled(requireContext().applicationContext)) {
      (activity as? OnboardingActivity)?.tryNavigateToMainIfSetupIsCompleted()
    }
  }

  private fun startInstall() {
    installButton.isEnabled = false
    val appContext = requireContext().applicationContext
    lifecycleScope.launch {
      withContext(Dispatchers.IO) {
        UbuntuInstaller(appContext).install { phase ->
          val text = when (phase) {
            is InstallPhase.Downloading ->
              "Downloading ${phase.percent}% (${"%.1f".format(phase.receivedMb)}/${"%.1f".format(phase.totalMb)} MB)"
            is InstallPhase.Extracting -> "Extracting ${phase.count} files…"
            is InstallPhase.Finalizing -> "Finalizing…"
            is InstallPhase.Done -> "Installed."
            is InstallPhase.Failed -> "Failed: ${phase.message}"
            else -> null
          }
          text?.let {
            launch(Dispatchers.Main) {
              if (isAdded) {
                statusView.text = it
              }
            }
          }
        }
      }
      withContext(Dispatchers.Main) {
        if (!isAdded) {
          return@withContext
        }
        if (ProotConfig.isInstalled(appContext)) {
          flashSuccess("Installation finished")
          (activity as? OnboardingActivity)?.tryNavigateToMainIfSetupIsCompleted()
        } else {
          flashError("Installation failed")
          installButton.isEnabled = true
        }
      }
    }
  }
}
