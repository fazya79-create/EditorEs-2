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

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Test

class RetryPolicyTest {

  @Test
  fun `overload and rate limit responses are retried`() {
    listOf(429, 500, 502, 503, 504, 524).forEach { status ->
      assertThat(RetryPolicy.isRetryable(HttpStatusException(status, ""))).isTrue()
    }
  }

  @Test
  fun `client mistakes are not retried`() {
    listOf(400, 401, 403, 404, 422).forEach { status ->
      assertThat(RetryPolicy.isRetryable(HttpStatusException(status, ""))).isFalse()
    }
  }

  @Test
  fun `transient network faults are retried but an unknown host is not`() {
    assertThat(RetryPolicy.isRetryable(SocketTimeoutException("timeout"))).isTrue()
    assertThat(RetryPolicy.isRetryable(IOException("connection reset"))).isTrue()
    assertThat(RetryPolicy.isRetryable(UnknownHostException("nope"))).isFalse()
  }

  @Test
  fun `the delay grows with each attempt`() {
    val first = RetryPolicy.delayMillis(1, random = 0.0)
    val second = RetryPolicy.delayMillis(2, random = 0.0)
    val third = RetryPolicy.delayMillis(3, random = 0.0)

    assertThat(second).isGreaterThan(first)
    assertThat(third).isGreaterThan(second)
  }

  @Test
  fun `the delay is capped`() {
    assertThat(RetryPolicy.delayMillis(20, random = 1.0))
      .isAtMost(RetryPolicy.MAX_DELAY_MS)
  }

  @Test
  fun `jitter separates retries that would otherwise collide`() {
    val low = RetryPolicy.delayMillis(2, random = 0.0)
    val high = RetryPolicy.delayMillis(2, random = 1.0)

    assertThat(high).isGreaterThan(low)
  }

  @Test
  fun `a server supplied retry-after wins over the computed delay`() {
    assertThat(RetryPolicy.delayMillis(1, retryAfterSeconds = 5.0)).isEqualTo(5_000)
  }

  @Test
  fun `an oversized retry-after is still capped`() {
    assertThat(RetryPolicy.delayMillis(1, retryAfterSeconds = 9_000.0))
      .isEqualTo(RetryPolicy.MAX_DELAY_MS)
  }

  @Test
  fun `a retry-after header is parsed and a malformed one is ignored`() {
    assertThat(RetryPolicy.retryAfterSeconds("12")).isEqualTo(12.0)
    assertThat(RetryPolicy.retryAfterSeconds("  3.5 ")).isEqualTo(3.5)
    assertThat(RetryPolicy.retryAfterSeconds("soon")).isNull()
    assertThat(RetryPolicy.retryAfterSeconds(null)).isNull()
  }
}
