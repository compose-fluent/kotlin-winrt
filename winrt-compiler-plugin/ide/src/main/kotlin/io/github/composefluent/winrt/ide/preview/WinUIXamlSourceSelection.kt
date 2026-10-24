package io.github.composefluent.winrt.ide.preview

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.runtime.WinUIXamlVisualNode

/** IDE navigation only: runtime/template children need not have a source tag.
 * Prefer unique names and never guess between repeated unnamed controls. */
internal object WinUIXamlSourceSelection {
    private fun XmlTag.inTemplate(): Boolean = generateSequence(this) { it.parentTag }.any {
        it.localName in setOf("ControlTemplate", "DataTemplate", "ItemsPanelTemplate", "Style")
    }
    private fun XmlTag.xamlName(): String? = attributes.singleOrNull {
        it.localName == "Name" && (it.namespace.isEmpty() || it.namespace == "http://schemas.microsoft.com/winfx/2006/xaml")
    }?.value?.takeIf(String::isNotBlank)

    private fun matchesType(tag: XmlTag, node: WinUIXamlVisualNode): Boolean {
        val framework = if (node.typeName.startsWith("Windows.UI.Xaml."))
            io.github.composefluent.winrt.runtime.XamlFramework.UWP else io.github.composefluent.winrt.runtime.XamlFramework.WinUI
        val namespaces = io.github.composefluent.winrt.metadata.WinUIXamlNamespaces.namespaces(tag.namespace, framework)
        return if (namespaces.isEmpty()) false
        else namespaces.any { node.typeName == "$it.${tag.localName}" }
    }

    fun node(tag: XmlTag, nodes: List<WinUIXamlVisualNode>): WinUIXamlVisualNode? {
        if (tag.inTemplate()) {
            // An inline Control.Template has one known visual owner. Scope names
            // to that owner's subtree; shared resources and repeated item templates
            // need an instance-aware contract and must not guess by global name.
            val template = generateSequence(tag) { it.parentTag }.firstOrNull {
                it.localName in setOf("ControlTemplate", "DataTemplate", "ItemsPanelTemplate", "Style")
            } ?: return null
            if (template.localName != "ControlTemplate") return null
            val property = template.parentTag ?: return null
            if (!property.localName.endsWith(".Template")) return null
            val owner = property.parentTag?.let { node(it, nodes) } ?: return null
            val name = tag.xamlName()
            if (name != null) return nodes.singleOrNull { it.name == name && matchesType(tag, it) &&
                it.path.size > owner.path.size && it.path.take(owner.path.size) == owner.path }
            // An inline template is a single known realization. Unnamed parts
            // are editable only when their complete direct sibling sequence
            // matches; a generated presenter/wrapper makes the mapping fail.
            if (tag == template || '.' in tag.localName) return null
            val parent = tag.parentTag?.let { if (it.localName.endsWith(".Children")) it.parentTag else it } ?: return null
            val visualParent = if (parent == template) owner else node(parent, nodes) ?: return null
            return orderedChild(tag, parent, visualParent, nodes)
        }
        val className = tag.getAttributeValue("Class", "http://schemas.microsoft.com/winfx/2006/xaml")
        if (tag.parentTag == null && !className.isNullOrBlank()) {
            nodes.singleOrNull { it.typeName == className }?.let { return it }
        }
        val name = tag.xamlName()
        if (name != null) return nodes.singleOrNull { it.name == name && matchesType(tag, it) }
        val type = tag.localName
        if ('.' in type) return null
        // A direct authored child is distinct from same-type template descendants.
        // Only narrow this way when the source parent has one child of this type.
        val sourceParent = tag.parentTag
        if (sourceParent != null && sourceParent.subTags.count { it.localName == type } == 1) {
            node(sourceParent, nodes)?.let { visualParent ->
                nodes.singleOrNull { matchesType(tag, it) &&
                    it.path.dropLast(1) == visualParent.path && it.path.size == visualParent.path.size + 1 }
                    ?.let { return it }
            }
        }
        // Ordered siblings are exact only when the entire direct visual sequence
        // agrees. Never guess through control-template or item-container children.
        val visualParentTag = sourceParent?.let { if ('.' in it.localName) it.parentTag else it }
        if (visualParentTag != null) node(visualParentTag, nodes)?.let { visualParent ->
            orderedChild(tag, visualParentTag, visualParent, nodes)?.let { return it }
        }
        val parentName = generateSequence(tag.parentTag) { it.parentTag }.firstNotNullOfOrNull { it.xamlName() }
        val parent = parentName?.let { value -> nodes.singleOrNull { it.name == value } } ?: if (parentName != null) return null else null
        return nodes.filter { matchesType(tag, it) &&
            (parent == null || it.path.size > parent.path.size && it.path.take(parent.path.size) == parent.path) }.singleOrNull()
    }

    private fun orderedChild(tag: XmlTag, parent: XmlTag, visualParent: WinUIXamlVisualNode,
        nodes: List<WinUIXamlVisualNode>): WinUIXamlVisualNode? {
        val sourceChildren = parent.subTags.flatMap { child ->
            if (child.localName.endsWith(".Children")) child.subTags.toList() else listOf(child)
        }.filter { '.' !in it.localName && it.localName !in setOf("ControlTemplate", "DataTemplate", "ItemsPanelTemplate", "Style") }
        val visualChildren = nodes.filter { it.path.dropLast(1) == visualParent.path && it.path.size == visualParent.path.size + 1 }
            .sortedBy { it.path.last() }
        if (sourceChildren.size != visualChildren.size || !sourceChildren.zip(visualChildren).all { (source, visual) -> matchesType(source, visual) }) return null
        return sourceChildren.indexOf(tag).takeIf { it >= 0 }?.let { visualChildren[it] }
    }

    fun tag(file: XmlFile, selected: WinUIXamlVisualNode, allNodes: List<WinUIXamlVisualNode>, component: List<Int>): XmlTag? {
        if (selected.path.take(component.size) != component) return null
        val nodes = allNodes.filter { it.path.size >= component.size && it.path.take(component.size) == component }
        val tags = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java)
        tags.filter { node(it, nodes) == selected }.singleOrNull()?.let { return it }
        val ancestors = nodes.filter { it.path.size in component.size..selected.path.size &&
            selected.path.take(it.path.size) == it.path && it.path.take(component.size) == component }.sortedByDescending { it.path.size }
        return ancestors.firstNotNullOfOrNull { item ->
            if (item.name.isNotBlank()) tags.singleOrNull { it.xamlName() == item.name && node(it, nodes) == item } else null
        } ?: ancestors.firstNotNullOfOrNull { item ->
            if (item.name.isNotEmpty()) null else tags.filter { node(it, nodes) == item }.singleOrNull()
        }
    }
}
