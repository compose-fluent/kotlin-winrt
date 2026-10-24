package io.github.composefluent.winrt.ide.preview

import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlFile
import com.intellij.openapi.components.service
import io.github.composefluent.winrt.ide.resources.WinRTResourceIndex
import io.github.composefluent.winrt.ide.resources.WinRTResourceReferences
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinUIXamlSymbols

internal data class DesignerStyleEdit(val pointer: SmartPsiElementPointer<XmlTag>, val sourceText: String, val label: String)

/** SDK Style.Setters own the shared values. Metadata (the CsWinRT member/DP
 * ownership contract) limits edits to the style target, not its derived consumer. */
internal object DesignerStyleEditing {
    private fun sdk(tag: XmlTag, name: String) = tag.namespace == WinUIXamlCatalog.PRESENTATION && tag.localName == name
    private fun collections(style: XmlTag) = style.subTags.filter { sdk(it, "Style.Setters") }
    private fun setters(style: XmlTag) = style.subTags.filter { sdk(it, "Setter") } + collections(style).flatMap { it.subTags.filter { child -> sdk(child, "Setter") } }
    private fun matches(style: XmlTag, property: String) = setters(style).filter { it.getAttributeValue("Property") == property }

    fun selection(base: DesignerSelection, catalog: WinUIXamlCatalog? = base.pointer.element?.let { WinUIXamlSymbols.catalog(it.containingFile) }, previewTheme: String? = null): DesignerSelection? {
        if (base.state != null || catalog == null) return null
        val tag = base.pointer.element ?: return null
        val inline = tag.subTags.filter { it.localName.substringAfter('.', "") == "Style" }
            .flatMap { it.subTags.toList() }.filter { sdk(it, "Style") }
        val value = tag.getAttribute("Style")?.valueElement
        val referenced = value?.let { WinRTResourceReferences.reference(it)?.multiResolve(false) }
            .orEmpty().mapNotNull { PsiTreeUtil.getParentOfType(it.element, XmlTag::class.java) }.distinct()
        val implicit = if (tag.getAttribute("Style") == null && tag.subTags.none { it.localName.substringAfter('.', "") == "Style" })
            WinRTResourceReferences.implicitStyle(tag, catalog, previewTheme) else null
        val style = (inline + referenced + listOfNotNull(implicit)).singleOrNull()?.takeIf { sdk(it, "Style") } ?: return null
        if (!style.containingFile.isWritable || collections(style).size > 1) return null
        val targetName = style.getAttributeValue("TargetType") ?: return null
        val target = catalog.resolve(style.getNamespaceByPrefix(targetName.substringBefore(':', "")), targetName.substringAfter(':')) ?: return null
        val members = catalog.members(target).filter { !it.isEvent && it.isWritable && catalog.isDependencyProperty(it) }.map { it.name }.toSet()
        val properties = base.properties.filter { it.dependencyProperty && it.name in members }.map { property ->
            val found = matches(style, property.name)
            property.copy(value = found.singleOrNull()?.getAttributeValue("Value"), writable = property.writable && found.size <= 1,
                elementValue = found.any { it.subTags.isNotEmpty() })
        }
        val label = style.getAttributeValue("Key", WinUIXamlCatalog.XAML) ?: if (implicit != null) "Implicit ${target.name} style" else "Inline ${target.name} style"
        val file = style.containingFile as XmlFile
        val lookup = file.originalFile.virtualFile?.path?.let { tag.project.service<WinRTResourceIndex>().forFile(it) }
        val keys = WinRTResourceReferences.resourceKeys(file, lookup, style, null).map { it.value }.distinct().sorted()
        return base.copy(properties = properties, resourceKeys = keys, structure = null, style = DesignerStyleEdit(
            SmartPointerManager.getInstance(tag.project).createSmartPsiElementPointer(style), style.containingFile.text, label))
    }

    fun validate(selection: DesignerSelection, changes: Map<String, String?>) {
        val edit = requireNotNull(selection.style)
        val style = edit.pointer.element ?: error("The style no longer exists. Select it again.")
        check(style.isValid && style.containingFile.isWritable && style.containingFile.text == edit.sourceText) { "The style changed. Select it again before editing." }
        check(collections(style).size <= 1) { "This style has multiple setter collections. Edit it in XAML." }
        changes.forEach { (property, _) ->
            check(selection.properties.single { it.name == property }.dependencyProperty) { "$property is not a dependency property." }
            val found = matches(style, property)
            check(found.size <= 1 && found.none { it.subTags.isNotEmpty() }) { "$property has complex or duplicate style setters. Edit it in XAML." }
        }
    }

    fun write(selection: DesignerSelection, changes: Map<String, String?>) {
        val style = requireNotNull(selection.style?.pointer?.element)
        changes.forEach { (property, value) ->
            val existing = matches(style, property).singleOrNull()
            if (value == null) existing?.delete()
            else if (existing != null) existing.setAttribute("Value", value)
            else {
                val owner = collections(style).singleOrNull() ?: style
                val setter = owner.createChildTag("Setter", WinUIXamlCatalog.PRESENTATION, null, false)
                setter.setAttribute("Property", property); setter.setAttribute("Value", value)
                owner.addSubTag(setter, false)
            }
        }
    }
}
