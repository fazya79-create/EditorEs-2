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

package com.itsaky.androidide.activities.editor

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CheckBox
import androidx.annotation.GravityInt
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.SizeUtils
import com.itsaky.androidide.R
import com.itsaky.androidide.R.string
import com.itsaky.androidide.databinding.LayoutSearchProjectBinding
import com.itsaky.androidide.fragments.sheets.ProgressSheet
import com.itsaky.androidide.handlers.LspHandler.connectClient
import com.itsaky.androidide.handlers.LspHandler.destroyLanguageServers
import com.itsaky.androidide.lsp.IDELanguageClientImpl
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.projects.internal.ProjectManagerImpl
import com.itsaky.androidide.utils.DialogUtils.newMaterialDialogBuilder
import com.itsaky.androidide.utils.RecursiveFileSearcher
import com.itsaky.androidide.utils.flashError
import kotlinx.coroutines.launch
import java.io.File
import java.util.regex.Pattern

/** @author Akash Yadav */
@Suppress("MemberVisibilityCanBePrivate")
abstract class ProjectHandlerActivity : BaseEditorActivity() {

  protected var mSearchingProgress: ProgressSheet? = null
  protected var mFindInProjectDialog: AlertDialog? = null

  val findInProjectDialog: AlertDialog
    get() {
      if (mFindInProjectDialog == null) {
        createFindInProjectDialog()
      }
      return mFindInProjectDialog!!
    }

  abstract fun doCloseAll(runAfter: () -> Unit)

  abstract fun saveOpenedFiles()

  override fun doDismissSearchProgress() {
    if (mSearchingProgress?.isShowing == true) {
      mSearchingProgress!!.dismiss()
    }
  }

  override fun doConfirmProjectClose() {
    confirmProjectClose()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startServices()
  }

  override fun onPause() {
    super.onPause()
    if (isDestroying) {
      // reset these values here
      // sometimes, when the IDE closed and reopened instantly, these values prevent initialization
      // of the project
      ProjectManagerImpl.getInstance().destroy()

      editorViewModel.isInitializing = false
      editorViewModel.isBuildInProgress = false
    }
  }

  override fun preDestroy() {

    if (isDestroying) {
      closeProject(false)
    }

    if (IDELanguageClientImpl.isInitialized()) {
      IDELanguageClientImpl.shutdown()
    }

    super.preDestroy()

    if (isDestroying) {
      try {
        stopLanguageServers()
      } catch (err: Exception) {
        log.error("Failed to stop editor services.")
      }
    }
  }

  fun setStatus(status: CharSequence) {
    setStatus(status, Gravity.CENTER)
  }

  fun setStatus(status: CharSequence, @GravityInt gravity: Int) {
    doSetStatus(status, gravity)
  }

  fun appendBuildOutput(str: String) {
    content.bottomSheet.appendBuildOut(str)
  }

  fun startServices() {
    initLspClient()
    lifecycleScope.launch {
      runCatching {
        ProjectManagerImpl.getInstance().setupProject()
      }.onFailure {
        log.error("Failed to setup project workspace", it)
      }.onSuccess {
        val manager = ProjectManagerImpl.getInstance()
        GeneralPreferences.lastOpenedProject = manager.projectDirPath
        supportActionBar!!.subtitle = manager.projectDir.name
      }
    }
  }

  fun stopLanguageServers() {
    try {
      destroyLanguageServers(isChangingConfigurations)
    } catch (err: Throwable) {
      log.error("Unable to stop editor services. Please report this issue.", err)
    }
  }

  protected open fun createFindInProjectDialog(): AlertDialog? {
    val manager = ProjectManagerImpl.getInstance()
    return createFindInProjectDialog(listOf(manager.projectDir))
  }

  protected open fun createFindInProjectDialog(moduleDirs: List<File>): AlertDialog? {
    val srcDirs = mutableListOf<File>()
    val binding = LayoutSearchProjectBinding.inflate(layoutInflater)
    binding.modulesContainer.removeAllViews()

    for (i in moduleDirs.indices) {
      val module = moduleDirs[i]
      if (!module.exists() || !module.isDirectory) {
        continue
      }
      val src = File(module, "src").takeIf { it.isDirectory } ?: module

      val check = CheckBox(this)
      check.text = module.name
      check.isChecked = true

      val params = MarginLayoutParams(-2, -2)
      params.bottomMargin = SizeUtils.dp2px(4f)
      binding.modulesContainer.addView(check, params)
      srcDirs.add(src)
    }

    val builder = newMaterialDialogBuilder(this)
    builder.setTitle(string.menu_find_project)
    builder.setView(binding.root)
    builder.setCancelable(false)
    builder.setPositiveButton(string.menu_find) { dialog, _ ->
      val text = binding.input.editText!!.text.toString().trim()
      if (text.isEmpty()) {
        flashError(string.msg_empty_search_query)
        return@setPositiveButton
      }

      val searchDirs = mutableListOf<File>()
      for (i in 0 until binding.modulesContainer.childCount) {
        val check = binding.modulesContainer.getChildAt(i) as CheckBox
        if (check.isChecked) {
          searchDirs.add(srcDirs[i])
        }
      }

      val extensions = binding.filter.editText!!.text.toString().trim()
      val extensionList = mutableListOf<String>()
      if (extensions.isNotEmpty()) {
        if (extensions.contains("|")) {
          for (str in
          extensions
            .split(Pattern.quote("|").toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()) {
            if (str.trim().isEmpty()) {
              continue
            }
            extensionList.add(str)
          }
        } else {
          extensionList.add(extensions)
        }
      }

      if (searchDirs.isEmpty()) {
        flashError(string.msg_select_search_modules)
      } else {
        dialog.dismiss()

        getProgressSheet(string.msg_searching_project)?.apply {
          show(supportFragmentManager, "search_in_project_progress")
        }

        RecursiveFileSearcher.searchRecursiveAsync(text, extensionList, searchDirs) { results ->
          handleSearchResults(results)
        }
      }
    }

    builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
    mFindInProjectDialog = builder.create()
    return mFindInProjectDialog
  }

  private fun closeProject(manualFinish: Boolean) {
    if (manualFinish) {
      // if the user is manually closing the project,
      // save the opened files cache
      // this is needed because in this case, the opened files cache will be empty
      // when onPause will be called.
      saveOpenedFiles()

      // reset the lastOpenedProject if the user explicitly chose to close the project
      GeneralPreferences.lastOpenedProject = GeneralPreferences.NO_OPENED_PROJECT
    }

    // Make sure we close files
    // This will make sure that file contents are not erased.
    doCloseAll {
      if (manualFinish) {
        finish()
      }
    }
  }

  private fun confirmProjectClose() {
    val builder = newMaterialDialogBuilder(this)
    builder.setTitle(string.title_confirm_project_close)
    builder.setMessage(string.msg_confirm_project_close)
    builder.setNegativeButton(string.no, null)
    builder.setPositiveButton(string.yes) { dialog, _ ->
      dialog.dismiss()
      closeProject(true)
    }
    builder.show()
  }

  private fun initLspClient() {
    if (!IDELanguageClientImpl.isInitialized()) {
      IDELanguageClientImpl.initialize(this as EditorHandlerActivity)
    }
    connectClient(IDELanguageClientImpl.getInstance())
  }

  open fun getProgressSheet(msg: Int): ProgressSheet? {
    doDismissSearchProgress()

    mSearchingProgress =
      ProgressSheet().also {
        it.isCancelable = false
        it.setMessage(getString(msg))
        it.setSubMessageEnabled(false)
      }

    return mSearchingProgress
  }
}
