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
import org.junit.Assume.assumeTrue
import org.junit.Test

class SkillCorpusTest {

  private fun corpusRoots(): List<File> =
    System.getenv(CORPUS_ENV)
      .orEmpty()
      .split(File.pathSeparatorChar)
      .filter { it.isNotBlank() }
      .map { File(it) }
      .filter { it.isDirectory }

  @Test
  fun `every skill in the external corpus parses into a usable skill`() {
    val roots = corpusRoots()
    assumeTrue("Set $CORPUS_ENV to a skills checkout to run this", roots.isNotEmpty())

    val manifests = roots.flatMap { root ->
      root.walkTopDown().filter { it.isFile && it.name == SkillRegistry.MANIFEST }.toList()
    }
    assumeTrue("No SKILL.md found under the corpus roots", manifests.isNotEmpty())

    val failures = mutableListOf<String>()
    var loaded = 0
    var withQuotedColon = 0
    var withNestedMetadata = 0

    manifests.forEach { manifest ->
      val raw = manifest.readText().replace("\r\n", "\n")
      val front = raw.substringAfter("---\n", "").substringBefore("\n---", "")

      when (val result = SkillRegistry.parse(manifest)) {
        is SkillLoadResult.Loaded -> {
          loaded++
          val skill = result.skill

          if (skill.description.isBlank()) {
            failures += "no description: ${manifest.absolutePath}"
          }
          if (skill.description.contains("---")) {
            failures += "description swallowed the delimiter: ${manifest.absolutePath}"
          }
          if (skill.body.trimStart().startsWith("name:")) {
            failures += "body contains front matter: ${manifest.absolutePath}"
          }

          val declaredName = Regex("^name:\\s*(.+)$", RegexOption.MULTILINE)
            .find(front)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.trim('"', '\'')
          if (declaredName != null && !declaredName.equals(skill.name, ignoreCase = true)) {
            failures += "name '${skill.name}' != declared '$declaredName': ${manifest.absolutePath}"
          }

          val quoted = Regex("^description:\\s*\"(.*)\"\\s*$", RegexOption.MULTILINE).find(front)
          if (quoted != null) {
            val inner = quoted.groupValues[1]
            if (inner.contains(':')) {
              withQuotedColon++
              if (skill.description != inner.replace("\\\"", "\"")) {
                failures += "quoted description was mangled: ${manifest.absolutePath}"
              }
            }
          }

          if (front.contains(Regex("^metadata:\\s*$", RegexOption.MULTILINE))) {
            withNestedMetadata++
            if (skill.metadata.isEmpty()) {
              failures += "nested metadata was dropped: ${manifest.absolutePath}"
            }
            skill.metadata.forEach { (key, value) ->
              if (key.contains(':') || key.startsWith("\"")) {
                failures += "metadata key '$key' not unquoted: ${manifest.absolutePath}"
              }
              if (value.isBlank()) {
                failures += "metadata '$key' lost its value: ${manifest.absolutePath}"
              }
            }
          }
        }

        is SkillLoadResult.Invalid -> failures += "${result.reason}: ${result.path}"
      }
    }

    assertThat(failures).isEmpty()
    assertThat(loaded).isEqualTo(manifests.size)
    assertThat(withQuotedColon).isGreaterThan(0)
    assertThat(withNestedMetadata).isGreaterThan(0)
  }

  companion object {

    private const val CORPUS_ENV = "ANDROIDIDE_SKILL_CORPUS"
  }
}
