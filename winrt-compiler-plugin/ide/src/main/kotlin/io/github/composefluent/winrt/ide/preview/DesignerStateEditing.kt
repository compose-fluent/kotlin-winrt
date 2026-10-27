@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog

internal data class DesignerStateEdit(val pointer: SmartPsiElementPointer<XmlTag>, val sourceText: String, val targetName: String)

/** VisualState.Setters are SDK-owned dependency property overrides. Recording
 * changes only their source declarations, never the selected element's base value. */
internal object DesignerStateEditing {
    private fun sdk(tag: XmlTag, name: String) = tag.namespace == WinUIXamlCatalog.PRESENTATION && tag.localName == name
    private fun name(tag: XmlTag) = tag.getAttributeValue("Name", WinUIXamlCatalog.XAML)
    private fun scope(tag: XmlTag): XmlTag = generateSequence(tag) { it.parentTag }
        .first { it.parentTag == null || it.namespace == WinUIXamlCatalog.PRESENTATION && it.localName in setOf("ControlTemplate", "DataTemplate") }
    private fun properties(state: XmlTag) = state.subTags.filter { sdk(it, "VisualState.Setters") }
    private fun target(name: String, property: String) = "$name." + if ('.' in property) "($property)" else property
    private fun setters(state: XmlTag, name: String, property: String): List<XmlTag> = properties(state)
        .flatMap { it.subTags.toList() }.filter { sdk(it, "Setter") && it.getAttributeValue("Target") == target(name, property) }

    fun selection(base: DesignerSelection, group: DesignerStateGroup, stateName: String): DesignerSelection? {
        val tag = base.pointer.element ?: return null
        val scope = scope(tag)
        val targetName = name(tag)?.takeIf(String::isNotBlank) ?: return null
        val tags = listOf(scope) + PsiTreeUtil.findChildrenOfType(scope, XmlTag::class.java)
        if (tags.count { scope(it) == scope && name(it) == targetName } != 1) return null
        // Transformed preview paths need not equal source paths; match the full
        // named group signature in the same namescope and reject ambiguity.
        val candidates = tags.filter { sdk(it, "VisualStateGroup") && scope(it) == scope &&
            it.parentTag?.let { parent -> sdk(parent, "VisualStateManager.VisualStateGroups") } == true &&
            name(it) == group.label && it.subTags.filter { child -> sdk(child, "VisualState") }.map { child -> name(child) } == group.states }
        val state = candidates.singleOrNull()?.subTags?.singleOrNull { sdk(it, "VisualState") && name(it) == stateName } ?: return null
        if (properties(state).size > 1) return null
        val overrides = base.properties.map { property ->
            val matches = setters(state, targetName, property.name)
            val setter = matches.singleOrNull()
            property.copy(value = setter?.getAttributeValue("Value"),
                writable = property.writable && property.dependencyProperty && matches.size <= 1,
                elementValue = setter?.subTags?.isNotEmpty() == true)
        }
        val effective = base.attributes.toMutableMap()
        overrides.forEach { property -> property.value?.let { effective[property.name] = it } }
        return base.copy(properties = overrides, attributes = effective, structure = null,
            state = DesignerStateEdit(SmartPointerManager.getInstance(tag.project).createSmartPsiElementPointer(state), tag.containingFile.text, targetName))
    }

    fun validate(selection: DesignerSelection, changes: Map<String, String?>) {
        val edit = requireNotNull(selection.state)
        val state = edit.pointer.element ?: error("The selected visual state no longer exists.")
        val tag = selection.pointer.element ?: error("The selected element no longer exists.")
        check(state.isValid && state.containingFile == tag.containingFile && state.containingFile.text == edit.sourceText) {
            "XAML changed during state editing. Select the element again."
        }
        check(scope(state) == scope(tag) && name(tag) == edit.targetName) { "The target namescope changed." }
        check(properties(state).size <= 1) { "This state has multiple setter collections. Edit it in XAML." }
        changes.forEach { (property, _) ->
            check(selection.properties.single { it.name == property }.dependencyProperty) { "$property is not a dependency property." }
            val matches = setters(state, edit.targetName, property)
            check(matches.size <= 1 && matches.none { it.subTags.isNotEmpty() }) { "$property has complex or duplicate state setters. Edit it in XAML." }
        }
    }

    fun write(selection: DesignerSelection, changes: Map<String, String?>) {
        val edit = requireNotNull(selection.state)
        val state = requireNotNull(edit.pointer.element)
        changes.forEach { (property, value) ->
            val existing = setters(state, edit.targetName, property).singleOrNull()
            if (value == null) existing?.delete()
            else if (existing != null) existing.setAttribute("Value", value)
            else {
                val collection = properties(state).singleOrNull() ?: state.addSubTag(
                    state.createChildTag("VisualState.Setters", WinUIXamlCatalog.PRESENTATION, null, false), false)
                val setter = collection.createChildTag("Setter", WinUIXamlCatalog.PRESENTATION, null, false)
                setter.setAttribute("Target", target(edit.targetName, property))
                setter.setAttribute("Value", value)
                collection.addSubTag(setter, false)
            }
        }
    }
}
