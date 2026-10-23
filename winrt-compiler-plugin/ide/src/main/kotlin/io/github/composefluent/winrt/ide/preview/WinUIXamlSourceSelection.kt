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

    fun node(tag: XmlTag, nodes: List<WinUIXamlVisualNode>): WinUIXamlVisualNode? {
        if (tag.inTemplate()) return null
        val name = tag.xamlName()
        if (name != null) return nodes.singleOrNull { it.name == name }
        val type = tag.localName
        if ('.' in type) return null
        // A direct authored child is distinct from same-type template descendants.
        // Only narrow this way when the source parent has one child of this type.
        val sourceParent = tag.parentTag
        if (sourceParent != null && sourceParent.subTags.count { it.localName == type } == 1) {
            node(sourceParent, nodes)?.let { visualParent ->
                nodes.singleOrNull { it.typeName.substringAfterLast('.') == type &&
                    it.path.dropLast(1) == visualParent.path && it.path.size == visualParent.path.size + 1 }
                    ?.let { return it }
            }
        }
        val parentName = generateSequence(tag.parentTag) { it.parentTag }.firstNotNullOfOrNull { it.xamlName() }
        val parent = parentName?.let { value -> nodes.singleOrNull { it.name == value } } ?: if (parentName != null) return null else null
        return nodes.filter { it.typeName.substringAfterLast('.') == type &&
            (parent == null || it.path.size > parent.path.size && it.path.take(parent.path.size) == parent.path) }.singleOrNull()
    }

    fun tag(file: XmlFile, selected: WinUIXamlVisualNode, nodes: List<WinUIXamlVisualNode>, component: List<Int>): XmlTag? {
        val tags = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filterNot { it.inTemplate() }
        tags.filter { node(it, nodes) == selected }.singleOrNull()?.let { return it }
        val ancestors = nodes.filter { it.path.size in component.size..selected.path.size &&
            selected.path.take(it.path.size) == it.path && it.path.take(component.size) == component }.sortedByDescending { it.path.size }
        return ancestors.firstNotNullOfOrNull { item ->
            if (item.name.isNotBlank()) tags.singleOrNull { it.xamlName() == item.name } else null
        } ?: ancestors.firstNotNullOfOrNull { item ->
            if (item.name.isNotEmpty()) null else tags.filter { node(it, nodes) == item }.singleOrNull()
        }
    }
}
