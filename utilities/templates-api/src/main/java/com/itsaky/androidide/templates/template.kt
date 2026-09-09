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

package com.itsaky.androidide.templates

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.io.File
import java.util.UUID

/**
 * Empty template recipe (no-op).
 */
val EMPTY_RECIPE = TemplateRecipe { null }

/**
 * Data that is used to create templates.
 */
sealed class TemplateData

/**
 * Result obtained after the execution of [TemplateRecipe].
 */
interface TemplateRecipeResult

interface TemplateRecipeResultWithData<D : TemplateData> : TemplateRecipeResult {

  /**
   * The data used to create the template.
   */
  val data: D
}

/**
 * Result of recipe execution for a [ProjectTemplate].
 */
interface ProjectTemplateRecipeResult : TemplateRecipeResultWithData<ProjectTemplateData>

/**
 * Recipe used to configure the execution of [TemplateRecipe].
 *
 * A [TemplateRecipeConfigurator] is called just before the [TemplateRecipe] is executed.
 */
typealias TemplateRecipeConfigurator = RecipeExecutor.() -> Unit

/**
 * Recipe for creating the project/module.
 */
fun interface TemplateRecipe<T : TemplateRecipeResult> {

  /**
   * Execute the recipe and return the [result][TemplateRecipeResult].
   *
   * @param executor The [RecipeExecutor].
   * @return The result of the execution.
   */
  fun execute(executor: RecipeExecutor): T?
}

/**
 * Recipe used to finalize the execution of [TemplateRecipe].
 *
 * A [TemplateRecipeFinalizer] is called just after the [TemplateRecipe] is executed.
 */
typealias TemplateRecipeFinalizer = RecipeExecutor.() -> Unit

/**
 * Base class for [TemplateData] implementations.
 *
 * @property name The name of the module.
 * @property projectDir The directory for the module.
 */
abstract class BaseTemplateData(val name: String, val projectDir: File) : TemplateData()

/**
 * Data for creating root projects.
 */
class ProjectTemplateData(name: String, projectDir: File) : BaseTemplateData(name, projectDir)

/**
 * Model for a template.
 *
 * @property templateName The name of the template.
 * @property thumb The thumbnail for the template.
 */
open class Template<R : TemplateRecipeResult>(@field:StringRes open val templateName: Int,
  @field:DrawableRes open val thumb: Int, open val widgets: List<Widget<*>>,
  open val recipe: TemplateRecipe<R>) {

  /**
   * The ID for this template.
   */
  val templateId: String by lazy {
    UUID.randomUUID().toString()
  }

  open val parameters: Collection<Parameter<*>>
    get() = widgets.filterIsInstance<ParameterWidget<*>>().map { it.parameter }

  open fun release() {
    widgets.forEach { it.release() }
  }

  companion object {

    @JvmStatic
    val EMPTY = Template(-1, -1, emptyList(), EMPTY_RECIPE)
  }
}

open class ProjectTemplate(val moduleTemplates: List<Template<*>>, @StringRes templateName: Int,
  @DrawableRes thumb: Int, widgets: List<Widget<*>>,
  recipe: TemplateRecipe<ProjectTemplateRecipeResult>) :
  Template<ProjectTemplateRecipeResult>(templateName, thumb, widgets, recipe) {

  override val parameters: Collection<Parameter<*>>
    get() = if (moduleTemplates.isEmpty()) super.parameters else super.parameters.toMutableList()
      .apply {
        addAll(moduleTemplates.flatMap { it.parameters })
      }

  override val widgets: List<Widget<*>>
    get() = if (moduleTemplates.isEmpty()) super.widgets else super.widgets.toMutableList().apply {
      addAll(moduleTemplates.flatMap { it.widgets })
    }

  override val recipe: TemplateRecipe<ProjectTemplateRecipeResult>
    get() = if (moduleTemplates.isEmpty()) super.recipe else super.recipe.let { projectRecipe ->
      TemplateRecipe {
        val result = projectRecipe.execute(it)
        moduleTemplates.forEach { module -> module.recipe.execute(it) }
        result
      }
    }

  override fun release() {
    super.release()
    moduleTemplates.forEach { it.release() }
  }
}

/**
 * Base class for template builders.
 *
 * @property templateName String resource for template name.
 * @property thumb Drawable resource for the template thumbnail.
 * @property widgets The widgets that will be rendered while creating this template.
 * @property recipe The recipe for building the template.
 */
abstract class TemplateBuilder<R : TemplateRecipeResult>(
  @field:StringRes open var templateName: Int? = null, @field:DrawableRes open var thumb: Int? = null,
  open var widgets: List<Widget<*>>? = null, open var recipe: TemplateRecipe<R>? = null) {

  /**
   * Adds the given widgets to the widgets list.
   *
   * @param widgets The widgets to add.
   */
  fun widgets(vararg widgets: Widget<*>) {
    var new = this.widgets?.toMutableList() ?: mutableListOf()
    if (new.isNotEmpty()) {
      // If any widgets have been already added, add the new widgets to the list
      new.addAll(widgets)
    } else {
      new = widgets.toMutableList()
    }
    this.widgets = new
  }

  fun build(): Template<R> {
    requireNotNull(templateName) { "Template must have a name" }
    requireNotNull(thumb) { "Template must have a thumbnail" }
    requireNotNull(recipe) { "Template must have a recipe" }

    this.widgets = this.widgets ?: emptyList()

    return buildInternal()
  }

  protected abstract fun buildInternal(): Template<R>
}
