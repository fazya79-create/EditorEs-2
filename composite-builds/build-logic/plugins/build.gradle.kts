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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
}

repositories {
  google()
  gradlePluginPortal()
  mavenCentral()
}

tasks.withType<KotlinCompile> {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    apiVersion.set(KotlinVersion.KOTLIN_2_1)
    languageVersion.set(KotlinVersion.KOTLIN_2_1)
    freeCompilerArgs.add("-Xuse-fir-lt=false")
  }
}

dependencies {
  implementation(projects.buildLogic.common)
  implementation(projects.buildLogic.desugaring)

  implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
  implementation(libs.maven.publish)

  implementation(libs.common.jkotlin)
  implementation(libs.common.antlr4)
}

gradlePlugin {
  plugins {
    create("com.itsaky.androidide.build") {
      id = "com.itsaky.androidide.build"
      implementationClass = "com.itsaky.androidide.plugins.AndroidIDEPlugin"
    }
    create("com.itsaky.androidide.core-app") {
      id = "com.itsaky.androidide.core-app"
      implementationClass = "com.itsaky.androidide.plugins.AndroidIDECoreAppPlugin"
    }
    create("com.itsaky.androidide.build.lexergenerator") {
      id = "com.itsaky.androidide.build.lexergenerator"
      implementationClass = "com.itsaky.androidide.plugins.LexerGeneratorPlugin"
    }
  }
}
