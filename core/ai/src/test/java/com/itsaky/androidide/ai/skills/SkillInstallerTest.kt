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

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkillInstallerTest {

  @get:Rule
  val temp = TemporaryFolder()

  private lateinit var store: SkillStore
  private lateinit var installer: SkillInstaller

  @Before
  fun setUp() {
    store = SkillStore(ApplicationProvider.getApplicationContext())
    store.installedRoot().deleteRecursively()
    installer = SkillInstaller(store)
  }

  private fun manifest(name: String, description: String = "does a thing") =
    "---\nname: $name\ndescription: $description\n---\n\nSteps for $name."

  private fun zipOf(entries: Map<String, String>): ByteArrayInputStream {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      entries.forEach { (path, content) ->
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray())
        zip.closeEntry()
      }
    }
    return ByteArrayInputStream(bytes.toByteArray())
  }

  @Test
  fun `a directory holding a manifest installs and becomes loadable`() {
    val dir = temp.newFolder("my-skill")
    dir.resolve(SkillRegistry.MANIFEST).writeText(manifest("my-skill"))

    val result = installer.installFromDirectory(dir)

    assertThat(result).isInstanceOf(SkillInstallResult.Installed::class.java)
    assertThat((result as SkillInstallResult.Installed).names).containsExactly("my-skill")
    assertThat(store.installed().map { it.name }).containsExactly("my-skill")
    assertThat(store.installed().single().source).isEqualTo(SkillSource.INSTALLED)
  }

  @Test
  fun `bundled resources alongside the manifest come across too`() {
    val dir = temp.newFolder("with-files")
    dir.resolve(SkillRegistry.MANIFEST).writeText(manifest("with-files"))
    dir.resolve("scripts").mkdirs()
    dir.resolve("scripts/go.py").writeText("print('hi')")

    installer.installFromDirectory(dir)

    val installed = store.dirFor("with-files")
    assertThat(installed.resolve("scripts/go.py").isFile).isTrue()
    assertThat(installed.resolve("scripts/go.py").readText()).contains("print")
  }

  @Test
  fun `a directory with no manifest is refused`() {
    val dir = temp.newFolder("empty")
    dir.resolve("notes.txt").writeText("nothing here")

    val result = installer.installFromDirectory(dir)

    assertThat(result).isInstanceOf(SkillInstallResult.Failed::class.java)
    assertThat(store.installed()).isEmpty()
  }

  @Test
  fun `an archive containing several skills installs every one of them`() {
    val zip = zipOf(
      mapOf(
        "pack/alpha/SKILL.md" to manifest("alpha"),
        "pack/beta/SKILL.md" to manifest("beta"),
        "pack/README.md" to "not a skill"
      )
    )

    val result = installer.installFromZip(zip, "bundle.zip")

    assertThat(result).isInstanceOf(SkillInstallResult.Installed::class.java)
    assertThat((result as SkillInstallResult.Installed).names).containsExactly("alpha", "beta")
    assertThat(store.installed().map { it.name }).containsExactly("alpha", "beta")
  }

  @Test
  fun `an archive entry that escapes the staging directory is ignored`() {
    val zip = zipOf(
      mapOf(
        "../evil.txt" to "should not land outside",
        "ok/SKILL.md" to manifest("ok")
      )
    )

    val result = installer.installFromZip(zip, "bundle.zip")

    assertThat(result).isInstanceOf(SkillInstallResult.Installed::class.java)
    assertThat(store.installedRoot().parentFile?.resolve("evil.txt")?.exists()).isFalse()
  }

  @Test
  fun `an empty archive reports a failure rather than succeeding quietly`() {
    val result = installer.installFromZip(zipOf(emptyMap()), "empty.zip")

    assertThat(result).isInstanceOf(SkillInstallResult.Failed::class.java)
  }

  @Test
  fun `reinstalling replaces the previous copy instead of merging into it`() {
    val first = temp.newFolder("dup-one")
    first.resolve(SkillRegistry.MANIFEST).writeText(manifest("dup", "first version"))
    first.resolve("stale.txt").writeText("old")
    installer.installFromDirectory(first)

    val second = temp.newFolder("dup-two")
    second.resolve(SkillRegistry.MANIFEST).writeText(manifest("dup", "second version"))
    installer.installFromDirectory(second)

    assertThat(store.installed().single().description).isEqualTo("second version")
    assertThat(store.dirFor("dup").resolve("stale.txt").exists()).isFalse()
  }

  @Test
  fun `the origin is recorded and read back`() {
    val dir = temp.newFolder("tracked")
    dir.resolve(SkillRegistry.MANIFEST).writeText(manifest("tracked"))

    installer.installFromDirectory(dir)

    assertThat(store.readOrigin("tracked")).isEqualTo(dir.absolutePath)
    assertThat(store.installed().single().origin).isEqualTo(dir.absolutePath)
  }

  @Test
  fun `a bare manifest downloaded from a url installs under its declared name`() {
    val result = installer.installFromManifestText(
      manifest("from-url"),
      fallbackName = "ignored",
      origin = "https://example.com/SKILL.md"
    )

    assertThat(result).isInstanceOf(SkillInstallResult.Installed::class.java)
    assertThat(store.installed().map { it.name }).containsExactly("from-url")
    assertThat(store.readOrigin("from-url")).isEqualTo("https://example.com/SKILL.md")
  }

  @Test
  fun `a manifest whose name breaks the spec is refused`() {
    val result = installer.installFromManifestText(
      manifest("Bad_Name"),
      fallbackName = "bad",
      origin = "test"
    )

    assertThat(result).isInstanceOf(SkillInstallResult.Failed::class.java)
    assertThat(store.installed()).isEmpty()
  }

  @Test
  fun `deleting removes the skill from disk`() {
    val dir = temp.newFolder("goner")
    dir.resolve(SkillRegistry.MANIFEST).writeText(manifest("goner"))
    installer.installFromDirectory(dir)

    assertThat(store.delete("goner")).isTrue()
    assertThat(store.installed()).isEmpty()
    assertThat(store.delete("goner")).isFalse()
  }

  @Test
  fun `a non http url is refused before any network access`() {
    val result = installer.installFromUrl("ftp://example.com/skill.zip")

    assertThat(result).isInstanceOf(SkillInstallResult.Failed::class.java)
    assertThat((result as SkillInstallResult.Failed).reason).contains("http")
  }

  @Test
  fun `an unusable url is refused with a clear reason`() {
    assertThat(installer.installFromUrl("   "))
      .isInstanceOf(SkillInstallResult.Failed::class.java)
    assertThat(installer.installFromUrl("https://example.com/not/a/skill"))
      .isInstanceOf(SkillInstallResult.Failed::class.java)
  }
}
