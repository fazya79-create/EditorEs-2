package com.itsaky.androidide.backend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.itsaky.androidide.R

class BackendInstallNotifier(private val context: Context) {

  private val manager: NotificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  init {
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_backend_channel),
        NotificationManager.IMPORTANCE_DEFAULT)
    )
  }

  fun showProgress(percent: Int, text: String) {
    manager.notify(NOTIFICATION_ID, baseBuilder()
      .setContentText(text)
      .setProgress(100, percent, false)
      .setOngoing(true)
      .build())
  }

  fun showIndeterminate(text: String) {
    manager.notify(NOTIFICATION_ID, baseBuilder()
      .setContentText(text)
      .setProgress(0, 0, true)
      .setOngoing(true)
      .build())
  }

  fun showDone() {
    manager.notify(NOTIFICATION_ID, baseBuilder()
      .setContentText(context.getString(R.string.notif_backend_done))
      .setProgress(0, 0, false)
      .setOngoing(false)
      .setAutoCancel(true)
      .build())
  }

  fun showFailed(message: String) {
    manager.notify(NOTIFICATION_ID, baseBuilder()
      .setContentText(message)
      .setProgress(0, 0, false)
      .setOngoing(false)
      .setAutoCancel(true)
      .build())
  }

  fun cancel() {
    manager.cancel(NOTIFICATION_ID)
  }

  private fun baseBuilder(): NotificationCompat.Builder {
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(context.getString(R.string.notif_backend_title))
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setOnlyAlertOnce(true)
  }

  companion object {
    const val CHANNEL_ID = "ide_backend_install"
    const val NOTIFICATION_ID = 0xbeac0
  }
}
