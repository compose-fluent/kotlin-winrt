package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.xaml.*
import io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds
import kotlin.math.abs

internal enum class DesignerAlignment(val label: String) {
    LEFT("Align left"), CENTER("Align horizontal centers"), RIGHT("Align right"),
    TOP("Align top"), MIDDLE("Align vertical centers"), BOTTOM("Align bottom"),
    WIDTH("Match width"), HEIGHT("Match height")
}

internal fun designerAlign(selections: List<DesignerSelection>, operation: DesignerAlignment): List<Pair<DesignerSelection, Map<String, String?>>> {
    require(selections.size > 1) { "Select at least two source elements." }
    val parent = selections.last().pointer.element?.parentTag
    require(parent != null && selections.all { it.pointer.element?.parentTag == parent }) { "Align elements in the same container." }
    require(parent.localName in setOf("Grid", "Canvas")) { "This container controls child flow. Use its layout properties instead." }
    val anchor = selections.last().bounds
    return selections.dropLast(1).map { selection ->
        val b = selection.bounds
        val change = when (operation) {
            DesignerAlignment.LEFT -> DesignerTransform(anchor.x - b.x, 0.0)
            DesignerAlignment.CENTER -> DesignerTransform(anchor.x + anchor.width / 2 - b.x - b.width / 2, 0.0)
            DesignerAlignment.RIGHT -> DesignerTransform(anchor.x + anchor.width - b.x - b.width, 0.0)
            DesignerAlignment.TOP -> DesignerTransform(0.0, anchor.y - b.y)
            DesignerAlignment.MIDDLE -> DesignerTransform(0.0, anchor.y + anchor.height / 2 - b.y - b.height / 2)
            DesignerAlignment.BOTTOM -> DesignerTransform(0.0, anchor.y + anchor.height - b.y - b.height)
            DesignerAlignment.WIDTH -> DesignerTransform(0.0, 0.0, anchor.width - b.width, 0.0, true)
            DesignerAlignment.HEIGHT -> DesignerTransform(0.0, 0.0, 0.0, anchor.height - b.height, true)
        }
        selection to WinUIXamlDesignerEditing.layout(selection, change)
    }
}

internal data class DesignerSnap(val change: DesignerTransform, val vertical: Double? = null, val horizontal: Double? = null)
/** Compare rendered sibling edges/centers in DIP; Alt bypasses snapping in the canvas. */
internal fun designerSnap(bounds: WinUIXamlVisualBounds, change: DesignerTransform,
    others: List<WinUIXamlVisualBounds>, threshold: Double): DesignerSnap {
    fun nearest(values: List<Double>, guides: List<Double>): Pair<Double, Double>? =
        values.flatMap { value -> guides.map { guide -> (guide - value) to guide } }
            .filter { abs(it.first) <= threshold }.minByOrNull { abs(it.first) }
    val x = bounds.x + change.x; val y = bounds.y + change.y
    val w = bounds.width + change.width; val h = bounds.height + change.height
    val xs = if (!change.resize) listOf(x, x + w / 2, x + w) else
        if (change.x != 0.0) listOf(x) else if (change.width != 0.0) listOf(x + w) else emptyList()
    val ys = if (!change.resize) listOf(y, y + h / 2, y + h) else
        if (change.y != 0.0) listOf(y) else if (change.height != 0.0) listOf(y + h) else emptyList()
    val sx = nearest(xs, others.flatMap { listOf(it.x, it.x + it.width / 2, it.x + it.width) })
    val sy = nearest(ys, others.flatMap { listOf(it.y, it.y + it.height / 2, it.y + it.height) })
    val dx = sx?.first ?: 0.0; val dy = sy?.first ?: 0.0
    val result = if (!change.resize) change.copy(x = change.x + dx, y = change.y + dy) else change.copy(
        x = change.x + if (change.x != 0.0) dx else 0.0,
        y = change.y + if (change.y != 0.0) dy else 0.0,
        width = change.width + if (change.x != 0.0) -dx else dx,
        height = change.height + if (change.y != 0.0) -dy else dy)
    if (bounds.width + result.width < 1 || bounds.height + result.height < 1) return DesignerSnap(change)
    return DesignerSnap(result, sx?.second, sy?.second)
}

