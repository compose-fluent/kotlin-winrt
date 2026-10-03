// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.collections

/** Shared data model used by the Gallery's recipe DataTemplate. */
class Recipe(val Num: Int, val Name: String, val Color: String, val IngList: List<String>) {
    val Ingredients = IngList.joinToString("\n", "\n")
    val NumIngredients get() = IngList.size
}
