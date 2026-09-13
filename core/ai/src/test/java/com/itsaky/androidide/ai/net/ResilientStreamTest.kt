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
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ResilientStreamTest {

  private fun drop(producedOutput: Boolean = false) =
    StreamAttemptFailure(IOException("connection reset"), producedOutput)

  @Test
  fun `a dropped stream is replayed until it succeeds`() {
    runBlocking {
    var attempts = 0
    val notices = mutableListOf<ReconnectNotice>()

    ResilientStream.run(
      isOnline = { true },
      awaitOnline = { true },
      onReconnect = { notices += it }
    ) {
      attempts++
      if (attempts < 3) {
        throw drop()
      }
    }

    assertThat(attempts).isEqualTo(3)
    assertThat(notices).hasSize(2)
    assertThat(notices.map { it.offline }).containsExactly(false, false)
    }
  }

  @Test
  fun `retries stop once the attempt budget is spent`() {
    runBlocking {
    var attempts = 0

    val error = runCatching {
      ResilientStream.run(
        isOnline = { true },
        awaitOnline = { true },
        onReconnect = {}
      ) {
        attempts++
        throw drop()
      }
    }.exceptionOrNull()

    assertThat(error).isInstanceOf(StreamAttemptFailure::class.java)
    assertThat(attempts).isEqualTo(RetryPolicy.MAX_ATTEMPTS)
    }
  }

  @Test
  fun `a non-retryable failure is surfaced immediately`() {
    runBlocking {
    var attempts = 0

    val error = runCatching {
      ResilientStream.run(
        isOnline = { true },
        awaitOnline = { true },
        onReconnect = {}
      ) {
        attempts++
        throw StreamAttemptFailure(HttpStatusException(401, "bad key"), false)
      }
    }.exceptionOrNull()

    assertThat(error).isInstanceOf(StreamAttemptFailure::class.java)
    assertThat(attempts).isEqualTo(1)
    }
  }

  @Test
  fun `losing the network waits for it instead of spending a retry`() {
    runBlocking {
    var attempts = 0
    var online = false
    var waited = 0
    val notices = mutableListOf<ReconnectNotice>()

    ResilientStream.run(
      isOnline = { online },
      awaitOnline = {
        waited++
        online = true
        true
      },
      onReconnect = { notices += it }
    ) {
      attempts++
      if (attempts == 1) {
        throw drop()
      }
    }

    assertThat(waited).isEqualTo(1)
    assertThat(attempts).isEqualTo(2)
    assertThat(notices.single().offline).isTrue()
    }
  }

  @Test
  fun `an offline stretch does not consume the retry budget`() {
    runBlocking {
      var attempts = 0
      val offlineNotices = mutableListOf<ReconnectNotice>()

      ResilientStream.run(
        isOnline = { false },
        awaitOnline = { true },
        onReconnect = { notice ->
          if (notice.offline) {
            offlineNotices += notice
          }
        }
      ) {
        attempts++
        if (attempts <= RetryPolicy.MAX_ATTEMPTS + 2) {
          throw drop()
        }
      }

      assertThat(attempts).isEqualTo(RetryPolicy.MAX_ATTEMPTS + 3)
      assertThat(offlineNotices).hasSize(RetryPolicy.MAX_ATTEMPTS + 2)
      assertThat(offlineNotices.map { it.attempt }.distinct()).containsExactly(1)
    }
  }

  @Test
  fun `giving up waiting for the network surfaces the failure`() {
    runBlocking {
    val error = runCatching {
      ResilientStream.run(
        isOnline = { false },
        awaitOnline = { false },
        onReconnect = {}
      ) {
        throw drop()
      }
    }.exceptionOrNull()

    assertThat(error).isInstanceOf(StreamAttemptFailure::class.java)
    }
  }
}
