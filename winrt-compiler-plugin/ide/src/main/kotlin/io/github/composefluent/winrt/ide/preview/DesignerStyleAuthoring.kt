package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.resources.WinRTResourceIndex
import io.github.composefluent.winrt.ide.resources.WinRTResourceReferences
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinUIXamlSymbols

/** Source resource ownership follows the SDK Resources member. Construct a
 * detached owner subtree before committing the resource and consumer together. */
internal object DesignerStyleAuthoring {
    fun create(project: Project, selection: DesignerSelection, key: String, extract: Boolean,
        catalog: WinUIXamlCatalog? = selection.pointer.element?.let { WinUIXamlSymbols.catalog(it.containingFile) }) {
        WriteCommandAction.writeCommandAction(project).withName(if (extract) "Extract XAML style" else "Create XAML style").run<RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val tag = selection.pointer.element ?: error("Select a source element first.")
            check(tag.isValid && tag.containingFile.isWritable && tag.text == selection.text) { "XAML changed. Select the element again." }
            require(key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Use a resource key starting with a letter or underscore." }
            check(selection.state == null && selection.style == null) { "Return to element properties before creating a style." }
            check(tag.getAttribute("Style") == null && tag.subTags.none { it.localName.substringAfter('.', "") == "Style" }) {
                "This element already has a style. Edit that style before creating another."
            }
            val schema = requireNotNull(catalog) { "Wait for the XAML type catalog." }
            val type = schema.resolve(tag.namespace, tag.localName) ?: error("The element type is unavailable.")
            check(schema.members(type).any { it.name == "Style" && it.isWritable }) { "This element does not expose a writable Style." }
            val owner = generateSequence(tag.parentTag) { it.parentTag }.takeWhile {
                it.namespace != WinUIXamlCatalog.PRESENTATION || it.localName !in setOf("ControlTemplate", "DataTemplate")
            }.firstOrNull { parent -> schema.resolve(parent.namespace, parent.localName)?.let { parentType ->
                schema.members(parentType).any { it.name == "Resources" }
            } == true } ?: error("Select an element inside a source container with Resources.")
            val file = tag.containingFile as XmlFile
            val lookup = file.originalFile.virtualFile?.path?.let { project.service<WinRTResourceIndex>().forFile(it) }
            check(WinRTResourceReferences.resourceKeys(file, lookup, tag, key).isEmpty()) { "The resource key '$key' already exists in scope." }
            val path = mutableListOf<Int>()
            var current = tag
            while (current != owner) {
                val parent = requireNotNull(current.parentTag)
                path.add(0, parent.subTags.indexOf(current)); current = parent
            }
            val replacement = owner.copy() as XmlTag
            val consumer = path.fold(replacement) { parent, index -> parent.subTags[index] }
            val collections = replacement.subTags.filter { it.localName == "${owner.localName}.Resources" && it.namespace == owner.namespace }
            check(collections.size <= 1) { "This container has multiple Resources properties. Edit it in XAML." }
            val resources = collections.singleOrNull() ?: replacement.createChildTag("${owner.name}.Resources", owner.namespace, null, false)
            val dictionaries = resources.subTags.filter { it.localName == "ResourceDictionary" && it.namespace == WinUIXamlCatalog.PRESENTATION }
            check(dictionaries.size <= 1 && (dictionaries.isEmpty() || resources.subTags.size == 1)) { "The Resources declaration is ambiguous. Edit it in XAML." }
            val dictionary = dictionaries.singleOrNull() ?: resources
            check(dictionary.getAttribute("Source") == null) { "Create the style in the referenced dictionary first." }
            val style = dictionary.createChildTag("Style", WinUIXamlCatalog.PRESENTATION, null, false)
            val x = owner.getPrefixByNamespace(WinUIXamlCatalog.XAML)?.takeIf(String::isNotEmpty) ?: "x".also { style.setAttribute("xmlns:x", WinUIXamlCatalog.XAML) }
            style.setAttribute("$x:Key", key)
            val prefix = tag.namespacePrefix
            if (prefix.isNotEmpty()) style.setAttribute("xmlns:$prefix", tag.namespace)
            style.setAttribute("TargetType", tag.name)
            if (extract) selection.properties.filter { it.writable && it.dependencyProperty && !it.elementValue && it.name !in setOf("Name", "Style") }.forEach { property ->
                val attribute = consumer.getAttribute(property.name) ?: return@forEach
                val value = attribute.value ?: return@forEach
                // Expressions and names stay at their original source scope.
                if (value.startsWith("{") || attribute.namespace.isNotEmpty()) return@forEach
                val setter = style.createChildTag("Setter", WinUIXamlCatalog.PRESENTATION, null, false)
                setter.setAttribute("Property", property.name); setter.setAttribute("Value", value)
                style.addSubTag(setter, false); attribute.delete()
            }
            dictionary.addSubTag(style, false)
            if (collections.isEmpty()) replacement.addSubTag(resources, true)
            consumer.setAttribute("Style", "{StaticResource $key}")
            owner.replace(replacement)
            documents.getDocument(file)?.let(documents::doPostponedOperationsAndUnblockDocument)
        }
    }
}
