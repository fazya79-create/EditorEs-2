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

package com.itsaky.androidide.preferences.internal

@Suppress("MemberVisibilityCanBePrivate")
object BackendPreferences {

  const val BUILD_ABI = "ide.backend.buildAbi"
  const val BUILD_API_LEVEL = "ide.backend.buildApiLevel"
  const val BUILD_TYPE = "ide.backend.buildType"
  const val NDK_VERSION = "ide.backend.ndkVersion"
  const val CMAKE_VERSION = "ide.backend.cmakeVersion"

  const val ABI_ARM64 = 0
  const val ABI_ARM32 = 1
  const val ABI_ALL = 2

  const val BUILD_TYPE_RELEASE = 0
  const val BUILD_TYPE_DEBUG = 1
  const val BUILD_TYPE_REL_WITH_DEB_INFO = 2
  const val BUILD_TYPE_MIN_SIZE_REL = 3

  var buildAbi: Int
    get() = prefManager.getInt(BUILD_ABI, ABI_ARM64)
    set(value) {
      prefManager.putInt(BUILD_ABI, value)
    }

  var buildApiLevel: Int
    get() = prefManager.getInt(BUILD_API_LEVEL, 24)
    set(value) {
      prefManager.putInt(BUILD_API_LEVEL, value)
    }

  var buildTypeIndex: Int
    get() = prefManager.getInt(BUILD_TYPE, BUILD_TYPE_RELEASE)
    set(value) {
      prefManager.putInt(BUILD_TYPE, value)
    }

  var ndkVersion: String
    get() = prefManager.getString(NDK_VERSION, "")
    set(value) {
      prefManager.putString(NDK_VERSION, value)
    }

  var cmakeVersion: String
    get() = prefManager.getString(CMAKE_VERSION, "")
    set(value) {
      prefManager.putString(CMAKE_VERSION, value)
    }

  fun abis(): List<String> = when (buildAbi) {
    ABI_ARM32 -> listOf("armeabi-v7a")
    ABI_ALL -> listOf("arm64-v8a", "armeabi-v7a")
    else -> listOf("arm64-v8a")
  }

  fun buildType(): String = when (buildTypeIndex) {
    BUILD_TYPE_DEBUG -> "Debug"
    BUILD_TYPE_REL_WITH_DEB_INFO -> "RelWithDebInfo"
    BUILD_TYPE_MIN_SIZE_REL -> "MinSizeRel"
    else -> "Release"
  }
}
