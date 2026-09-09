package com.itsaky.androidide.fragments.dialogs

import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.LayoutApkFileBrowserBinding
import com.itsaky.androidide.databinding.LayoutApkFileBrowserItemBinding
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.utils.DialogUtils
import java.io.File
import java.util.Locale

class ApkFileBrowserDialogFragment : DialogFragment() {

  private var binding: LayoutApkFileBrowserBinding? = null
  private lateinit var adapter: FileAdapter
  private var directory = Environment.getExternalStorageDirectory()

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = DialogUtils.newMaterialDialogBuilder(requireContext())
    val viewBinding = LayoutApkFileBrowserBinding.inflate(LayoutInflater.from(builder.context))
    binding = viewBinding
    adapter = FileAdapter(::onEntryClicked)
    viewBinding.files.layoutManager = LinearLayoutManager(viewBinding.root.context)
    viewBinding.files.adapter = adapter
    loadDirectory(directory)
    return builder.setTitle(string.action_browse_apk).setView(viewBinding.root).create()
  }

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  private fun onEntryClicked(entry: Entry) {
    if (entry.file.isDirectory) {
      loadDirectory(entry.file)
    } else if (entry.file.extension.equals("apk", true)) {
      parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(RESULT_PATH to entry.file.path))
      dismiss()
    }
  }

  private fun loadDirectory(target: File) {
    val current = binding ?: return
    directory = target
    current.path.text = target.path
    adapter.submit(emptyList())
    Thread {
      val entries = buildList {
        target.parentFile?.takeIf { target != Environment.getExternalStorageDirectory() }?.let {
          add(Entry(it, "..", true))
        }
        target.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
          ?.forEach { add(Entry(it, it.name, it.isDirectory)) }
      }
      current.root.post {
        if (binding !== current || directory != target) return@post
        adapter.submit(entries)
        entries.filter { !it.directory && it.file.extension.equals("apk", true) }.forEach { entry ->
          loadIcon(current, target, entry)
        }
      }
    }.start()
  }

  private fun loadIcon(current: LayoutApkFileBrowserBinding, target: File, entry: Entry) {
    Thread {
      val icon = runCatching {
        val info = requireContext().packageManager.getPackageArchiveInfo(entry.file.path, 0) ?: return@runCatching null
        checkNotNull(info.applicationInfo).apply {
          sourceDir = entry.file.path
          publicSourceDir = entry.file.path
        }.loadIcon(requireContext().packageManager)
      }.getOrNull()
      current.root.post {
        if (binding === current && directory == target && icon != null) adapter.setIcon(entry.file, icon)
      }
    }.start()
  }

  private data class Entry(val file: File, val label: String, val directory: Boolean, var icon: Drawable? = null)

  private class FileAdapter(private val onClick: (Entry) -> Unit) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {
    private val entries = mutableListOf<Entry>()

    fun submit(newEntries: List<Entry>) {
      entries.clear()
      entries.addAll(newEntries)
      notifyDataSetChanged()
    }

    fun setIcon(file: File, icon: Drawable) {
      val position = entries.indexOfFirst { it.file == file }
      if (position >= 0) {
        entries[position].icon = icon
        notifyItemChanged(position)
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
      ViewHolder(LayoutApkFileBrowserItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(entries[position])

    override fun getItemCount(): Int = entries.size

    inner class ViewHolder(private val binding: LayoutApkFileBrowserItemBinding) : RecyclerView.ViewHolder(binding.root) {
      fun bind(entry: Entry) {
        binding.name.text = entry.label
        binding.icon.setImageDrawable(entry.icon ?: binding.root.context.getDrawable(
          if (entry.directory) android.R.drawable.ic_menu_agenda else android.R.drawable.ic_menu_save
        ))
        binding.root.setOnClickListener { onClick(entry) }
      }
    }
  }

  companion object {
    const val RESULT_KEY = "apk-file-browser-result"
    const val RESULT_PATH = "apk-file-browser-path"
  }
}
