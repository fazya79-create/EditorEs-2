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
import org.junit.Test

class GitHubSkillUrlTest {

  private fun archive(url: String): String? =
    (GitHubSkillUrl.resolve(url) as? GitHubSkillUrl.Resolved.Archive)?.url

  private fun manifest(url: String): GitHubSkillUrl.Resolved.Manifest? =
    GitHubSkillUrl.resolve(url) as? GitHubSkillUrl.Resolved.Manifest

  @Test
  fun `a plain repository url becomes its default branch archive`() {
    assertThat(archive("https://github.com/anthropics/skills"))
      .isEqualTo("https://codeload.github.com/anthropics/skills/zip/HEAD")
  }

  @Test
  fun `a trailing slash or dot git suffix is tolerated`() {
    assertThat(archive("https://github.com/anthropics/skills/"))
      .isEqualTo("https://codeload.github.com/anthropics/skills/zip/HEAD")
    assertThat(archive("https://github.com/anthropics/skills.git"))
      .isEqualTo("https://codeload.github.com/anthropics/skills/zip/HEAD")
  }

  @Test
  fun `a tree url downloads that ref rather than the default branch`() {
    assertThat(archive("https://github.com/anthropics/skills/tree/main/skills/pdf"))
      .isEqualTo("https://codeload.github.com/anthropics/skills/zip/refs/heads/main")
  }

  @Test
  fun `a direct zip url is used as given`() {
    val url = "https://example.com/bundle.zip"
    assertThat(archive(url)).isEqualTo(url)
  }

  @Test
  fun `a blob url pointing at a manifest becomes its raw url`() {
    val resolved = manifest("https://github.com/anthropics/skills/blob/main/skills/pdf/SKILL.md")

    assertThat(resolved?.url)
      .isEqualTo("https://raw.githubusercontent.com/anthropics/skills/main/skills/pdf/SKILL.md")
    assertThat(resolved?.name).isEqualTo("pdf")
  }

  @Test
  fun `an already raw manifest url is kept and still yields a name`() {
    val raw = "https://raw.githubusercontent.com/foo/bar/main/skills/my-skill/SKILL.md"
    val resolved = manifest(raw)

    assertThat(resolved?.url).isEqualTo(raw)
    assertThat(resolved?.name).isEqualTo("my-skill")
  }

  @Test
  fun `a host that is not github and not an archive is rejected`() {
    assertThat(GitHubSkillUrl.resolve("https://example.com/some/page")).isNull()
    assertThat(GitHubSkillUrl.resolve("https://gitlab.com/owner/repo")).isNull()
  }

  @Test
  fun `an incomplete github url is rejected`() {
    assertThat(GitHubSkillUrl.resolve("https://github.com/onlyowner")).isNull()
    assertThat(GitHubSkillUrl.resolve("")).isNull()
  }

  @Test
  fun `a query string or fragment does not leak into the path`() {
    assertThat(archive("https://github.com/anthropics/skills?tab=readme"))
      .isEqualTo("https://codeload.github.com/anthropics/skills/zip/HEAD")
  }
}
