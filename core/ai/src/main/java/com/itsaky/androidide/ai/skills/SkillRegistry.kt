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

import com.itsaky.androidide.ai.frontmatter.YamlFrontMatter
import com.itsaky.androidide.ai.frontmatter.YamlValue
import java.io.File
import org.slf4j.LoggerFactory

class SkillRegistry(private val skills: Map<String, Skill>) {

  fun all(): List<Skill> = skills.values.sortedBy { it.name }

  fun find(name: String): Skill? = skills[name.trim().lowercase()]

  val isEmpty: Boolean
    get() = skills.isEmpty()

  companion object {

    private val log = LoggerFactory.getLogger(SkillRegistry::class.java)

    val SEARCH_DIRS = listOf(
      ".androidide/skills",
      ".claude/skills",
      ".agents/skills",
      ".opencode/skills",
      ".opencode/skill",
      "skills"
    )

    const val MANIFEST = "SKILL.md"

    private const val MAX_MANIFEST_BYTES = 512L * 1024
    private const val MAX_DEPTH = 4
    private const val MAX_SKILLS = 200
    private const val MAX_RESOURCES = 24
    private const val NAME_MAX = 64
    private const val DESCRIPTION_MAX = 1024

    private val NAME_PATTERN = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    private val IGNORED_DIRS = setOf(".git", "node_modules", "build", ".gradle", "__pycache__")

    fun load(projectDir: File?): SkillRegistry {
      val roots = rootsFor(projectDir)
      val merged = LinkedHashMap<String, Skill>()

      roots.forEach { root ->
        manifestsIn(root).forEach { manifest ->
          when (val result = parse(manifest)) {
            is SkillLoadResult.Loaded ->
              if (merged.size < MAX_SKILLS && !merged.containsKey(result.skill.name)) {
                merged[result.skill.name] = result.skill.copy(source = SkillSource.PROJECT)
              }

            is SkillLoadResult.Invalid ->
              log.debug("Ignoring skill {}: {}", result.path, result.reason)
          }
        }
      }

      return SkillRegistry(merged)
    }

    fun merge(
      project: SkillRegistry,
      installed: List<Skill>,
      bundled: List<Skill>
    ): SkillRegistry {
      val merged = LinkedHashMap<String, Skill>()
      project.all().forEach { merged[it.name] = it }
      installed.forEach { merged.putIfAbsent(it.name, it) }
      bundled.forEach { merged.putIfAbsent(it.name, it) }
      return SkillRegistry(merged)
    }

    fun rootsFor(projectDir: File?): List<File> {
      val project = projectDir ?: return emptyList()
      return SEARCH_DIRS.map { project.resolve(it) }.filter { it.isDirectory }
    }

    fun manifestsIn(root: File): List<File> {
      val found = mutableListOf<File>()
      collect(root, 0, found)
      return found.sortedBy { it.absolutePath }
    }

    private fun collect(dir: File, depth: Int, into: MutableList<File>) {
      if (depth > MAX_DEPTH || into.size >= MAX_SKILLS) {
        return
      }

      val manifest = dir.resolve(MANIFEST)
      if (manifest.isFile) {
        into += manifest
        return
      }

      dir.listFiles().orEmpty()
        .filter { it.isDirectory && it.name !in IGNORED_DIRS }
        .sortedBy { it.name }
        .forEach { collect(it, depth + 1, into) }
    }

    fun parse(manifest: File): SkillLoadResult {
      val path = manifest.absolutePath

      if (!manifest.isFile) {
        return SkillLoadResult.Invalid(path, "not a file")
      }
      if (manifest.length() > MAX_MANIFEST_BYTES) {
        return SkillLoadResult.Invalid(path, "manifest larger than $MAX_MANIFEST_BYTES bytes")
      }

      val text = runCatching { manifest.readText() }.getOrElse {
        return SkillLoadResult.Invalid(path, "unreadable: ${it.message}")
      }

      val document = YamlFrontMatter.parse(text)
      val directory = manifest.parentFile ?: return SkillLoadResult.Invalid(path, "no parent")

      val declared = document.string("name")?.trim().orEmpty()
      val name = declared.ifEmpty { directory.name }.lowercase()

      if (name.isEmpty() || name.length > NAME_MAX) {
        return SkillLoadResult.Invalid(path, "name must be 1..$NAME_MAX characters")
      }
      if (!NAME_PATTERN.matches(name)) {
        return SkillLoadResult.Invalid(path, "name '$name' is not lowercase-hyphen-separated")
      }

      val body = document.body.trim()
      if (body.isEmpty()) {
        return SkillLoadResult.Invalid(path, "no instructions below the front matter")
      }

      val description = document.string("description")?.trim().orEmpty().take(DESCRIPTION_MAX)

      return SkillLoadResult.Loaded(
        Skill(
          name = name,
          description = description,
          body = body,
          directory = directory,
          license = document.string("license")?.trim().orEmpty(),
          compatibility = document.string("compatibility")?.trim().orEmpty(),
          version = document.string("version")?.trim().orEmpty(),
          allowedTools = allowedTools(document.value("allowed-tools")),
          metadata = metadataOf(document.value("metadata")),
          resources = resourcesIn(directory)
        )
      )
    }

    private fun allowedTools(value: YamlValue?): List<String> = when (value) {
      null -> emptyList()
      is YamlValue.Scalar -> value.text.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
      else -> value.itemsOrEmpty.mapNotNull { it.scalarOrNull }.filter { it.isNotEmpty() }
    }

    private fun metadataOf(value: YamlValue?): Map<String, String> = when (value) {
      is YamlValue.Mapping -> value.entries.mapValues { it.value.flatten() }
      null -> emptyMap()
      else -> mapOf("value" to value.flatten())
    }

    private fun resourcesIn(directory: File): List<String> {
      val found = mutableListOf<String>()
      collectResources(directory, directory, 0, found)
      return found.sorted().take(MAX_RESOURCES)
    }

    private fun collectResources(root: File, dir: File, depth: Int, into: MutableList<String>) {
      if (depth > 2 || into.size >= MAX_RESOURCES) {
        return
      }
      dir.listFiles().orEmpty().sortedBy { it.name }.forEach { entry ->
        when {
          entry.isDirectory && entry.name !in IGNORED_DIRS ->
            collectResources(root, entry, depth + 1, into)

          entry.isFile && entry.name != MANIFEST ->
            if (into.size < MAX_RESOURCES) {
              into += entry.relativeTo(root).path.replace(File.separatorChar, '/')
            }
        }
      }
    }
  }
}
