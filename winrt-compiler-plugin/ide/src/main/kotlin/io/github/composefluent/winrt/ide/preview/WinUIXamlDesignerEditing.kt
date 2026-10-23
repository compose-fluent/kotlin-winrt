package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import io.github.composefluent.winrt.ide.xaml.*
import io.github.composefluent.winrt.runtime.*
import kotlin.math.round

/** Source editing belongs to the IDE, not the runtime projection or preview host.
 * Setter availability comes from the existing normalized WinMD catalog (CsWinRT
 * class-member/interface ownership). Visual snapshots never become editable state. */
internal data class DesignerProperty(val name: String, val type: String, val value: String?,
    val choices: List<String>, val writable: Boolean, val elementValue: Boolean)
internal data class DesignerSelection(val pointer: SmartPsiElementPointer<XmlTag>, val text: String,
    val properties: List<DesignerProperty>, val bounds: WinUIXamlVisualBounds, val parent: String,
    val attributes: Map<String, String>, val resourceKeys: List<String> = emptyList())
internal data class DesignerTransform(val x: Double, val y: Double, val width: Double = 0.0, val height: Double = 0.0,
    val resize: Boolean = false)

internal object WinUIXamlDesignerEditing {
    fun selection(project: Project, path: String, selected: WinUIXamlVisualNode,
        nodes: List<WinUIXamlVisualNode>): DesignerSelection? {
        val file = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: return null
        val xml = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
        // Editing must be exact. Navigation's nearest-source-ancestor fallback is unsafe here.
        val tag = PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java)
            .filter { WinUIXamlSourceSelection.node(it, nodes) == selected }.singleOrNull() ?: return null
        val catalog = WinUIXamlSymbols.catalog(xml) ?: return null
        val members = WinUIXamlSymbols.members(tag).filterNot { it.isEvent }.toMutableList()
        tag.parentTag?.let { parent ->
            catalog.resolve(parent.namespace, parent.localName)?.let { owner ->
                members += catalog.attachedMembers(owner).map { it.copy(name = "${parent.name}.${it.name}") }
            }
        }
        val properties = members.distinctBy { it.name }.map { member ->
            val choices = if (member.typeName in setOf("Boolean", "System.Boolean", "boolean")) listOf("True", "False")
                else catalog.types[member.typeName]?.enumMembers?.map { it.name }.orEmpty()
            DesignerProperty(member.name, member.typeName, tag.getAttributeValue(member.name), choices,
                member.isWritable, tag.subTags.any { it.localName.substringAfter('.', "") == member.name.substringAfter('.') })
        }.sortedWith(compareBy({ it.name !in layoutProperties }, { it.name }))
        return DesignerSelection(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag), tag.text,
            properties, selected.bounds, tag.parentTag?.localName.orEmpty(), tag.attributes.associate { it.name to it.value.orEmpty() },
            io.github.composefluent.winrt.ide.resources.WinRTResourceReferences.resourceKeys(xml,
                project.getService(io.github.composefluent.winrt.ide.resources.WinRTResourceIndex::class.java).forFile(file.path), tag, null)
                .map { it.value }.distinct().sorted())
    }

    fun apply(project: Project, selection: DesignerSelection, changes: Map<String, String?>, title: String) =
        applyBatch(project, listOf(selection to changes), title)

    /** Validate the entire command before touching any tag. One gesture = one undo. */
    fun applyBatch(project: Project, edits: List<Pair<DesignerSelection, Map<String, String?>>>, title: String) {
        WriteCommandAction.writeCommandAction(project).withName(title).run<RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val validated = edits.map { (selection, changes) ->
                val tag = selection.pointer.element ?: error("The selected element no longer exists. Select it again.")
                check(tag.isValid && tag.text == selection.text) { "XAML changed during this edit. Select the element again." }
                check(tag.containingFile.isWritable) { "This XAML file is read only." }
                changes.forEach { (name, value) ->
                    val property = selection.properties.firstOrNull { it.name == name }
                        ?: error("$name is not available on this element.")
                    check(property.writable) { "$name is read only." }
                    check(!property.elementValue) { "$name uses a property element. Edit it in XAML." }
                    value?.let { validate(property, it)?.let { message -> error(message) } }
                }
                tag to changes
            }
            validated.forEach { (tag, changes) -> changes.forEach { (name, value) -> tag.setAttribute(name, value) } }
            validated.map { it.first.containingFile }.distinct().forEach { file ->
                documents.getDocument(file)?.let(documents::doPostponedOperationsAndUnblockDocument)
            }
        }
    }

    fun validate(property: DesignerProperty, value: String): String? {
        if (value.startsWith("{")) return null // Keep XAML bindings/resources as source expressions.
        if (property.choices.isNotEmpty() && value !in property.choices) return "Choose a valid ${property.name} value."
        val scalar = property.type.substringAfterLast('.')
        if (scalar in setOf("Double", "Single", "Float", "Int32", "UInt32", "Int64", "UInt64", "Int16", "UInt16", "Byte")) {
            if (value == "Auto" && property.name in setOf("Width", "Height")) return null
            val number = value.toDoubleOrNull()
            if (number == null || !number.isFinite()) return "${property.name} requires a finite number."
            if (scalar !in setOf("Double", "Single", "Float") && number % 1.0 != 0.0) return "${property.name} requires a whole number."
            if ((scalar.startsWith("U") || scalar == "Byte" || property.name in setOf("Width", "Height", "MinWidth", "MinHeight", "MaxWidth", "MaxHeight")) && number < 0) return "${property.name} cannot be negative."
        }
        if (scalar == "Thickness" && thickness(value) == null) return "Use one, two or four comma-separated numbers."
        return null
    }

    /** Preserve panel semantics: Canvas uses attached offsets; other panels use
     * margins and alignment. A gesture never replaces a binding or property element. */
    fun layout(selection: DesignerSelection, change: DesignerTransform): Map<String, String?> {
        val attributes = selection.attributes
        fun literal(name: String, fallback: Double = 0.0): Double {
            val value = attributes[name] ?: return fallback
            return value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: error("$name is not a numeric local value. Edit it in Properties.")
        }
        val result = linkedMapOf<String, String?>()
        val width = (selection.bounds.width + change.width).coerceAtLeast(1.0)
        val height = (selection.bounds.height + change.height).coerceAtLeast(1.0)
        if (change.resize) {
            // A side handle owns only its axis; preserve Auto, bindings and
            // stretch alignment on the untouched axis.
            listOf("Width" to change.width, "Height" to change.height).forEach { (name, delta) ->
                if (delta != 0.0) {
                    check(attributes[name]?.startsWith("{") != true) { "$name is bound. Edit it in Properties before resizing." }
                    result[name] = number(if (name == "Width") width else height)
                }
            }
        }
        if (selection.parent == "Canvas") {
            val prefix = selection.properties.firstOrNull { it.name.endsWith("Canvas.Left") }?.name?.removeSuffix("Left") ?: "Canvas."
            if (change.x != 0.0) result[prefix + "Left"] = number(literal(prefix + "Left") + change.x)
            if (change.y != 0.0) result[prefix + "Top"] = number(literal(prefix + "Top") + change.y)
        } else {
            val margin = attributes["Margin"]?.let { thickness(it) ?: error("Margin is an expression. Edit it in Properties before dragging.") }
                ?: doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            fun axis(alignment: String, leading: Int, trailing: Int, offset: Double, sizeDelta: Double) {
                if (offset == 0.0 && sizeDelta == 0.0) return
                val align = attributes[alignment] ?: "Stretch"
                check(align in setOf("Left", "Right", "Top", "Bottom", "Center", "Stretch")) { "$alignment is an expression. Edit it in Properties." }
                when (align) {
                    "Right", "Bottom" -> margin[trailing] -= offset + sizeDelta
                    "Center" -> margin[leading] += 2 * offset + sizeDelta
                    "Stretch" -> {
                        margin[leading] += offset
                        if (change.resize) result[alignment] = if (leading == 0) "Left" else "Top"
                        else margin[trailing] -= offset
                    }
                    else -> margin[leading] += offset
                }
            }
            axis("HorizontalAlignment", 0, 2, change.x, if (change.resize) width - selection.bounds.width else 0.0)
            axis("VerticalAlignment", 1, 3, change.y, if (change.resize) height - selection.bounds.height else 0.0)
            result["Margin"] = margin.joinToString(",", transform = ::number)
        }
        return result
    }
    internal fun thickness(value: String): DoubleArray? {
        val numbers = value.split(',').map { it.trim().toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
        return when (numbers.size) { 1 -> DoubleArray(4) { numbers[0] }; 2 -> doubleArrayOf(numbers[0], numbers[1], numbers[0], numbers[1]); 4 -> numbers.toDoubleArray(); else -> null }
    }
    private fun number(value: Double) = java.math.BigDecimal.valueOf(round(value * 100) / 100).stripTrailingZeros().toPlainString()
    private val layoutProperties = setOf("Width", "Height", "Margin", "HorizontalAlignment", "VerticalAlignment", "Canvas.Left", "Canvas.Top", "Grid.Row", "Grid.Column")
}
