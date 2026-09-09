package com.itsaky.androidide.fragments.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.color.MaterialColors
import com.itsaky.androidide.apk.ApkPatchEngine
import com.itsaky.androidide.databinding.LayoutNativeLibraryInjectionBinding
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.resources.R.attr
import com.itsaky.androidide.utils.DialogUtils
import java.io.File

class NativeLibraryInjectionDialogFragment : DialogFragment() {

  private var binding: LayoutNativeLibraryInjectionBinding? = null
  private var libraries: List<ApkPatchEngine.NativeLibrary> = emptyList()
  private var selectedLibrary: ApkPatchEngine.NativeLibrary? = null
  private var patchedApk: File? = null
  private var isPatching = false
  private val mainHandler = Handler(Looper.getMainLooper())

  private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (!isPatching) {
      uri?.let { binding?.apkPath?.setText(it.toString()) }
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = DialogUtils.newMaterialDialogBuilder(requireContext())
    val viewBinding = LayoutNativeLibraryInjectionBinding.inflate(LayoutInflater.from(builder.context))
    viewBinding.logScroll.isNestedScrollingEnabled = false
    binding = viewBinding
    libraries = emptyList()
    selectedLibrary = null
    viewBinding.browseApk.setEndIconOnClickListener {
      if (!isPatching) {
        apkPicker.launch(arrayOf("application/vnd.android.package-archive"))
      }
    }
    viewBinding.nativeLibrary.setOnItemClickListener { parent, _, position, _ ->
      if (!isPatching) {
        selectedLibrary = (parent.getItemAtPosition(position) as? LibraryChoice)?.library
        updateControls()
      }
    }
    viewBinding.patch.setOnClickListener { patch() }
    viewBinding.install.setOnClickListener { installPatchedApk() }
    viewBinding.apkPath.doOnTextChanged { _, _, _, _ ->
      updateControls()
    }
    updateControls()
    loadLibraries()
    return builder
      .setTitle(string.title_native_library_injection)
      .setView(viewBinding.root)
      .create()
  }

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  private fun loadLibraries() {
    val current = binding ?: return
    val project = runCatching { IProjectManager.getInstance().projectDir }.getOrNull()
    Thread {
      val discovered = runCatching { project?.let(::discoverLibraries).orEmpty() }
        .getOrElse { emptyMap() }
      mainHandler.post {
        if (binding !== current) return@post
        libraries = Abi.entries.flatMap { abi ->
          val name = when (abi) {
            Abi.ARM -> "armeabi-v7a"
            Abi.ARM64 -> "arm64-v8a"
          }
          discovered[abi].orEmpty().map { ApkPatchEngine.NativeLibrary(name, it) }
        }
        val buildDir = project?.resolve("build")
        val choices = libraries.map { library ->
          val path = buildDir?.let { library.file.relativeToOrSelf(it).path } ?: library.file.path
          LibraryChoice(library, getString(string.native_injection_library_label, library.file.name, library.abi, path))
        }
        current.nativeLibrary.setAdapter(object : ArrayAdapter<LibraryChoice>(
          current.nativeLibrary.context,
          com.google.android.material.R.layout.m3_auto_complete_simple_item,
          choices,
        ) {
          override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (super.getView(position, convertView, parent) as TextView).apply { maxLines = 3 }
        })
        selectedLibrary = libraries.singleOrNull()
        current.nativeLibrary.setText(if (choices.size == 1) choices.single().label else "", false)
        current.nativeLibraryInput.helperText = getString(
          if (libraries.isEmpty()) string.msg_native_injection_no_libraries
          else string.msg_native_injection_library_help
        )
        updateControls()
      }
    }.start()
  }

  private fun updateControls() {
    val current = binding ?: return
    current.browseApk.isEnabled = !isPatching
    current.nativeLibraryInput.isEnabled = !isPatching && libraries.isNotEmpty()
    current.patch.isEnabled = !isPatching && selectedLibrary != null && !current.apkPath.text.isNullOrBlank()
    current.patch.setText(if (isPatching) string.action_native_injection_patching else string.action_native_injection_patch)
    current.install.isEnabled = !isPatching && patchedApk != null
    current.progress.isVisible = isPatching
  }

  private fun patch() {
    if (isPatching) return
    val viewBinding = binding ?: return
    val selection = selectedLibrary ?: return
    val inputPath = viewBinding.apkPath.text?.toString().orEmpty().trim()
    if (inputPath.isBlank()) return
    val context = requireContext().applicationContext
    val project = runCatching { IProjectManager.getInstance().projectDir }.getOrElse { error ->
      appendLog(getString(string.msg_native_injection_failed, error.message ?: error.javaClass.simpleName))
      return
    }
    isPatching = true
    patchedApk = null
    updateControls()
    appendLog(getString(string.msg_native_injection_preparing))
    Thread {
      val result = runCatching {
        val temporaryInput = copyInput(context, inputPath)
        try {
          val output = File(project, "build/patched.apk")
          output.parentFile?.listFiles { file -> file.name.matches(stalePatchedApk) }?.forEach { it.delete() }
          ApkPatchEngine(context).patch(temporaryInput, output, listOf(selection)) { message ->
            mainHandler.post { appendLog(message) }
          }
        } finally {
          temporaryInput.delete()
        }
      }
      mainHandler.post {
        isPatching = false
        result.onSuccess {
          patchedApk = it.output
          appendLog(context.getString(string.msg_native_injection_complete, it.launcherActivity, it.output.path))
        }.onFailure { error ->
          appendLog(context.getString(string.msg_native_injection_failed, error.message ?: error.javaClass.simpleName))
        }
        updateControls()
      }
    }.start()
  }

  private fun copyInput(context: Context, value: String): File {
    val input = File(context.cacheDir, "input-${System.nanoTime()}.apk")
    try {
      if (value.startsWith("content://")) {
        checkNotNull(context.contentResolver.openInputStream(Uri.parse(value))).use { source ->
          input.outputStream().use(source::copyTo)
        }
      } else {
        File(value).copyTo(input)
      }
      return input
    } catch (error: Throwable) {
      input.delete()
      throw error
    }
  }

  private fun installPatchedApk() {
    if (isPatching) return
    val file = patchedApk ?: return
    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.providers.fileprovider", file)
    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
  }

  private fun appendLog(message: String) {
    val current = binding ?: return
    current.logEmpty.isVisible = false
    val color = when {
      message.startsWith(getString(string.msg_native_injection_complete).substringBefore('%')) ->
        MaterialColors.getColor(current.log, attr.colorSuccess)
      message.startsWith(getString(string.msg_native_injection_failed).substringBefore('%')) ->
        MaterialColors.getColor(current.log, com.google.android.material.R.attr.colorError)
      else -> MaterialColors.getColor(current.log, com.google.android.material.R.attr.colorOnSurfaceVariant)
    }
    current.log.append(SpannableString("$message\n").apply {
      setSpan(ForegroundColorSpan(color), 0, length, 0)
    })
    current.logScroll.post {
      if (binding === current) current.logScroll.fullScroll(View.FOCUS_DOWN)
    }
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

  private data class LibraryChoice(val library: ApkPatchEngine.NativeLibrary, val label: String) {
    override fun toString(): String = label
  }

  private enum class Abi {
    ARM,
    ARM64,
  }

  companion object {
    private val stalePatchedApk = Regex("patched-\\d+\\.apk")
  }
}
