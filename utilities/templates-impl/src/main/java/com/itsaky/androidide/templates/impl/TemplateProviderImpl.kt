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

package com.itsaky.androidide.templates.impl

import com.google.auto.service.AutoService
import com.google.common.collect.ImmutableList
import com.itsaky.androidide.templates.ITemplateProvider
import com.itsaky.androidide.templates.Template
import com.itsaky.androidide.templates.impl.cppExecutable.cppExecutableProject

@Suppress("unused")
@AutoService(ITemplateProvider::class)
class TemplateProviderImpl : ITemplateProvider {

  private val templates = mutableMapOf<String, Template<*>>()

  init {
    initializeTemplates()
  }

  private fun templates() =
    arrayOf(
      cppExecutableProject()
    )

  private fun initializeTemplates() {
    templates().forEach { template ->
      templates[template.templateId] = template
    }
  }

  override fun getTemplates(): List<Template<*>> {
    return ImmutableList.copyOf(templates.values)
  }

  override fun getTemplate(templateId: String): Template<*>? {
    return templates[templateId]
  }

  override fun reload() {
    release()
    initializeTemplates()
  }

  override fun release() {
    templates.values.forEach { it.release() }
    templates.clear()
  }
}
