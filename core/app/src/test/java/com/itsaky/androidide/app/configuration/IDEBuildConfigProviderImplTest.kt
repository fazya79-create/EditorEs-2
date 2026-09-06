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

package com.itsaky.androidide.app.configuration

import com.itsaky.androidide.app.configuration.CpuArch.AARCH64
import com.itsaky.androidide.app.configuration.CpuArch.ARM
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import com.itsaky.androidide.app.configuration.CpuArch.X86_64

/**
 * @author Akash Yadav
 */
class IDEBuildConfigProviderImplTest {

  class TestBuildConfigProvider(
    override val cpuAbiName: String,
    val deviceArchs: Array<String>,
  ) : IDEBuildConfigProviderImpl() {

    override val deviceArch: CpuArch
      get() = CpuArch.forAbi(deviceArchs[0])!!

    override fun supportsCpuAbi(): Boolean = deviceArchs.contains(cpuAbiName)
  }

  @Test
  fun `test aarch64 build on aarch64-only device`() {
    TestBuildConfigProvider(AARCH64.abi, arrayOf(AARCH64.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(AARCH64.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.AARCH64)
      assertThat(deviceArch).isEqualTo(CpuArch.AARCH64)

      assertThat(isArm64v8aBuild()).isTrue()
      assertThat(isX86_64Build()).isFalse()
      assertThat(isArmeabiv7aBuild()).isFalse()

      assertThat(isArm64v8aDevice()).isTrue()
      assertThat(isX86_64Device()).isFalse()
      assertThat(isArmeabiv7aDevice()).isFalse()

      assertThat(supportsCpuAbi()).isTrue()
      assertThat(cpuArch).isEqualTo(deviceArch)
    }
  }

  @Test
  fun `test arm build on arm-only device`() {
    TestBuildConfigProvider(ARM.abi, arrayOf(ARM.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(ARM.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.ARM)
      assertThat(deviceArch).isEqualTo(CpuArch.ARM)

      assertThat(isArm64v8aBuild()).isFalse()
      assertThat(isX86_64Build()).isFalse()
      assertThat(isArmeabiv7aBuild()).isTrue()

      assertThat(isArm64v8aDevice()).isFalse()
      assertThat(isX86_64Device()).isFalse()
      assertThat(isArmeabiv7aDevice()).isTrue()

      assertThat(supportsCpuAbi()).isTrue()
      assertThat(cpuArch).isEqualTo(deviceArch)
    }
  }

  @Test
  fun `test x86_64 build on x86_64-only device`() {
    TestBuildConfigProvider(X86_64.abi, arrayOf(X86_64.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(X86_64.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.X86_64)
      assertThat(deviceArch).isEqualTo(CpuArch.X86_64)

      assertThat(isArm64v8aBuild()).isFalse()
      assertThat(isX86_64Build()).isTrue()
      assertThat(isArmeabiv7aBuild()).isFalse()

      assertThat(isArm64v8aDevice()).isFalse()
      assertThat(isX86_64Device()).isTrue()
      assertThat(isArmeabiv7aDevice()).isFalse()

      assertThat(supportsCpuAbi()).isTrue()
      assertThat(cpuArch).isEqualTo(deviceArch)
    }
  }

  @Test
  fun `test arm build on (aarch64,arm) device`() {
    TestBuildConfigProvider(ARM.abi, arrayOf(AARCH64.abi, ARM.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(ARM.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.ARM)
      assertThat(deviceArch).isEqualTo(CpuArch.AARCH64)

      assertThat(isArm64v8aBuild()).isFalse()
      assertThat(isX86_64Build()).isFalse()
      assertThat(isArmeabiv7aBuild()).isTrue()

      assertThat(isArm64v8aDevice()).isTrue()
      assertThat(isX86_64Device()).isFalse()
      assertThat(isArmeabiv7aDevice()).isFalse()

      assertThat(supportsCpuAbi()).isTrue()
      assertThat(cpuArch).isNotEqualTo(deviceArch)
    }
  }

  @Test
  fun `test arm build on (x86_64,arm) device`() {
    TestBuildConfigProvider(ARM.abi, arrayOf(X86_64.abi, ARM.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(ARM.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.ARM)
      assertThat(deviceArch).isEqualTo(CpuArch.X86_64)

      assertThat(isArm64v8aBuild()).isFalse()
      assertThat(isX86_64Build()).isFalse()
      assertThat(isArmeabiv7aBuild()).isTrue()

      assertThat(isArm64v8aDevice()).isFalse()
      assertThat(isX86_64Device()).isTrue()
      assertThat(isArmeabiv7aDevice()).isFalse()

      assertThat(supportsCpuAbi()).isTrue()
      assertThat(cpuArch).isNotEqualTo(deviceArch)
    }
  }

  @Test
  fun `test aarch64 build on arm-only device`() {
    TestBuildConfigProvider(AARCH64.abi, arrayOf(ARM.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(AARCH64.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.AARCH64)
      assertThat(deviceArch).isEqualTo(CpuArch.ARM)

      assertThat(isArm64v8aBuild()).isTrue()
      assertThat(isX86_64Build()).isFalse()
      assertThat(isArmeabiv7aBuild()).isFalse()

      assertThat(isArm64v8aDevice()).isFalse()
      assertThat(isX86_64Device()).isFalse()
      assertThat(isArmeabiv7aDevice()).isTrue()

      assertThat(supportsCpuAbi()).isFalse()
      assertThat(cpuArch).isNotEqualTo(deviceArch)
    }
  }

  @Test
  fun `test aarch64 build on x86_64-only device`() {
    TestBuildConfigProvider(AARCH64.abi, arrayOf(X86_64.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(AARCH64.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.AARCH64)
      assertThat(deviceArch).isEqualTo(CpuArch.X86_64)

      assertThat(isArm64v8aBuild()).isTrue()
      assertThat(isX86_64Build()).isFalse()
      assertThat(isArmeabiv7aBuild()).isFalse()

      assertThat(isArm64v8aDevice()).isFalse()
      assertThat(isX86_64Device()).isTrue()
      assertThat(isArmeabiv7aDevice()).isFalse()

      assertThat(supportsCpuAbi()).isFalse()
      assertThat(cpuArch).isNotEqualTo(deviceArch)
    }
  }

  @Test
  fun `test aarch64 build on (x86_64,aarch64) device`() {
    TestBuildConfigProvider(AARCH64.abi, arrayOf(X86_64.abi, AARCH64.abi)).apply {
      assertThat(cpuAbiName).isEqualTo(AARCH64.abi)
      assertThat(cpuArch).isEqualTo(CpuArch.AARCH64)
      assertThat(deviceArch).isEqualTo(CpuArch.X86_64)

      assertThat(isArm64v8aBuild()).isTrue()
      assertThat(isX86_64Build()).isFalse()
      assertThat(isArmeabiv7aBuild()).isFalse()

      assertThat(isArm64v8aDevice()).isFalse()
      assertThat(isX86_64Device()).isTrue()
      assertThat(isArmeabiv7aDevice()).isFalse()

      assertThat(supportsCpuAbi()).isTrue()
      assertThat(cpuArch).isNotEqualTo(deviceArch)
    }
  }
}