/** Palette defaults are authoring conveniences, never a second projection registry. */
internal val designerToolbox = linkedMapOf(
    "Button" to "Content=\"Button\"", "TextBlock" to "Text=\"Text\"", "TextBox" to "PlaceholderText=\"Enter text\" Width=\"160\"",
    "CheckBox" to "Content=\"CheckBox\"", "ToggleSwitch" to "Header=\"Toggle\"", "Slider" to "Width=\"160\"",
    "ProgressBar" to "Width=\"160\" Value=\"50\"", "Image" to "Width=\"100\" Height=\"100\"",
    "Grid" to "Width=\"200\" Height=\"150\"", "StackPanel" to "Width=\"200\" Height=\"150\"",
    "Canvas" to "Width=\"200\" Height=\"150\"", "Border" to "Width=\"200\" Height=\"150\" BorderThickness=\"1\" BorderBrush=\"Gray\"")

internal object DesignerInsertion {
    fun insert(project: Project, selection: DesignerSelection, typeName: String, x: Double? = null, y: Double? = null) {
        WriteCommandAction.writeCommandAction(project).withName("Insert $typeName").run<RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val selected = selection.pointer.element ?: error("Select a source container first.")
            check(selected.text == selection.text && selected.containingFile.isWritable) { "XAML changed. Select the insertion target again." }
            val catalog = WinUIXamlSymbols.catalog(selected.containingFile) ?: error("XAML metadata is loading.")
            val type = catalog.resolve(WinUIXamlCatalog.PRESENTATION, typeName) ?: error("$typeName is unavailable in this SDK.")
            require(catalog.isVisual(type) && typeName in designerToolbox) { "Choose a visual control from the toolbox." }
            // Walk up from a leaf. Never replace existing content or a binding.
            val target = generateSequence(selected) { it.parentTag }.firstNotNullOfOrNull { tag ->
                val owner = catalog.resolve(tag.namespace, tag.localName) ?: return@firstNotNullOfOrNull null
                val member = catalog.contentMember(owner) ?: return@firstNotNullOfOrNull null
                if (tag.getAttributeValue(member.name) != null) return@firstNotNullOfOrNull null
                val propertyTag = tag.subTags.firstOrNull { it.localName == "${tag.localName}.${member.name}" }
                val content = propertyTag ?: tag
                val children = content.subTags.filter { '.' !in it.localName }
                if (!member.collection && (children.isNotEmpty() || content.value.trimmedText.isNotEmpty())) null else tag to content
            } ?: error("Select a container with available content. Existing content is never replaced.")
            val (parent, content) = target
            val prefix = parent.getPrefixByNamespace(WinUIXamlCatalog.PRESENTATION).orEmpty()
            val name = if (prefix.isEmpty()) typeName else "$prefix:$typeName"
            val xml = selected.containingFile as com.intellij.psi.xml.XmlFile
            val root = xml.rootTag!!
            var xPrefix = root.getPrefixByNamespace(WinUIXamlCatalog.XAML)
            if (xPrefix.isNullOrEmpty()) { xPrefix = "x"; var i = 1; while (root.getNamespaceByPrefix(xPrefix).isNotEmpty()) xPrefix = "x${i++}" }
            val used = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java)
                .flatMap { it.attributes.toList() }.filter { it.localName == "Name" }.mapNotNull { it.value }.toSet()
            var number = 1
            while ("${typeName}${number}" in used) number++
            val element = XmlElementFactory.getInstance(project).createTagFromText("<$name ${designerToolbox.getValue(typeName)}/>")
            element.setAttribute("$xPrefix:Name", "${typeName}${number}")
            if (x != null && y != null && parent == selected && parent.localName in setOf("Canvas", "Grid")) {
                val left = (x - selection.bounds.x).coerceAtLeast(0.0).toInt().toString()
                val top = (y - selection.bounds.y).coerceAtLeast(0.0).toInt().toString()
                if (parent.localName == "Canvas") {
                    element.setAttribute("${parent.name}.Left", left); element.setAttribute("${parent.name}.Top", top)
                } else {
                    element.setAttribute("Margin", "$left,$top,0,0")
                    element.setAttribute("HorizontalAlignment", "Left"); element.setAttribute("VerticalAlignment", "Top")
                }
            }
            if (root.getNamespaceByPrefix(xPrefix) != WinUIXamlCatalog.XAML) root.setAttribute("xmlns:$xPrefix", WinUIXamlCatalog.XAML)
            content.addSubTag(element, false)
            documents.getDocument(xml)?.let(documents::doPostponedOperationsAndUnblockDocument)
        }
    }
}
