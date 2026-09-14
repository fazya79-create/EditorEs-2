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

object GitHubSkillUrl {

  sealed interface Resolved {

    data class Archive(val url: String) : Resolved

    data class Manifest(val url: String, val name: String) : Resolved
  }

  fun resolve(raw: String): Resolved? {
    val url = raw.trim().trimEnd('/')
    if (url.isEmpty()) {
      return null
    }

    if (url.endsWith(".zip")) {
      return Resolved.Archive(url)
    }

    if (url.endsWith("/${SkillRegistry.MANIFEST}", ignoreCase = true)) {
      return Resolved.Manifest(rawContentUrl(url), nameFromManifestUrl(url))
    }

    val host = hostOf(url) ?: return null
    if (host != "github.com" && host != "www.github.com") {
      return null
    }

    val segments = pathOf(url).split('/').filter { it.isNotEmpty() }
    if (segments.size < 2) {
      return null
    }

    val owner = segments[0]
    val repo = segments[1].removeSuffix(".git")

    val ref = if (segments.size >= 4 && segments[2] == "tree") segments[3] else null

    return Resolved.Archive(
      if (ref != null) {
        "https://codeload.github.com/$owner/$repo/zip/refs/heads/$ref"
      } else {
        "https://codeload.github.com/$owner/$repo/zip/HEAD"
      }
    )
  }

  private fun rawContentUrl(url: String): String {
    val host = hostOf(url)
    if (host != "github.com" && host != "www.github.com") {
      return url
    }

    val segments = pathOf(url).split('/').filter { it.isNotEmpty() }
    val blob = segments.indexOf("blob")
    if (blob < 0 || segments.size < blob + 3) {
      return url
    }

    val owner = segments[0]
    val repo = segments[1]
    val rest = segments.drop(blob + 1).joinToString("/")
    return "https://raw.githubusercontent.com/$owner/$repo/$rest"
  }

  private fun nameFromManifestUrl(url: String): String =
    pathOf(url).split('/').filter { it.isNotEmpty() }.dropLast(1).lastOrNull().orEmpty()

  private fun hostOf(url: String): String? {
    val withoutScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (withoutScheme.isEmpty()) {
      return null
    }
    return withoutScheme.substringBefore('/').substringBefore(':').lowercase()
  }

  private fun pathOf(url: String): String {
    val withoutScheme = url.substringAfter("://", missingDelimiterValue = url)
    val slash = withoutScheme.indexOf('/')
    if (slash < 0) {
      return ""
    }
    return withoutScheme.substring(slash + 1).substringBefore('?').substringBefore('#')
  }
}
