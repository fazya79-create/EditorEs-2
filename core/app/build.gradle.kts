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

@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.desugaring.utils.JavaIOReplacements.applyJavaIOReplacements

plugins {
  // must precede com.android.application: the root script checks for it in plugins.withId
  id("com.itsaky.androidide.core-app")
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-kapt")
  id("kotlin-parcelize")
  id("androidx.navigation.safeargs.kotlin")
  id("com.itsaky.androidide.desugaring")
}

buildscript {
  dependencies {
    classpath(libs.logging.logback.core)
    classpath(libs.composite.desugaringCore)
  }
}

android {
  namespace = BuildConfig.packageName

  signingConfigs {
    getByName("debug") {
      val ciKeystore = System.getenv("DEBUG_KEYSTORE")
        ?: (findProperty("debugKeystore") as? String)
      if (!ciKeystore.isNullOrBlank()) {
        storeFile = file(ciKeystore)
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("release") {
      storeFile = file(
        System.getenv("RELEASE_KEYSTORE") ?: "config/release.keystore")
      storePassword = System.getenv("RELEASE_STORE_PASSWORD")
      keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "editores"
      keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
    }
  }

  defaultConfig {
    applicationId = BuildConfig.packageName
    vectorDrawables.useSupportLibrary = true
  }

  androidResources {
    generateLocaleConfig = true
  }

  packaging {
    resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
  }

  buildTypes {
    release {
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("release")
    }
  }

  lint {
    abortOnError = false
    disable.addAll(arrayOf("VectorPath", "NestedWeights", "ContentDescription", "SmallSp"))
  }
}

kapt {
  arguments {
    arg("eventBusIndex", "${BuildConfig.packageName}.events.AppEventsIndex")
  }
}

desugaring {
  replacements {
    includePackage(
      "org.eclipse.jgit",
    )

    applyJavaIOReplacements()
  }
}

dependencies {
  // Annotation processors
  kapt(libs.google.auto.service)
  kapt(projects.annotation.processors)

  implementation(libs.common.editor)
  implementation(libs.common.jsonrpc)
  implementation(libs.common.lsp4j)
  implementation(libs.common.utilcode)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.common.hiddenApiBypass)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.gson)
  implementation(libs.google.guava)
  implementation(libs.android.apksig)
  implementation(libs.smali.dexlib2)
  implementation(libs.bouncycastle.prov)
  implementation(libs.bouncycastle.pkix)

  // Git
  implementation(libs.git.jgit)

  // AndroidX
  implementation(libs.androidx.splashscreen)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.cardview)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.coordinatorlayout)
  implementation(libs.androidx.drawer)
  implementation(libs.androidx.grid)
  implementation(libs.androidx.nav.fragment)
  implementation(libs.androidx.nav.ui)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.transition)
  implementation(libs.androidx.vectors)
  implementation(libs.androidx.animated.vectors)
  implementation(libs.google.material)
  implementation(libs.google.flexbox)

  // Kotlin
  implementation(libs.androidx.core.ktx)
  implementation(libs.common.kotlin)

  // Dependencies in composite build
  implementation(libs.composite.appintro)
  implementation(libs.composite.desugaringCore)

  // Local projects here
  implementation(projects.core.actions)
  implementation(projects.core.ai)
  implementation(projects.core.backend)
  implementation(projects.core.common)
  implementation(projects.core.lspApi)
  implementation(projects.core.lspModels)
  implementation(projects.core.projects)
  implementation(projects.core.resources)
  implementation(projects.editor.impl)
  implementation(projects.editor.lexers)
  implementation(projects.event.eventbus)
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.termux.application)
  implementation(projects.termux.view)
  implementation(projects.termux.emulator)
  implementation(projects.termux.shared)
  implementation(projects.utilities.buildInfo)
  implementation(projects.utilities.lookup)
  implementation(projects.utilities.preferences)
  implementation(projects.utilities.shared)
  implementation(projects.utilities.templatesApi)
  implementation(projects.utilities.templatesImpl)
  implementation(projects.utilities.treeview)

  testImplementation(projects.testing.unitTest)
}

val injectionFixtures = layout.buildDirectory.dir("generated/injectionFixtures")
val generateInjectionFixtures by tasks.registering {
  inputs.dir("src/test/fixtures/native-injection")
  outputs.dir(injectionFixtures)
  doLast {
    val output = injectionFixtures.get().asFile.resolve("native-injection").apply { mkdirs() }
    val buildTools = android.sdkDirectory.resolve("build-tools/${android.buildToolsVersion}")
    listOf(21, 26).forEach { minSdk ->
      exec {
        commandLine(buildTools.resolve("aapt2"), "link", "--manifest",
          file("src/test/fixtures/native-injection/AndroidManifest.xml"),
          "-I", android.sdkDirectory.resolve("platforms/${android.compileSdkVersion}/android.jar"),
          "--min-sdk-version", minSdk, "--target-sdk-version", 28,
          "-o", output.resolve("manifest-$minSdk.apk"))
      }
    }
    val toolchain = android.sdkDirectory.resolve("ndk/${android.ndkVersion}/toolchains/llvm/prebuilt")
      .listFiles()!!.single { it.isDirectory }.resolve("bin")
    mapOf("armeabi-v7a" to "armv7a-linux-androideabi21-clang", "arm64-v8a" to "aarch64-linux-android21-clang").forEach { (abi, compiler) ->
      val library = output.resolve("$abi/libfixture.so.bin").apply { parentFile.mkdirs() }
      exec {
        commandLine(toolchain.resolve(compiler), "-shared", "-fPIC",
          file("src/test/fixtures/native-injection/library.c"), "-o", library)
      }
    }
  }
}

android.sourceSets.getByName("test").resources.srcDir(injectionFixtures)
tasks.matching { it.name.startsWith("process") && it.name.endsWith("UnitTestJavaRes") }.configureEach {
  dependsOn(generateInjectionFixtures)
}
