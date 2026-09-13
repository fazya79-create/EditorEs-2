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

package com.itsaky.androidide.ai.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object Connectivity {

  fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  // Waiting on a callback beats polling: the radio coming back wakes us immediately, and
  // a device left offline costs nothing until the timeout expires.
  suspend fun awaitOnline(context: Context, timeoutMillis: Long): Boolean {
    if (isOnline(context)) {
      return true
    }
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return true

    return withTimeoutOrNull(timeoutMillis) {
      suspendCancellableCoroutine { continuation ->
        val request = NetworkRequest.Builder()
          .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            if (continuation.isActive) {
              continuation.resume(true)
            }
          }
        }

        runCatching { manager.registerNetworkCallback(request, callback) }
          .onFailure {
            if (continuation.isActive) {
              continuation.resume(true)
            }
            return@suspendCancellableCoroutine
          }

        continuation.invokeOnCancellation {
          runCatching { manager.unregisterNetworkCallback(callback) }
        }
      }
    } ?: false
  }
}
