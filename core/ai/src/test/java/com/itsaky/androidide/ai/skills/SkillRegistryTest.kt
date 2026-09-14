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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillRegistryTest {

  @get:Rule
  val temp = TemporaryFolder()

  private fun skill(dir: String, text: String): File {
    val target = temp.root.resolve(dir)
    target.mkdirs()
    val manifest = target.resolve(SkillRegistry.MANIFEST)
    manifest.writeText(text)
    return manifest
  }

  private fun manifest(name: String, description: String = "does a thing", body: String = "Steps") =
    """
      ---
      name: $name
      description: $description
      ---

      $body
    """.trimIndent()

  @Test
  fun `a skill is discovered in every supported directory layout`() {
    SkillRegistry.SEARCH_DIRS.forEachIndexed { index, dir ->
      skill("$dir/skill-$index", manifest("skill-$index"))
    }

    val registry = SkillRegistry.load(temp.root)

    SkillRegistry.SEARCH_DIRS.indices.forEach { index ->
      assertThat(registry.find("skill-$index")).isNotNull()
    }
  }

  @Test
  fun `a skill nested a few directories deep is still found`() {
    skill(".androidide/skills/group/subgroup/deep-skill", manifest("deep-skill"))

    assertThat(SkillRegistry.load(temp.root).find("deep-skill")).isNotNull()
  }

  @Test
  fun `the name falls back to the directory when the front matter omits it`() {
    skill(
      ".androidide/skills/fallback-name",
      "---\ndescription: no name declared\n---\n\nbody here"
    )

    val skill = SkillRegistry.load(temp.root).find("fallback-name")
    assertThat(skill).isNotNull()
    assertThat(skill?.name).isEqualTo("fallback-name")
  }

  @Test
  fun `a name that breaks the spec is rejected rather than loaded`() {
    val bad = skill(".androidide/skills/bad", manifest("Not_Valid_Name"))

    assertThat(SkillRegistry.parse(bad)).isInstanceOf(SkillLoadResult.Invalid::class.java)
    assertThat(SkillRegistry.load(temp.root).all()).isEmpty()
  }

  @Test
  fun `a manifest with no instructions below the front matter is rejected`() {
    val empty = skill(".androidide/skills/empty", "---\nname: empty\ndescription: d\n---\n\n   ")

    assertThat(SkillRegistry.parse(empty)).isInstanceOf(SkillLoadResult.Invalid::class.java)
  }

  @Test
  fun `the earliest search directory wins when two skills share a name`() {
    skill(".androidide/skills/dup", manifest("dup", description = "from androidide"))
    skill(".claude/skills/dup", manifest("dup", description = "from claude"))

    val skill = SkillRegistry.load(temp.root).find("dup")
    assertThat(skill?.description).isEqualTo("from androidide")
  }

  @Test
  fun `bundled resources are listed but the manifest itself is not`() {
    skill(".androidide/skills/bundled", manifest("bundled"))
    val dir = temp.root.resolve(".androidide/skills/bundled")
    dir.resolve("scripts").mkdirs()
    dir.resolve("scripts/run.py").writeText("print('x')")
    dir.resolve("references").mkdirs()
    dir.resolve("references/api.md").writeText("# api")

    val skill = SkillRegistry.load(temp.root).find("bundled")
    assertThat(skill?.resources).containsExactly("references/api.md", "scripts/run.py")
    assertThat(skill?.resources?.none { it.contains(SkillRegistry.MANIFEST) }).isTrue()
  }

  @Test
  fun `optional spec fields are carried through`() {
    skill(
      ".androidide/skills/rich",
      """
        ---
        name: rich
        description: a rich skill
        license: Apache-2.0
        compatibility: Requires Python 3.14+
        allowed-tools: read_file grep
        metadata:
          author: example-org
          version: "1.0"
        ---

        body
      """.trimIndent()
    )

    val skill = SkillRegistry.load(temp.root).find("rich")
    assertThat(skill?.license).isEqualTo("Apache-2.0")
    assertThat(skill?.compatibility).isEqualTo("Requires Python 3.14+")
    assertThat(skill?.allowedTools).containsExactly("read_file", "grep").inOrder()
    assertThat(skill?.metadata?.get("author")).isEqualTo("example-org")
    assertThat(skill?.metadata?.get("version")).isEqualTo("1.0")
  }

  @Test
  fun `lookup is case insensitive and trims the requested name`() {
    skill(".androidide/skills/pdf", manifest("pdf"))

    val registry = SkillRegistry.load(temp.root)
    assertThat(registry.find("PDF")).isNotNull()
    assertThat(registry.find("  pdf  ")).isNotNull()
    assertThat(registry.find("nope")).isNull()
  }

  @Test
  fun `a project without any skills yields an empty registry`() {
    val registry = SkillRegistry.load(temp.root)

    assertThat(registry.isEmpty).isTrue()
    assertThat(registry.all()).isEmpty()
  }

  @Test
  fun `a null project directory is handled without throwing`() {
    assertThat(SkillRegistry.load(null).isEmpty).isTrue()
  }
}
