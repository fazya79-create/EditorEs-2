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

package com.itsaky.androidide.ai.skills

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class SkillMergeTest {

  private fun skill(name: String, source: SkillSource, description: String) = Skill(
    name = name,
    description = description,
    body = "body",
    directory = File("/tmp/$name"),
    source = source
  )

  @Test
  fun `a project skill shadows an installed and a bundled one of the same name`() {
    val merged = SkillRegistry.merge(
      project = SkillRegistry(
        mapOf("review" to skill("review", SkillSource.PROJECT, "from project"))
      ),
      installed = listOf(skill("review", SkillSource.INSTALLED, "from installed")),
      bundled = listOf(skill("review", SkillSource.BUILT_IN, "from bundle"))
    )

    val review = merged.find("review")
    assertThat(review?.description).isEqualTo("from project")
    assertThat(review?.source).isEqualTo(SkillSource.PROJECT)
  }

  @Test
  fun `an installed skill shadows a bundled one so a user can override what ships`() {
    val merged = SkillRegistry.merge(
      project = SkillRegistry(emptyMap()),
      installed = listOf(skill("review", SkillSource.INSTALLED, "mine")),
      bundled = listOf(skill("review", SkillSource.BUILT_IN, "shipped"))
    )

    assertThat(merged.find("review")?.description).isEqualTo("mine")
    assertThat(merged.find("review")?.source).isEqualTo(SkillSource.INSTALLED)
  }

  @Test
  fun `skills from every source appear when their names do not collide`() {
    val merged = SkillRegistry.merge(
      project = SkillRegistry(mapOf("p" to skill("p", SkillSource.PROJECT, "d"))),
      installed = listOf(skill("i", SkillSource.INSTALLED, "d")),
      bundled = listOf(skill("b", SkillSource.BUILT_IN, "d"))
    )

    assertThat(merged.all().map { it.name }).containsExactly("b", "i", "p")
  }

  @Test
  fun `only an installed skill reports itself as deletable`() {
    assertThat(SkillSource.INSTALLED.canDelete).isTrue()
    assertThat(SkillSource.BUILT_IN.canDelete).isFalse()
    assertThat(SkillSource.PROJECT.canDelete).isFalse()
  }

  @Test
  fun `merging nothing yields an empty registry`() {
    val merged = SkillRegistry.merge(SkillRegistry(emptyMap()), emptyList(), emptyList())

    assertThat(merged.isEmpty).isTrue()
  }
}
