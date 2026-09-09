package com.itsaky.androidide.fragments.dialogs

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.R
import com.itsaky.androidide.apk.ApkPatchEngine
import com.itsaky.androidide.databinding.LayoutNativeLibraryInjectionBinding
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R.style
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class NativeLibraryInjectionDialogFragmentTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var project: File
  private lateinit var controller: ActivityController<FragmentActivity>
  private lateinit var fragment: NativeLibraryInjectionDialogFragment
  private lateinit var binding: LayoutNativeLibraryInjectionBinding

  @Before
  fun setUp() {
    project = temporaryFolder.newFolder("project")
    IProjectManager.getInstance().openProject(project)
    controller = Robolectric.buildActivity(FragmentActivity::class.java)
    controller.get().setTheme(style.Theme_AndroidIDE)
    controller.setup().visible()
  }

  @After
  fun tearDown() {
    if (::fragment.isInitialized && fragment.isAdded) fragment.dismissNow()
    if (::controller.isInitialized) controller.pause().stop().destroy()
    IProjectManager.getInstance().destroy()
  }

  @Test
  fun `browse icon opens the in-app browser and handles its result`() {
    library("armeabi-v7a/libsample.so")
    showDialog(1)

    val icon = browseIcon()
    assertThat(icon.isClickable).isTrue()
    assertThat(icon.performClick()).isTrue()
    assertThat(controller.get().supportFragmentManager.findFragmentByTag("apk-file-browser"))
      .isInstanceOf(ApkFileBrowserDialogFragment::class.java)
    controller.get().supportFragmentManager.setFragmentResult(
      ApkFileBrowserDialogFragment.RESULT_KEY,
      bundleOf(ApkFileBrowserDialogFragment.RESULT_PATH to "/storage/emulated/0/Download/example.apk"),
    )
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(binding.apkPath.text.toString()).isEqualTo("/storage/emulated/0/Download/example.apk")
    assertThat(binding.patch.isEnabled).isTrue()
    assertThat(binding.log.text.toString()).isEmpty()
  }

  @Test
  fun `dismissing the in-app browser preserves the current APK path`() {
    library("arm64-v8a/libsample.so")
    showDialog(1)
    val originalPath = "/storage/emulated/0/Download/original.apk"
    binding.apkPath.setText(originalPath)

    assertThat(browseIcon().performClick()).isTrue()
    (controller.get().supportFragmentManager.findFragmentByTag("apk-file-browser") as DialogFragment).dismiss()
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(binding.apkPath.text.toString()).isEqualTo(originalPath)
    assertThat(binding.patch.isEnabled).isTrue()
    assertThat(binding.log.text.toString()).isEmpty()
  }

  @Test
  fun `a single discovered library is automatically selected`() {
    val file = library("armeabi-v7a/libsample.so")
    showDialog(1)

    assertThat(dropdowns(binding.root)).containsExactly(binding.nativeLibrary)
    assertThat(selectedLibrary()).isEqualTo(ApkPatchEngine.NativeLibrary("armeabi-v7a", file))
    assertThat(binding.nativeLibrary.text.toString()).contains("armeabi-v7a")
    assertThat(binding.nativeLibrary.text.toString()).contains("armeabi-v7a/libsample.so")
    assertThat(binding.patch.isEnabled).isFalse()
    binding.apkPath.setText("/tmp/input.apk")
    assertThat(binding.patch.isEnabled).isTrue()
    assertThat(binding.log.text.toString()).isEmpty()
  }

  @Test
  fun `same-name libraries with cache ABIs remain separate explicit choices`() {
    val arm = library("debug32/libsample.so")
    arm.parentFile!!.resolve("CMakeCache.txt").writeText("ANDROID_ABI:STRING=armeabi-v7a\n")
    val arm64 = library("debug64/libsample.so")
    arm64.parentFile!!.resolve("CMakeCache.txt").writeText("ANDROID_ABI:STRING=arm64-v8a\n")
    showDialog(2)

    assertThat(dropdowns(binding.root)).containsExactly(binding.nativeLibrary)
    assertThat(selectedLibrary()).isNull()
    assertThat(binding.nativeLibrary.text.toString()).isEmpty()
    binding.apkPath.setText("/tmp/input.apk")
    assertThat(binding.patch.isEnabled).isFalse()
    val labels = (0 until binding.nativeLibrary.adapter.count).map {
      binding.nativeLibrary.adapter.getItem(it).toString()
    }
    assertThat(labels[0]).contains("armeabi-v7a")
    assertThat(labels[0]).contains("debug32/libsample.so")
    assertThat(labels[1]).contains("arm64-v8a")
    assertThat(labels[1]).contains("debug64/libsample.so")

    chooseLibrary(1)
    assertThat(selectedLibrary()).isEqualTo(ApkPatchEngine.NativeLibrary("arm64-v8a", arm64))
    assertThat(binding.patch.isEnabled).isTrue()
    chooseLibrary(0)
    assertThat(selectedLibrary()).isEqualTo(ApkPatchEngine.NativeLibrary("armeabi-v7a", arm))
    assertThat(binding.log.text.toString()).isEmpty()
  }

  @Test
  fun `discovery deduplicates files found through both cache and preset paths`() {
    val file = library("armeabi-v7a/libsample.so")
    file.parentFile!!.resolve("CMakeCache.txt").writeText("ANDROID_ABI:STRING=armeabi-v7a\n")
    showDialog(1)

    assertThat(selectedLibrary()).isEqualTo(ApkPatchEngine.NativeLibrary("armeabi-v7a", file))
    assertThat(binding.log.text.toString()).isEmpty()
  }

  @Test
  fun `an empty build keeps patch disabled without writing housekeeping logs`() {
    showDialog(0)
    binding.apkPath.setText("/tmp/input.apk")

    assertThat(dropdowns(binding.root)).containsExactly(binding.nativeLibrary)
    assertThat(selectedLibrary()).isNull()
    assertThat(binding.nativeLibrary.isEnabled).isFalse()
    assertThat(binding.patch.isEnabled).isFalse()
    assertThat(binding.log.text.toString()).isEmpty()
  }

  @Test
  fun `a picked content URI is copied and the successful output can be installed`() {
    library("arm64-v8a/libsample.so")
    showDialog(1)
    val bytes = checkNotNull(javaClass.getResourceAsStream("/native-injection/manifest-21.apk"))
      .use { it.readBytes() }
    val uri = Uri.parse("content://downloads/document/fixture.apk")
    shadowOf(RuntimeEnvironment.getApplication().contentResolver).registerInputStream(uri, bytes.inputStream())
    val copiedInput = AtomicReference<File>()
    mockkConstructor(ApkPatchEngine::class)
    every { anyConstructed<ApkPatchEngine>().patch(any(), any(), any(), any()) } answers {
      val input = firstArg<File>()
      val output = secondArg<File>()
      copiedInput.set(input)
      assertThat(input.readBytes()).isEqualTo(bytes)
      output.parentFile!!.mkdirs()
      output.writeBytes(bytes)
      ApkPatchEngine.PatchResult(output, "fixture.MainActivity")
    }

    try {
      controller.get().supportFragmentManager.setFragmentResult(
        ApkFileBrowserDialogFragment.RESULT_KEY,
        bundleOf(ApkFileBrowserDialogFragment.RESULT_PATH to uri.toString()),
      )
      binding.patch.performClick()
      await { binding.install.isEnabled }
      assertThat(copiedInput.get().exists()).isFalse()
      assertThat(binding.log.text.toString()).contains("fixture.MainActivity")

      binding.install.performClick()
      val intent = shadowOf(controller.get()).nextStartedActivity
      assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
      assertThat(intent.type).isEqualTo("application/vnd.android.package-archive")
      assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
      assertThat(intent.data?.scheme).isEqualTo("content")
      assertThat(intent.data?.authority).isEqualTo("${controller.get().packageName}.providers.fileprovider")
    } finally {
      unmockkConstructor(ApkPatchEngine::class)
    }
  }

  @Test
  fun `repeated patches reuse one output file and remove earlier patched APKs`() {
    library("arm64-v8a/libsample.so")
    val build = project.resolve("build")
    val stale = build.resolve("patched-1700000000000.apk").apply { writeText("stale") }
    val unrelated = build.resolve("app-debug.apk").apply { writeText("unrelated") }
    showDialog(1)
    var run = 0
    mockkConstructor(ApkPatchEngine::class)
    every { anyConstructed<ApkPatchEngine>().patch(any(), any(), any(), any()) } answers {
      val output = secondArg<File>()
      output.parentFile!!.mkdirs()
      output.writeText("patched ${++run}")
      ApkPatchEngine.PatchResult(output, "fixture.MainActivity")
    }

    try {
      binding.apkPath.setText(temporaryFolder.newFile("source.apk").apply { writeText("original") }.path)
      repeat(3) { iteration ->
        binding.patch.performClick()
        await { binding.progress.visibility == View.GONE && binding.install.isEnabled }
        val apks = build.listFiles { file -> file.extension == "apk" }!!.map { it.name }
        assertThat(apks).containsExactly("patched.apk", "app-debug.apk")
        assertThat(build.resolve("patched.apk").readText()).isEqualTo("patched ${iteration + 1}")
      }
      assertThat(stale.exists()).isFalse()
      assertThat(unrelated.readText()).isEqualTo("unrelated")

      binding.install.performClick()
      val intent = shadowOf(controller.get()).nextStartedActivity
      assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
      assertThat(intent.data?.lastPathSegment).isEqualTo("patched.apk")
    } finally {
      unmockkConstructor(ApkPatchEngine::class)
    }
  }

  @Test
  fun `patching locks inputs captures the selection and cleans up after failure`() {
    val library = library("arm64-v8a/libsample.so")
    val input = temporaryFolder.newFile("source.apk").apply { writeText("original APK") }
    showDialog(1)
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val copiedInput = AtomicReference<File>()
    val output = AtomicReference<File>()
    val selections = AtomicReference<Collection<ApkPatchEngine.NativeLibrary>>()
    mockkConstructor(ApkPatchEngine::class)
    every { anyConstructed<ApkPatchEngine>().patch(any(), any(), any(), any()) } answers {
      copiedInput.set(firstArg())
      output.set(secondArg())
      selections.set(thirdArg())
      entered.countDown()
      check(release.await(10, TimeUnit.SECONDS))
      throw IllegalStateException("test patch failure")
    }

    try {
      binding.apkPath.setText(input.path)
      assertThat(binding.patch.performClick()).isTrue()
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(binding.apkPath.isEnabled).isFalse()
      assertThat(browseIcon().isEnabled).isFalse()
      assertThat(binding.nativeLibrary.isEnabled).isFalse()
      assertThat(binding.install.isEnabled).isFalse()
      assertThat(binding.progress.visibility).isEqualTo(View.VISIBLE)

      binding.apkPath.setText("/tmp/another.apk")
      assertThat(binding.patch.isEnabled).isFalse()
      binding.patch.performClick()
      verify(exactly = 1) { anyConstructed<ApkPatchEngine>().patch(any(), any(), any(), any()) }
      assertThat(copiedInput.get().readText()).isEqualTo("original APK")
      assertThat(output.get().parentFile).isEqualTo(project.resolve("build"))
      assertThat(selections.get()).containsExactly(ApkPatchEngine.NativeLibrary("arm64-v8a", library))

      release.countDown()
      await { binding.apkPath.isEnabled }
      assertThat(copiedInput.get().exists()).isFalse()
      assertThat(binding.nativeLibrary.isEnabled).isTrue()
      assertThat(binding.patch.isEnabled).isTrue()
      assertThat(binding.install.isEnabled).isFalse()
      assertThat(binding.progress.visibility).isEqualTo(View.GONE)
      assertThat(binding.log.text.toString()).contains("test patch failure")
    } finally {
      release.countDown()
      try {
        await { binding.apkPath.isEnabled }
      } finally {
        unmockkConstructor(ApkPatchEngine::class)
      }
    }
  }

  private fun library(path: String): File = project.resolve("build/$path").apply {
    parentFile!!.mkdirs()
    writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
  }

  private fun showDialog(expectedLibraries: Int) {
    fragment = NativeLibraryInjectionDialogFragment()
    fragment.showNow(controller.get().supportFragmentManager, "native-injection")
    binding = LayoutNativeLibraryInjectionBinding.bind(
      checkNotNull(fragment.dialog).findViewById(R.id.native_injection_content)
    )
    await { binding.nativeLibrary.adapter?.count == expectedLibraries }
    assertThat(binding.nativeLibrary.adapter).isNotNull()
    assertThat(binding.nativeLibrary.adapter.count).isEqualTo(expectedLibraries)
  }

  private fun await(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!condition() && System.nanoTime() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(10)
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(condition()).isTrue()
  }

  private fun browseIcon(): View = binding.browseApk.findViewById(
    com.google.android.material.R.id.text_input_end_icon
  )

  private fun chooseLibrary(position: Int) {
    binding.nativeLibrary.requestFocus()
    binding.nativeLibrary.showDropDown()
    shadowOf(Looper.getMainLooper()).idle()
    binding.nativeLibrary.setListSelection(position)
    binding.nativeLibrary.performCompletion()
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun selectedLibrary(): ApkPatchEngine.NativeLibrary? =
    NativeLibraryInjectionDialogFragment::class.java.getDeclaredField("selectedLibrary").let {
      it.isAccessible = true
      it.get(fragment) as ApkPatchEngine.NativeLibrary?
    }

  private fun dropdowns(view: View): List<MaterialAutoCompleteTextView> = when (view) {
    is MaterialAutoCompleteTextView -> listOf(view)
    is ViewGroup -> (0 until view.childCount).flatMap { dropdowns(view.getChildAt(it)) }
    else -> emptyList()
  }
}
