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
