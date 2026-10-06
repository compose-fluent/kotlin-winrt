package io.github.composefluent.winrt.ide.resources

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import java.nio.file.Files
import java.nio.file.Path

object WinRTReswTranslations {
    /** Call on a background thread. Unsaved documents take precedence over disk text. */
    fun inspect(project: Project, entries: List<WinRTResourceEntry>): List<String> {
        val texts = entries.filter { it.target.endsWith(".resw", true) }.mapNotNull { entry ->
            val cached = ReadAction.compute<String?, RuntimeException> {
                LocalFileSystem.getInstance().findFileByPath(entry.source.replace('\\', '/'))
                    ?.let { FileDocumentManager.getInstance().getCachedDocument(it)?.text }
            }
            (cached ?: runCatching { Files.readString(Path.of(entry.source)) }.getOrNull())?.let { entry to it }
        }
        return ReadAction.compute<List<String>, RuntimeException> {
            val errors = mutableListOf<String>()
            val keySets = texts.mapNotNull { (entry, text) ->
                val xml = PsiFileFactory.getInstance(project).createFileFromText("Resources.resw", XmlFileType.INSTANCE, text) as XmlFile
                if (xml.rootTag?.localName != "root" || PsiTreeUtil.findChildOfType(xml, PsiErrorElement::class.java) != null) {
                    errors += "Invalid .resw XML: ${entry.target}"
                    null
                } else {
                    errors += WinRTXmlForms.snapshot(xml).errors.map { "${entry.target}: $it" }
                    entry.target to xml.rootTag!!.findSubTags("data").mapNotNull { it.getAttributeValue("name") }.toSet()
                }
            }
            keySets.groupBy { (target, _) ->
                val parts = target.split('/')
                if (parts.size >= 3 && parts[0].equals("Strings", true)) (parts.take(1) + parts.drop(2)).joinToString("/") else target
            }.values.filter { it.size > 1 }.forEach { languages ->
                val union = languages.flatMap { it.second }.toSet()
                languages.forEach { (target, keys) -> (union - keys).sorted().forEach { errors += "$target: missing translation '$it'" } }
            }
            errors
        }
    }
}
