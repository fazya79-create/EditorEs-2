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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class StreamAttemptFailure(val error: Throwable, val producedOutput: Boolean) :
  RuntimeException(error.message, error)

data class ReconnectNotice(
  val attempt: Int,
  val maxAttempts: Int,
  val delayMillis: Long,
  val offline: Boolean,
  val reason: String
)

object ResilientStream {

  const val OFFLINE_WAIT_MS = 120_000L

  // A dropped stream is replayed as a whole request rather than resumed mid-token: no
  // provider exposes a cursor to restart from, and a half-delivered message is not
  // something the model can continue. Anything already shown is discarded on the retry so
  // the caller never renders the same sentence twice.
  suspend fun run(
    isOnline: () -> Boolean,
    awaitOnline: suspend (Long) -> Boolean,
    onReconnect: suspend (ReconnectNotice) -> Unit,
    attempt: suspend (attemptIndex: Int) -> Unit
  ) {
    var attemptIndex = 1
    while (true) {
      try {
        attempt(attemptIndex)
        return
      } catch (err: CancellationException) {
        throw err
      } catch (err: StreamAttemptFailure) {
        val offline = !isOnline()

        // Losing the network is not the user's mistake and not a permanent failure, so it
        // does not consume a retry: the turn waits for the radio instead of dying.
        if (!offline && (attemptIndex >= RetryPolicy.MAX_ATTEMPTS || !RetryPolicy.isRetryable(err.error))) {
          throw err
        }

        val wait = if (offline) 0L else RetryPolicy.delayMillis(
          attemptIndex,
          (err.error as? HttpStatusException)?.retryAfterSeconds
        )

        onReconnect(
          ReconnectNotice(
            attempt = attemptIndex,
            maxAttempts = RetryPolicy.MAX_ATTEMPTS,
            delayMillis = wait,
            offline = offline,
            reason = err.error.message.orEmpty()
          )
        )

        if (offline) {
          if (!awaitOnline(OFFLINE_WAIT_MS)) {
            throw err
          }
        } else {
          delay(wait)
          attemptIndex++
        }
      }
    }
  }
}
