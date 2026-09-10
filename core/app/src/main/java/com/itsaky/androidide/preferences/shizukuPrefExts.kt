package com.itsaky.androidide.preferences

import android.content.Context
import androidx.preference.Preference
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.terminal.shizuku.ShizukuManager
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.parcelize.Parcelize

@Parcelize
class ShizukuPreference(
  override val key: String = "idepref_shizuku",
  override val title: Int = string.idepref_shizuku_title,
) : SimplePreference() {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(context, it) }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    if (!ShizukuManager.isShizukuInstalled(context)) {
      flashError(string.idepref_shizuku_summary_not_installed)
      return true
    }
    if (!ShizukuManager.isShizukuRunning()) {
      flashError(string.idepref_shizuku_summary_not_running)
      return true
    }
    if (ShizukuManager.isPermissionGranted()) {
      val uid = ShizukuManager.getUid()
      val privilege = if (uid == 0) "root (0)" else "ADB shell ($uid)"
      flashSuccess(context.getString(string.idepref_shizuku_summary_authorized, privilege))
      return true
    }
    ShizukuManager.requestPermission { granted ->
      updateSummary(context, preference)
      if (granted) {
        val uid = ShizukuManager.getUid()
        val privilege = if (uid == 0) "root (0)" else "ADB shell ($uid)"
        flashSuccess(context.getString(string.idepref_shizuku_summary_authorized, privilege))
      } else {
        flashError(com.termux.R.string.shizuku_permission_denied)
      }
    }
    return true
  }

  private fun updateSummary(context: Context, preference: Preference) {
    if (!ShizukuManager.isShizukuInstalled(context)) {
      preference.summary = context.getString(string.idepref_shizuku_summary_not_installed)
      return
    }
    if (!ShizukuManager.isShizukuRunning()) {
      preference.summary = context.getString(string.idepref_shizuku_summary_not_running)
      return
    }
    if (ShizukuManager.isPermissionGranted()) {
      val uid = ShizukuManager.getUid()
      val privilege = if (uid == 0) "root (0)" else "ADB shell ($uid)"
      preference.summary = context.getString(string.idepref_shizuku_summary_authorized, privilege)
    } else {
      preference.summary = context.getString(string.idepref_shizuku_summary_permission_required)
    }
  }
}
