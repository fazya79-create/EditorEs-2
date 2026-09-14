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

package com.itsaky.androidide.ai.frontmatter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YamlFrontMatterTest {

  @Test
  fun `a minimal skill manifest yields its name description and body`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: pdf
        description: Work with PDF files.
        ---

        # PDF Processing

        Use pdfplumber.
      """.trimIndent()
    )

    assertThat(document.string("name")).isEqualTo("pdf")
    assertThat(document.string("description")).isEqualTo("Work with PDF files.")
    assertThat(document.body.trim()).startsWith("# PDF Processing")
  }

  @Test
  fun `text without front matter is returned unchanged as the body`() {
    val document = YamlFrontMatter.parse("# Just markdown\n\nno front matter here")

    assertThat(document.root.entries).isEmpty()
    assertThat(document.body).isEqualTo("# Just markdown\n\nno front matter here")
  }

  @Test
  fun `an unterminated front matter block is treated as plain text`() {
    val document = YamlFrontMatter.parse("---\nname: broken\nstill going")

    assertThat(document.root.entries).isEmpty()
    assertThat(document.body).contains("still going")
  }

  @Test
  fun `a nested mapping is preserved rather than flattened away`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: grok
        metadata:
          hermes:
            tags: [Coding-Agent, Grok]
            related_skills: [codex]
        ---
        body
      """.trimIndent()
    )

    val hermes = document.value("metadata")?.get("hermes")
    assertThat(hermes).isNotNull()
    assertThat(hermes?.get("tags")?.itemsOrEmpty?.mapNotNull { it.scalarOrNull })
      .containsExactly("Coding-Agent", "Grok")
    assertThat(hermes?.get("related_skills")?.itemsOrEmpty?.mapNotNull { it.scalarOrNull })
      .containsExactly("codex")
  }

  @Test
  fun `an inline list parses into its items`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: x
        platforms: [linux, macos, windows]
        ---
        body
      """.trimIndent()
    )

    assertThat(document.strings("platforms")).containsExactly("linux", "macos", "windows").inOrder()
  }

  @Test
  fun `a dash list parses into its items`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: x
        dependencies:
          - python
          - uv
        ---
        body
      """.trimIndent()
    )

    assertThat(document.strings("dependencies")).containsExactly("python", "uv").inOrder()
  }

  @Test
  fun `a literal block scalar keeps its line breaks`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: x
        description: |
          first line
          second line
        ---
        body
      """.trimIndent()
    )

    assertThat(document.string("description")).isEqualTo("first line\nsecond line")
  }

  @Test
  fun `a folded block scalar joins its lines`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: x
        description: >
          first part
          second part
        ---
        body
      """.trimIndent()
    )

    assertThat(document.string("description")).isEqualTo("first part second part")
  }

  @Test
  fun `quoted values keep colons and commas that would otherwise split them`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: docx
        description: "Use when: creating, editing, or reading Word files."
        license: 'Proprietary'
        ---
        body
      """.trimIndent()
    )

    assertThat(document.string("description"))
      .isEqualTo("Use when: creating, editing, or reading Word files.")
    assertThat(document.string("license")).isEqualTo("Proprietary")
  }

  @Test
  fun `a url value is not truncated at its scheme colon`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        name: x
        homepage: https://example.com/docs
        ---
        body
      """.trimIndent()
    )

    assertThat(document.string("homepage")).isEqualTo("https://example.com/docs")
  }

  @Test
  fun `comments and blank lines inside front matter are ignored`() {
    val document = YamlFrontMatter.parse(
      """
        ---
        # leading comment
        name: x

        description: hello  # trailing comment
        ---
        body
      """.trimIndent()
    )

    assertThat(document.string("name")).isEqualTo("x")
    assertThat(document.string("description")).isEqualTo("hello")
  }

  @Test
  fun `carriage returns and a byte order mark do not break parsing`() {
    val document = YamlFrontMatter.parse("\uFEFF---\r\nname: x\r\ndescription: y\r\n---\r\nbody")

    assertThat(document.string("name")).isEqualTo("x")
    assertThat(document.string("description")).isEqualTo("y")
    assertThat(document.body.trim()).isEqualTo("body")
  }

  @Test
  fun `strings reads a single scalar as a one element list`() {
    val document = YamlFrontMatter.parse("---\nname: x\ntags: solo\n---\nbody")

    assertThat(document.strings("tags")).containsExactly("solo")
  }
}
