package com.itsaky.androidide.fragments.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.itsaky.androidide.apk.ApkPatchEngine
import com.itsaky.androidide.databinding.LayoutNativeLibraryInjectionBinding
import com.itsaky.androidide.projects.IProjectManager
import java.io.File

class NativeLibraryInjectionDialogFragment : DialogFragment() {

  private var binding: LayoutNativeLibraryInjectionBinding? = null
  private var armLibraries: List<File> = emptyList()
  private var arm64Libraries: List<File> = emptyList()
  private var patchedApk: File? = null

  private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { binding?.apkPath?.setText(it.toString()) }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val viewBinding = LayoutNativeLibraryInjectionBinding.inflate(LayoutInflater.from(requireContext()))
    binding = viewBinding
    viewBinding.browseApk.setOnClickListener { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) }
    viewBinding.patch.setOnClickListener { patch() }
    viewBinding.install.setOnClickListener { installPatchedApk() }
    viewBinding.apkPath.doOnTextChanged { _, _, _, _ ->
      viewBinding.patch.isEnabled = !viewBinding.apkPath.text.isNullOrBlank()
    }
    loadLibraries()
    return MaterialAlertDialogBuilder(requireContext())
      .setTitle(com.itsaky.androidide.resources.R.string.title_native_library_injection)
      .setView(viewBinding.root)
      .create()
  }

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  private fun loadLibraries() {
    appendLog("Scanning native build outputs…")
    Thread {
      val libraries = runCatching { discoverLibraries(IProjectManager.getInstance().projectDir) }
        .getOrElse { emptyMap() }
      activity?.runOnUiThread {
        val viewBinding = binding ?: return@runOnUiThread
        armLibraries = libraries[Abi.ARM].orEmpty()
        arm64Libraries = libraries[Abi.ARM64].orEmpty()
        setLibraries(viewBinding.armLibraries, armLibraries)
        setLibraries(viewBinding.arm64Libraries, arm64Libraries)
        appendLog("Found ${libraries[Abi.ARM].orEmpty().size} arm and ${libraries[Abi.ARM64].orEmpty().size} arm64 libraries.")
      }
    }.start()
  }

  private fun setLibraries(input: android.widget.AutoCompleteTextView, libraries: List<File>) {
    val buildDir = runCatching { File(IProjectManager.getInstance().projectDir, "build") }.getOrNull()
    val labels = libraries.map { file ->
      buildDir?.let { runCatching { file.relativeTo(it).path }.getOrNull() } ?: file.path
    }
    input.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
  }

  private fun patch() {
    val viewBinding = binding ?: return
    val selections = listOfNotNull(
      selectedLibrary(viewBinding.armLibraries, armLibraries, "armeabi-v7a"),
      selectedLibrary(viewBinding.arm64Libraries, arm64Libraries, "arm64-v8a"),
    )
    if (selections.isEmpty()) {
      appendLog("Select at least one native library.")
      return
    }
    viewBinding.patch.isEnabled = false
    viewBinding.install.isEnabled = false
    appendLog("Preparing APK patch…")
    Thread {
      val result = runCatching {
        val temporaryInput = copyInput(viewBinding.apkPath.text?.toString().orEmpty())
        try {
          val project = IProjectManager.getInstance().projectDir
          val output = File(project, "build/patched-${System.currentTimeMillis()}.apk")
          ApkPatchEngine(requireContext().applicationContext).patch(temporaryInput, output, selections) { message ->
            activity?.runOnUiThread { appendLog(message) }
          }
        } finally {
          temporaryInput.delete()
        }
      }
      activity?.runOnUiThread {
        val current = binding ?: return@runOnUiThread
        result.onSuccess {
          patchedApk = it.output
          current.install.isEnabled = true
          appendLog("Patched ${it.launcherActivity} and wrote ${it.output.path}")
        }.onFailure { error -> appendLog("Patch failed: ${error.message ?: error.javaClass.simpleName}") }
        current.patch.isEnabled = true
      }
    }.start()
  }

  private fun selectedLibrary(
    input: android.widget.AutoCompleteTextView,
    libraries: List<File>,
    abi: String,
  ): ApkPatchEngine.NativeLibrary? {
    val file = libraries.getOrNull(input.listSelection) ?: libraries.firstOrNull {
      it.name == input.text.toString().substringAfterLast('/')
    }
    return file?.let { ApkPatchEngine.NativeLibrary(abi, it) }
  }

  private fun copyInput(value: String): File {
    val input = File(requireContext().cacheDir, "input-${System.nanoTime()}.apk")
    if (value.startsWith("content://")) {
      requireContext().contentResolver.openInputStream(android.net.Uri.parse(value))!!.use { source ->
        input.outputStream().use(source::copyTo)
      }
    } else {
      File(value).copyTo(input)
    }
    return input
  }

  private fun installPatchedApk() {
    val file = patchedApk ?: return
    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.providers.fileprovider", file)
    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
  }

  private fun appendLog(message: String) {
    binding?.log?.append("$message\n")
    binding?.logScroll?.post { binding?.logScroll?.fullScroll(android.view.View.FOCUS_DOWN) }
  }

  private fun discoverLibraries(projectDir: File): Map<Abi, List<File>> {
    val buildDir = File(projectDir, "build")
    if (!buildDir.isDirectory) return emptyMap()
    val results = mutableMapOf<Abi, LinkedHashSet<File>>()
    val cacheDirectories = buildDir.walkTopDown()
      .filter { it.name == "CMakeCache.txt" && it.isFile }
      .map { it.parentFile }
      .toList()
    cacheDirectories.forEach { directory ->
      val abi = cacheAbi(directory) ?: abiFromPresetPath(directory, buildDir) ?: return@forEach
      directory.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { library ->
        results.getOrPut(abi) { linkedSetOf() }.add(library)
      }
    }
    buildDir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { library ->
      val abi = abiFromPresetPath(library, buildDir) ?: return@forEach
      results.getOrPut(abi) { linkedSetOf() }.add(library)
    }
    return results.mapValues { (_, libraries) -> libraries.sortedBy { it.name } }
  }

  private fun cacheAbi(directory: File): Abi? {
    val value = directory.resolve("CMakeCache.txt").useLines { lines ->
      lines.firstOrNull { it.startsWith("ANDROID_ABI") }?.substringAfter('=', "")
    }
    return abiOf(value.orEmpty())
  }

  private fun abiFromPresetPath(file: File, buildDir: File): Abi? =
    abiOf(runCatching { file.relativeTo(buildDir).path }.getOrDefault(file.absolutePath))

  private fun abiOf(value: String): Abi? = when {
    value.contains("arm64-v8a") -> Abi.ARM64
    value.contains("armeabi-v7a") || value.contains("armeabi") -> Abi.ARM
    else -> null
  }

  private enum class Abi {
    ARM,
    ARM64,
  }
}
