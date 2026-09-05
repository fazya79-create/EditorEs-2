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

package com.itsaky.androidide.actions.build

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorActivityAction
import com.itsaky.androidide.backend.build.BuildEvent
import com.itsaky.androidide.backend.build.BuildRequest
import com.itsaky.androidide.backend.build.BuildRunner
import com.itsaky.androidide.backend.build.RunConfigurations
import com.itsaky.androidide.backend.proot.ProotConfig
import com.itsaky.androidide.preferences.internal.BackendPreferences
import com.itsaky.androidide.projects.internal.ProjectManagerImpl
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.tasks.runOnUiThread
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import org.slf4j.LoggerFactory

class CmakeBuildAction(context: Context, override val order: Int) : EditorActivityAction() {

  override val id: String = "ide.editor.build.cmake"

  companion object {
    private val log = LoggerFactory.getLogger(CmakeBuildAction::class.java)
  }

  init {
    label = context.getString(R.string.action_build_cmake)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_run)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)
    val activity = data.getActivity() ?: run {
      visible = false
      enabled = false
      return
    }

    visible = true
    enabled = ProjectManagerImpl.getInstance().projectInitialized &&
      !activity.editorViewModel.isBuildInProgress
  }

  override suspend fun execAction(data: ActionData): Any {
    val activity = data.getActivity() ?: return BuildResult(false, 0)
    val appContext = activity.applicationContext

    if (!ProotConfig.isInstalled(appContext)) {
      return BuildResult(false, 0, "Ubuntu environment is not installed")
    }

    return try {
      val projectDir = ProjectManagerImpl.getInstance().projectDir
      val runner = BuildRunner(
        appContext,
        BackendPreferences.abis(),
        BackendPreferences.buildApiLevel,
        BackendPreferences.buildType()
      )
      val configs = RunConfigurations(projectDir, runner)
      if (!configs.hasPresets()) {
        configs.bootstrap()
      }
      val preset = configs.activePreset()
        ?: return BuildResult(false, 0, "No CMake presets found")

      runOnUiThread {
        activity.editorViewModel.isBuildInProgress = true
        activity.appendBuildOutput("> build $preset")
      }

      var exitCode = 1
      var failure: String? = null
      runner.run(projectDir, BuildRequest.Build(preset)) { event ->
        when (event) {
          is BuildEvent.Line -> runOnUiThread { activity.appendBuildOutput(event.text) }
          is BuildEvent.Finished -> exitCode = event.exitCode
          is BuildEvent.Failed -> failure = event.message
        }
      }
      BuildResult(failure == null && exitCode == 0, exitCode, failure)
    } catch (error: Throwable) {
      log.error("CMake build failed", error)
      BuildResult(false, 0, error.message)
    }
  }

  override fun postExec(data: ActionData, result: Any) {
    val activity = data.requireActivity()
    activity.editorViewModel.isBuildInProgress = false
    activity.invalidateOptionsMenu()

    if (result !is BuildResult) {
      return
    }

    if (result.success) {
      activity.appendBuildOutput("> build finished")
      activity.flashSuccess(activity.getString(R.string.msg_build_finished, result.exitCode))
    } else {
      val detail = result.message ?: "exit code ${result.exitCode}"
      activity.appendBuildOutput("> build failed: $detail")
      activity.flashError(detail)
    }
  }

  data class BuildResult(val success: Boolean, val exitCode: Int, val message: String? = null)
}
