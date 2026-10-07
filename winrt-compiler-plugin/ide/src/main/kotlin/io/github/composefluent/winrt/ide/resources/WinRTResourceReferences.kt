package io.github.composefluent.winrt.ide.resources

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.*
import com.intellij.util.ProcessingContext
import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinRTXamlResourceExpression
import io.github.composefluent.winrt.ide.xaml.WinRTXamlSymbols
import java.net.URI

class WinRTResourceReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
                WinRTResourceReferences.reference(element)?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
        }
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttributeValue::class.java), provider)
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlText::class.java), provider)
    }
}

class WinRTResourceReferenceAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val reference = WinRTResourceReferences.reference(element) ?: return
        if (reference.multiResolve(false).isEmpty()) holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
            "No resource source candidate is known here. Runtime resources may be supplied by dependencies; stage the package to inspect them.")
            .range(reference.rangeInElement.shiftRight(element.textRange.startOffset)).create()
    }
}

internal object WinRTResourceReferences {
    private enum class Kind { File, String, Uid, Key }

    internal fun reference(element: PsiElement): PsiPolyVariantReference? {
        if (element !is XmlAttributeValue && element !is XmlText) return null
        val file = element.containingFile as? XmlFile ?: return null
        val lookup = file.project.service<WinRTResourceIndex>().forFile(file.virtualFile?.path ?: return null) ?: return null
        val attribute = (element as? XmlAttributeValue)?.parent as? XmlAttribute
        val value = when (element) { is XmlAttributeValue -> element.value; is XmlText -> element.value.trim(); else -> return null }
        val key = WinRTXamlResourceExpression.parse(value)?.key ?: value.takeIf {
            attribute?.localName == "ResourceKey" && attribute.parent.localName.removeSuffix("Extension") in
                setOf("StaticResource", "ThemeResource")
        }
        val kind = when {
            key != null && file.virtualFile.extension.equals("xaml", true) -> Kind.Key
            attribute?.localName == "Uid" && attribute.namespace == WinRTXamlCatalog.XAML -> Kind.Uid
            value.startsWith("ms-resource:", true) -> Kind.String
            value.startsWith("ms-appx:", true) -> Kind.File
            attribute != null && file.rootTag?.localName == "Package" && attribute.localName.endsWith("Logo") -> Kind.File
            attribute != null && file.virtualFile.extension.equals("xaml", true) &&
                WinRTXamlSymbols.members(attribute.parent).firstOrNull { it.name == attribute.localName }?.typeName in
                setOf("Windows.Foundation.Uri", "Microsoft.UI.Xaml.Media.ImageSource") && !value.startsWith('{') -> Kind.File
            else -> return null
        }
        if (value.isBlank()) return null
        val token = key ?: value
        if (kind == Kind.File && token.contains(':') && !token.startsWith("ms-appx:", true)) return null
        val range = ElementManipulators.getValueTextRange(element)
        return object : PsiPolyVariantReferenceBase<PsiElement>(element, range, true) {
            override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
                val targets = when (kind) {
                    Kind.File -> files(file, lookup, token)
                    Kind.String -> strings(file, lookup, token)
                    Kind.Uid -> strings(file, lookup, token, prefix = true)
                    Kind.Key -> resourceKeys(file, lookup, (attribute?.parent ?: (element.parent as? XmlTag)), token)
                }
                return targets.distinct().map { PsiElementResolveResult(it) }.toTypedArray()
            }
            override fun handleElementRename(newElementName: String): PsiElement {
                val targets = multiResolve(false)
                if (kind == Kind.Uid || targets.size != 1) return element
                val name = when (kind) {
                    Kind.Key -> token
                    Kind.File, Kind.String -> path(token)?.substringAfterLast('/') ?: return element
                    else -> return element
                }
                if (kind == Kind.File && (targets.single().element as? PsiFile)?.name != name) return element
                val offset = value.lastIndexOf(name).takeIf { it >= 0 } ?: return element
                return ElementManipulators.handleContentChange(element, range, value.replaceRange(offset, offset + name.length, newElementName))
            }
        }
    }

    private fun xml(file: PsiFile, path: String): XmlFile? = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))
        ?.let { PsiManager.getInstance(file.project).findFile(it) as? XmlFile }

    private fun path(value: String): String? = runCatching {
        val normalized = value.replace('\\', '/')
        val uri = URI(normalized)
        if (uri.scheme != null) {
            if (uri.rawAuthority.orEmpty().isNotEmpty()) return null // A foreign package has its own resource map.
            (uri.path ?: uri.schemeSpecificPart).trimStart('/')
        } else normalized.trimStart('/')
    }.getOrNull()?.takeUnless { it.split('/').any { part -> part == ".." } }

    private fun files(file: XmlFile, lookup: WinRTResourceLookup, value: String): List<PsiElement> {
        val logical = path(value)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val priTargets = lookup.pri.filter { candidate ->
            runCatching { URI(candidate.resourceUri).path?.substringAfter("/Files/", "")?.equals(logical, true) == true }.getOrDefault(false)
        }.map { it.value.replace('\\', '/').trimStart('/') }.toSet()
        val matches = lookup.entries.filter { entry -> entry.target.equals(logical, true) || entry.target in priTargets ||
            WinRTResourceCatalog.family(entry.target).equals(logical, true) }
        val xaml = lookup.xamlFiles.filter { it.replace('\\', '/').endsWith("/$logical", true) }
        val relative = file.virtualFile.parent?.findFileByRelativePath(logical)?.let { listOf(it.path) }.orEmpty()
        return (matches.map { it.source } + xaml + relative).distinct().mapNotNull { source ->
            LocalFileSystem.getInstance().findFileByPath(source.replace('\\', '/'))?.let { PsiManager.getInstance(file.project).findFile(it) }
        }
    }

    private fun strings(file: XmlFile, lookup: WinRTResourceLookup, value: String, prefix: Boolean = false): List<PsiElement> {
        val logical = (if (prefix) value else path(value)) ?: return emptyList()
        val key = logical.substringAfterLast('/')
        val map = logical.substringBeforeLast('/', "Resources").substringAfterLast('/')
        return lookup.entries.filter { it.target.endsWith(".resw", true) && it.target.substringAfterLast('/').substringBeforeLast('.').equals(map, true) }
            .flatMap { entry -> xml(file, entry.source)?.rootTag?.findSubTags("data").orEmpty().mapNotNull { data ->
                val name = data.getAttribute("name") ?: return@mapNotNull null
                name.valueElement?.takeIf { if (prefix) name.value?.startsWith("$key.") == true else name.value == key }
            } }
    }

    private fun dictionary(file: XmlFile, lookup: WinRTResourceLookup, tag: XmlTag, key: String?, visited: MutableSet<XmlTag>): List<XmlAttributeValue> {
        if (!visited.add(tag) || visited.size > 128) return emptyList()
        val found = tag.subTags.mapNotNull { child -> child.getAttribute("Key", WinRTXamlCatalog.XAML)?.valueElement?.takeIf { key == null || it.value == key } }
        val merged = tag.subTags.filter { it.localName.endsWith(".MergedDictionaries") || it.localName.endsWith(".ThemeDictionaries") }
            .flatMap { it.subTags.toList() }.flatMap { child -> dictionary(file, lookup, child, key, visited) }
        val source = tag.getAttributeValue("Source")?.let { uri -> files(file, lookup, uri).filterIsInstance<XmlFile>() }
            .orEmpty().flatMap { it.rootTag?.let { root -> dictionary(it.containingFile as XmlFile, lookup, root, key, visited) }.orEmpty() }
        val nested = tag.subTags.filter { it.localName == "ResourceDictionary" }.flatMap { dictionary(file, lookup, it, key, visited) }
        return found + merged + source + nested
    }

    internal fun resourceKeys(file: XmlFile, lookup: WinRTResourceLookup, tag: XmlTag?, key: String?): List<XmlAttributeValue> {
        val visited = hashSetOf<XmlTag>()
        val local = generateSequence(tag) { it.parentTag }.flatMap { owner ->
            val dictionaries = owner.subTags.filter { it.localName.endsWith(".Resources") } +
                if (owner.localName == "ResourceDictionary") listOf(owner) else emptyList()
            dictionaries.asSequence().flatMap { dictionary(file, lookup, it, key, visited) }
        }.toList()
        if (key != null && local.isNotEmpty()) return local
        val application = lookup.xamlFiles.mapNotNull { xml(file, it) }.filter { it.rootTag?.localName == "Application" }
            .flatMap { app -> app.rootTag!!.subTags.filter { it.localName.endsWith(".Resources") }
                .flatMap { dictionary(app, lookup, it, key, visited) } }
        if (key != null && application.isNotEmpty()) return application.distinct()
        val framework = lookup.frameworkDictionaries.mapNotNull { xml(file, it) }.flatMap { dictionary ->
            dictionary.rootTag?.let { dictionary(dictionary, lookup, it, key, visited) }.orEmpty()
        }
        return (local + application + framework).distinct()
    }
}
