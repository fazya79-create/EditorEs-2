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

package com.itsaky.androidide.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import org.slf4j.LoggerFactory

class AiStreamingService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopForegroundCompat()
      stopSelf()
      return START_NOT_STICKY
    }

    setupNotificationChannel()

    runCatching {
      ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,
        buildNotification(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
          0
        }
      )
    }.onFailure { err ->
      log.error("Failed to start the AI streaming foreground service", err)
      stopSelf()
    }

    return START_NOT_STICKY
  }

  private fun buildNotification() =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.title_ai_streaming_notification))
      .setContentText(getString(R.string.msg_ai_streaming_notification))
      .setSmallIcon(R.drawable.ic_ai_assistant)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(false)
      .setSilent(true)
      .build()

  private fun setupNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.title_ai_streaming_notification_channel),
      NotificationManager.IMPORTANCE_LOW
    )
    manager.createNotificationChannel(channel)
  }

  private fun stopForegroundCompat() {
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
  }

  companion object {

    private const val CHANNEL_ID = "ide.ai.streaming"
    private const val NOTIFICATION_ID = 5310
    private const val ACTION_STOP = "ide.ai.streaming.STOP"

    private val log = LoggerFactory.getLogger(AiStreamingService::class.java)

    fun start(context: Context) {
      val app = context.applicationContext
      runCatching {
        ContextCompat.startForegroundService(app, Intent(app, AiStreamingService::class.java))
      }.onFailure { err ->
        log.warn("Could not start the AI streaming foreground service", err)
      }
    }

    fun stop(context: Context) {
      val app = context.applicationContext
      runCatching {
        app.startService(
          Intent(app, AiStreamingService::class.java).setAction(ACTION_STOP)
        )
      }.onFailure { err ->
        log.warn("Could not stop the AI streaming foreground service", err)
      }
    }
  }
}
