package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.fragments.dialogs.NativeLibraryInjectionDialogFragment
import com.itsaky.androidide.resources.R
import kotlin.reflect.KClass

class NativeLibraryInjectionSidebarAction(context: Context, override val order: Int) :
  AbstractSidebarAction() {

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment>? = null

  init {
    label = context.getString(R.string.title_native_library_injection)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_inject)
  }

  override suspend fun execAction(data: ActionData): Any {
    val activity = data.requireContext() as? FragmentActivity ?: return false
    if (activity.supportFragmentManager.findFragmentByTag(TAG) == null) {
      NativeLibraryInjectionDialogFragment().show(activity.supportFragmentManager, TAG)
    }
    return true
  }

  companion object {
    const val ID = "ide.editor.sidebar.nativeLibraryInjection"
    private const val TAG = "nativeLibraryInjection"
  }
}
