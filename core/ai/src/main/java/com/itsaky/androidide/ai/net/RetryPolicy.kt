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

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.random.Random

object RetryPolicy {

  const val MAX_ATTEMPTS = 4
  const val INITIAL_DELAY_MS = 1_000L
  const val MAX_DELAY_MS = 30_000L

  private const val BACKOFF_FACTOR = 2.0
  private const val JITTER_RATIO = 0.25

  private val RETRYABLE_STATUS = setOf(408, 409, 425, 429, 500, 502, 503, 504, 522, 524)

  fun isRetryable(error: Throwable): Boolean = when (error) {
    is HttpStatusException -> error.status in RETRYABLE_STATUS
    is SocketTimeoutException -> true
    is UnknownHostException -> false
    is IOException -> true
    else -> false
  }

  // Jitter keeps several sessions that hit the same overloaded gateway from retrying in
  // lockstep. A server-supplied Retry-After always wins over the computed delay.
  fun delayMillis(
    attempt: Int,
    retryAfterSeconds: Double? = null,
    random: Double = Random.nextDouble()
  ): Long {
    if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
      return min((retryAfterSeconds * 1000).toLong(), MAX_DELAY_MS)
    }
    val base = INITIAL_DELAY_MS * Math.pow(BACKOFF_FACTOR, (attempt - 1).coerceAtLeast(0).toDouble())
    val capped = min(base, MAX_DELAY_MS.toDouble())
    return (capped + capped * JITTER_RATIO * random).toLong().coerceAtMost(MAX_DELAY_MS)
  }

  fun retryAfterSeconds(value: String?): Double? {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) {
      return null
    }
    return text.toDoubleOrNull()?.coerceAtLeast(0.0)
  }
}
