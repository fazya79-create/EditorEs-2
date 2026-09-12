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

package com.itsaky.androidide.ai.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelFilterTest {

  private val models = listOf(
    ModelInfo("gpt-4o-mini"),
    ModelInfo("gpt-4.1"),
    ModelInfo("claude-sonnet-4-5"),
    ModelInfo("gemini-2.5-flash"),
    ModelInfo("anthropic/claude-haiku-4-5")
  )

  @Test
  fun `a blank query keeps every model in its original order`() {
    assertThat(ModelFilter.filter(models, "")).isEqualTo(models)
    assertThat(ModelFilter.filter(models, "   ")).isEqualTo(models)
  }

  @Test
  fun `matching is case insensitive and matches anywhere in the id`() {
    assertThat(ModelFilter.filter(models, "CLAUDE").map { it.id })
      .containsExactly("claude-sonnet-4-5", "anthropic/claude-haiku-4-5")
      .inOrder()

    assertThat(ModelFilter.filter(models, "flash").map { it.id })
      .containsExactly("gemini-2.5-flash")
  }

  @Test
  fun `every term must match but the order does not matter`() {
    assertThat(ModelFilter.filter(models, "4 gpt").map { it.id })
      .containsExactly("gpt-4o-mini", "gpt-4.1")
      .inOrder()

    assertThat(ModelFilter.filter(models, "claude haiku").map { it.id })
      .containsExactly("anthropic/claude-haiku-4-5")
  }

  @Test
  fun `a query that matches nothing yields an empty list`() {
    assertThat(ModelFilter.filter(models, "llama")).isEmpty()
  }
}
