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

    private fun resource(value: com.intellij.psi.xml.XmlAttributeValue?): XmlTag? {
        if (value == null || !value.value.trimStart().startsWith("{StaticResource ")) return null
        return io.github.composefluent.winrt.ide.resources.WinRTResourceReferences.reference(value)
            ?.multiResolve(false).orEmpty().mapNotNull {
                PsiTreeUtil.getParentOfType(it.element, XmlTag::class.java)
            }.distinct().singleOrNull()
    }

    private fun sdk(tag: XmlTag, name: String) = tag.namespace ==
        io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog.PRESENTATION && tag.localName == name

    private fun referencedTemplate(tag: XmlTag): XmlTag? {
        if (tag.getAttribute("Template") != null)
            return resource(tag.getAttribute("Template")?.valueElement)?.takeIf { sdk(it, "ControlTemplate") }
        val localTemplate = tag.subTags.filter { it.localName.endsWith(".Template") }
        if (localTemplate.isNotEmpty()) return localTemplate.singleOrNull()?.subTags?.singleOrNull()?.takeIf { sdk(it, "ControlTemplate") }
        val inline = tag.subTags.filter { it.localName.endsWith(".Style") }.flatMap { it.subTags.toList() }
        val style = (inline + listOfNotNull(resource(tag.getAttribute("Style")?.valueElement))).singleOrNull() ?: return null
        val visited = hashSetOf<XmlTag>()
        fun fromStyle(current: XmlTag): XmlTag? {
            if (!sdk(current, "Style") || !visited.add(current) || visited.size > 64) return null
            val setters = (current.subTags.toList() + current.subTags.filter { sdk(it, "Style.Setters") }.flatMap { it.subTags.toList() })
                .filter { sdk(it, "Setter") && it.getAttributeValue("Property") == "Template" }
            if (setters.isNotEmpty()) {
                val setter = setters.singleOrNull() ?: return null
                val value = setter.getAttribute("Value")
                return (if (value != null) resource(value.valueElement) else setter.subTags.singleOrNull()
                    ?.takeIf { sdk(it, "Setter.Value") }?.subTags?.singleOrNull())?.takeIf { sdk(it, "ControlTemplate") }
            }
            return resource(current.getAttribute("BasedOn")?.valueElement)?.let(::fromStyle)
        }
        return fromStyle(style)
    }
    fun sourceTags(file: XmlFile): List<XmlTag> {
        val local = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).toList()
        return (local + local.filterNot { it.inTemplate() }.mapNotNull(::referencedTemplate).flatMap {
            listOf(it) + PsiTreeUtil.findChildrenOfType(it, XmlTag::class.java)
        }).distinct()
    }

    fun node(tag: XmlTag, nodes: List<WinUIXamlVisualNode>, instance: List<Int>? = null,
        consumerFile: XmlFile? = tag.containingFile as? XmlFile): WinUIXamlVisualNode? {
        if (tag.inTemplate()) {
            // Resolve the exact consuming control, then scope template names to its
            // realized subtree. A shared template needs the clicked instance path;
            // source-only navigation never guesses between repeated realizations.
            val template = generateSequence(tag) { it.parentTag }.firstOrNull {
                it.localName in setOf("ControlTemplate", "DataTemplate", "ItemsPanelTemplate", "Style")
            } ?: return null
            if (template.localName != "ControlTemplate") return null
            val property = template.parentTag ?: return null
            val owners = if (property.localName.endsWith(".Template")) listOfNotNull(property.parentTag)
                else consumerFile?.let { PsiTreeUtil.findChildrenOfType(it, XmlTag::class.java) }
                    .orEmpty().filter { !it.inTemplate() && referencedTemplate(it) == template }
            val owner = owners.mapNotNull { node(it, nodes, instance, consumerFile) }.filter {
                instance == null || instance.size > it.path.size && instance.take(it.path.size) == it.path
            }.singleOrNull() ?: return null
            val name = tag.xamlName()
            if (name != null) return nodes.singleOrNull { it.name == name && matchesType(tag, it) &&
                it.path.size > owner.path.size && it.path.take(owner.path.size) == owner.path }
            // An inline template is a single known realization. Unnamed parts
            // are editable only when their complete direct sibling sequence
            // matches; a generated presenter/wrapper makes the mapping fail.
            if (tag == template || '.' in tag.localName) return null
            val parent = tag.parentTag?.let { if (it.localName.endsWith(".Children")) it.parentTag else it } ?: return null
            val visualParent = if (parent == template) owner else node(parent, nodes, instance, consumerFile) ?: return null
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
            node(sourceParent, nodes, instance, consumerFile)?.let { visualParent ->
                nodes.singleOrNull { matchesType(tag, it) &&
                    it.path.dropLast(1) == visualParent.path && it.path.size == visualParent.path.size + 1 }
                    ?.let { return it }
            }
        }
        // Ordered siblings are exact only when the entire direct visual sequence
        // agrees. Never guess through control-template or item-container children.
        val visualParentTag = sourceParent?.let { if ('.' in it.localName) it.parentTag else it }
        if (visualParentTag != null) node(visualParentTag, nodes, instance, consumerFile)?.let { visualParent ->
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
        val tags = sourceTags(file)
        tags.filter { node(it, nodes, selected.path, file) == selected }.singleOrNull()?.let { return it }
        val ancestors = nodes.filter { it.path.size in component.size..selected.path.size &&
            selected.path.take(it.path.size) == it.path && it.path.take(component.size) == component }.sortedByDescending { it.path.size }
        return ancestors.firstNotNullOfOrNull { item ->
            if (item.name.isNotBlank()) tags.singleOrNull { it.xamlName() == item.name && node(it, nodes, selected.path, file) == item } else null
        } ?: ancestors.firstNotNullOfOrNull { item ->
            if (item.name.isNotEmpty()) null else tags.filter { node(it, nodes, selected.path, file) == item }.singleOrNull()
        }
    }
}
