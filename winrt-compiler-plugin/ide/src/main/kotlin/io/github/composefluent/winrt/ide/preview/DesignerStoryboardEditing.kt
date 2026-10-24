package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog

internal data class DesignerAnimationTrack(val pointer: SmartPsiElementPointer<XmlTag>, val source: String,
    val label: String, val kind: String, val frames: List<DesignerAnimationFrame>)
internal data class DesignerAnimationFrame(val pointer: SmartPsiElementPointer<XmlTag>, val type: String,
    val time: String, val value: String?, val complex: Boolean)

/** Source editing of SDK keyframes. Interpolation stays in the generated SDK projection.
 * https://learn.microsoft.com/windows/apps/develop/motion/key-frame-and-easing-function-animations */
internal object DesignerStoryboardEditing {
    private val kinds = setOf("Double", "Color", "Point", "Object")
    private fun sdk(tag: XmlTag, name: String) = tag.namespace == WinUIXamlCatalog.PRESENTATION && tag.localName == name
    fun tracks(project: Project, path: String, key: String): List<DesignerAnimationTrack> {
        val file = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: return emptyList()
        val xml = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return emptyList()
        return tracks(xml, key)
    }
    fun tracks(xml: XmlFile, key: String): List<DesignerAnimationTrack> {
        val storyboard = PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java).filter {
            sdk(it, "Storyboard") && (it.getAttributeValue("Key", WinUIXamlCatalog.XAML)
                ?: it.getAttributeValue("Name", WinUIXamlCatalog.XAML)) == key
        }.singleOrNull() ?: return emptyList()
        // A preview-transformed owner name is not a source locator. Accept only
        // one source resource with this key, never guess among repeated templates.
        if (generateSequence(storyboard.parentTag) { it.parentTag }.any {
            it.localName in setOf("ControlTemplate", "DataTemplate", "Style", "VisualState")
        }) return emptyList()
        val pointers = SmartPointerManager.getInstance(xml.project)
        return (storyboard.subTags.toList() + storyboard.subTags.filter { sdk(it, "Storyboard.Children") }.flatMap { it.subTags.toList() })
            .mapNotNull { track ->
                val kind = track.localName.removeSuffix("AnimationUsingKeyFrames")
                if (track.namespace != WinUIXamlCatalog.PRESENTATION || kind !in kinds || track.localName != "${kind}AnimationUsingKeyFrames") return@mapNotNull null
                val entries = track.subTags.toList().flatMap { if (sdk(it, "${track.localName}.KeyFrames")) it.subTags.toList() else listOf(it) }
                val frames = entries.filter { it.namespace == WinUIXamlCatalog.PRESENTATION && it.localName.endsWith("${kind}KeyFrame") }.map {
                    DesignerAnimationFrame(pointers.createSmartPsiElementPointer(it), it.localName,
                        it.getAttributeValue("KeyTime").orEmpty(), it.getAttributeValue("Value"),
                        it.subTags.any { child -> child.localName.endsWith(".Value") })
                }
                DesignerAnimationTrack(pointers.createSmartPsiElementPointer(track), xml.text,
                    "${track.getAttributeValue("Storyboard.TargetName").orEmpty()} · ${track.getAttributeValue("Storyboard.TargetProperty").orEmpty()}", kind, frames)
            }
    }
    fun createTrack(project: Project, selection: DesignerSelection, key: String, propertyName: String,
        millis: Int, value: String, dependent: Boolean) {
        require(key.isNotBlank() && key.length <= 256) { "Enter a storyboard resource key (up to 256 characters)." }
        val property = selection.properties.singleOrNull { it.name == propertyName && it.writable && it.dependencyProperty }
            ?: error("Select a writable dependency property.")
        require(value.isNotBlank()) { "Enter the first keyframe value." }
        WinUIXamlDesignerEditing.validate(property, value)?.let { error(it) }
        val keyTime = time(millis)
        val targetProperty = if ('.' in propertyName) "($propertyName)" else propertyName
        val kind = when (property.type.substringAfterLast('.')) {
            "Double" -> "Double"
            "Point" -> "Point"
            "Color" -> "Color"
            else -> "Object"
        }
        WriteCommandAction.writeCommandAction(project).withName("Create XAML animation track").run<RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val target = selection.pointer.element ?: error("Select an element first.")
            check(target.isValid && target.containingFile.isWritable && target.text == selection.text) { "XAML changed. Select the element again." }
            val root = (target.containingFile as? XmlFile)?.rootTag ?: error("Open a XAML document.")
            check(generateSequence(target) { it.parentTag }.none { it.localName in setOf("ControlTemplate", "DataTemplate", "Style") }) { "Open the template's animation in XAML." }
            val name = target.getAttributeValue("Name", WinUIXamlCatalog.XAML)?.takeIf(String::isNotBlank)
                ?: error("Give the selected element an x:Name first.")
            val all = listOf(root) + PsiTreeUtil.findChildrenOfType(root, XmlTag::class.java)
            check(all.count { it.getAttributeValue("Name", WinUIXamlCatalog.XAML) == name } == 1) { "The target name is ambiguous." }
            val properties = root.subTags.filter { sdk(it, "${root.localName}.Resources") }
            check(properties.size <= 1) { "Duplicate resource collections. Edit them in XAML." }
            val resources = properties.singleOrNull()
            val dictionaries = resources?.subTags.orEmpty().filter { sdk(it, "ResourceDictionary") }
            check(dictionaries.size <= 1 && (dictionaries.isEmpty() || resources!!.subTags.size == 1)) { "The resource collection is ambiguous." }
            val owner = dictionaries.singleOrNull() ?: resources
            val matches = all.filter { (it.getAttributeValue("Key", WinUIXamlCatalog.XAML) ?: it.getAttributeValue("Name", WinUIXamlCatalog.XAML)) == key }
            check(matches.size <= 1 && matches.all { sdk(it, "Storyboard") && it.parentTag == owner }) { "This resource key belongs to a different resource or namescope." }
            val existing = matches.singleOrNull()
            check(existing == null || PsiTreeUtil.findChildrenOfType(existing, XmlTag::class.java).none {
                it.getAttributeValue("Storyboard.TargetName") == name && it.getAttributeValue("Storyboard.TargetProperty") == targetProperty
            }) { "A track already animates this property. Edit its keyframes." }
            val track = root.createChildTag("${kind}AnimationUsingKeyFrames", WinUIXamlCatalog.PRESENTATION, null, false)
            track.setAttribute("Storyboard.TargetName", name)
            track.setAttribute("Storyboard.TargetProperty", targetProperty)
            if (dependent) track.setAttribute("EnableDependentAnimation", "True")
            val frame = track.createChildTag("${if (kind == "Object") "Discrete" else "Linear"}${kind}KeyFrame", WinUIXamlCatalog.PRESENTATION, null, false)
            frame.setAttribute("KeyTime", keyTime); frame.setAttribute("Value", value)
            track.addSubTag(frame, false)
            if (existing != null) {
                val children = existing.subTags.filter { sdk(it, "Storyboard.Children") }
                check(children.size <= 1) { "Duplicate animation collections. Edit them in XAML." }
                (children.singleOrNull() ?: existing).addSubTag(track, false)
            } else {
                val storyboard = root.createChildTag("Storyboard", WinUIXamlCatalog.PRESENTATION, null, false)
                val prefix = root.getPrefixByNamespace(WinUIXamlCatalog.XAML)?.takeIf(String::isNotEmpty) ?: "x".also { storyboard.setAttribute("xmlns:x", WinUIXamlCatalog.XAML) }
                storyboard.setAttribute("$prefix:Key", key)
                storyboard.addSubTag(track, false)
                if (owner != null) owner.addSubTag(storyboard, false)
                else {
                    val collection = root.createChildTag("${root.localName}.Resources", WinUIXamlCatalog.PRESENTATION, null, false)
                    collection.addSubTag(storyboard, false)
                    root.addSubTag(collection, true)
                }
            }
            documents.getDocument(root.containingFile)?.let(documents::doPostponedOperationsAndUnblockDocument)
        }
    }
    fun time(millis: Int): String {
        require(millis in 0..86_400_000) { "Use a position between 0 and 86400000 ms." }
        return "${millis / 3600000}:${millis / 60000 % 60}:${millis / 1000 % 60}.${(millis % 1000).toString().padStart(3, '0')}"
    }
    fun write(project: Project, track: DesignerAnimationTrack, frame: DesignerAnimationFrame?, millis: Int,
        value: String, interpolation: String, remove: Boolean = false) {
        val keyTime = time(millis)
        require(remove || value.isNotBlank()) { "Enter a keyframe value." }
        require(interpolation in if (track.kind == "Object") listOf("Discrete") else listOf("Linear", "Discrete")) { "Choose an interpolation." }
        if (!remove && track.kind == "Double" && !value.startsWith("{")) require(value.toDoubleOrNull()?.isFinite() == true) { "Enter a finite number." }
        WriteCommandAction.writeCommandAction(project).withName(if (remove) "Delete XAML keyframe" else "Edit XAML keyframe").run<RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val tag = track.pointer.element ?: error("The animation no longer exists.")
            check(tag.isValid && tag.containingFile.isWritable && tag.containingFile.text == track.source) { "XAML changed. Select the animation again." }
            val existing = frame?.pointer?.element
            check(frame == null || existing != null && existing.isValid && !frame.complex) { "This keyframe uses an object value. Edit it in XAML." }
            if (remove) requireNotNull(existing) { "Select a keyframe." }.delete()
            else if (existing != null) {
                // Preserve spline/easing and object metadata when editing a value.
                existing.setAttribute("KeyTime", keyTime)
                existing.setAttribute("Value", value)
            } else {
                val collections = tag.subTags.filter { sdk(it, "${tag.localName}.KeyFrames") }
                check(collections.size <= 1) { "Duplicate keyframe collections. Edit them in XAML." }
                val owner = collections.singleOrNull() ?: tag
                val added = owner.createChildTag("$interpolation${track.kind}KeyFrame", WinUIXamlCatalog.PRESENTATION, null, false)
                added.setAttribute("KeyTime", keyTime)
                added.setAttribute("Value", value)
                owner.addSubTag(added, false)
            }
            documents.getDocument(tag.containingFile)?.let(documents::doPostponedOperationsAndUnblockDocument)
        }
    }
}
