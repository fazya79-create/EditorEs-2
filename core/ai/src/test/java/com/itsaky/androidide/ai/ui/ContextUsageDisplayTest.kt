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

package com.itsaky.androidide.ai.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContextUsageDisplayTest {

  @Test
  fun `a nearly full context window reports a high percentage`() {
    val usage = ContextUsage(used = 800, window = 1_000)

    assertThat(usage.percent).isEqualTo(80)
  }

  @Test
  fun `a reading that overshoots the window is clamped instead of exceeding 100`() {
    val usage = ContextUsage(used = 2_000, window = 1_000)

    assertThat(usage.percent).isEqualTo(100)
  }

  @Test
  fun `the cached share is reported relative to what was used`() {
    val usage = ContextUsage(used = 800, window = 1_000, cached = 400)

    assertThat(usage.cachedPercent).isEqualTo(50)
  }

  @Test
  fun `an unknown window reports zero rather than dividing by it`() {
    val usage = ContextUsage(used = 800, window = 0)

    assertThat(usage.percent).isEqualTo(0)
    assertThat(usage.cachedPercent).isEqualTo(0)
  }
}
