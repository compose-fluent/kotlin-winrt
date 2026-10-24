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
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinUIXamlResourceExpression
import io.github.composefluent.winrt.ide.xaml.WinUIXamlSymbols
import io.github.composefluent.winrt.ide.xaml.WinUIXamlMarkupHighlighting
import io.github.composefluent.winrt.ide.xaml.WinUIXamlMarkupColors
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

class WinRTResourceReferenceAnnotator : Annotator, com.intellij.openapi.project.DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val reference = WinRTResourceReferences.reference(element) ?: return
        if (reference.multiResolve(false).isEmpty()) holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
            "No resource source candidate is known here. Runtime resources may be supplied by dependencies; stage the package to inspect them.")
            .range(reference.rangeInElement.shiftRight(element.textRange.startOffset)).create()
        else if (element is XmlAttributeValue && WinUIXamlMarkupHighlighting.parse(element)?.resourceKey != null)
            holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
                .range(reference.rangeInElement.shiftRight(element.textRange.startOffset))
                .textAttributes(WinUIXamlMarkupColors.RESOURCE).create()
    }
}

internal object WinRTResourceReferences {
    private enum class Kind { File, String, Uid, Key }

    internal fun reference(element: PsiElement): PsiPolyVariantReference? {
        if (element !is XmlAttributeValue && element !is XmlText) return null
        val file = element.containingFile as? XmlFile ?: return null
        val lookup = file.originalFile.virtualFile?.path?.let { file.project.service<WinRTResourceIndex>().forFile(it) }
        val attribute = (element as? XmlAttributeValue)?.parent as? XmlAttribute
        val dictionarySource = attribute?.localName == "Source" && WinUIXamlSymbols.isXaml(file) &&
            attribute.parent.localName == "ResourceDictionary"
        val value = when (element) { is XmlAttributeValue -> element.value; is XmlText -> element.value.trim(); else -> return null }
        val key = WinUIXamlResourceExpression.parse(value)?.key ?: value.takeIf {
            attribute?.localName == "ResourceKey" && attribute.parent.localName.removeSuffix("Extension") in
                setOf("StaticResource", "ThemeResource")
        }
        val kind = when {
            key != null && WinUIXamlSymbols.isXaml(file) -> Kind.Key
            attribute?.localName == "Uid" && attribute.namespace == WinUIXamlCatalog.XAML -> Kind.Uid
            value.startsWith("ms-resource:", true) -> Kind.String
            value.startsWith("ms-appx:", true) -> Kind.File
            dictionarySource && !value.startsWith('{') -> Kind.File
            attribute != null && file.rootTag?.localName == "Package" && attribute.localName.endsWith("Logo") -> Kind.File
            attribute != null && WinUIXamlSymbols.isXaml(file) &&
                WinUIXamlSymbols.members(attribute.parent).firstOrNull { it.name == attribute.localName }?.typeName in
                setOf("Windows.Foundation.Uri", "Microsoft.UI.Xaml.Media.ImageSource") && !value.startsWith('{') -> Kind.File
            else -> return null
        }
        if (value.isBlank()) return null
        if (lookup == null && kind != Kind.Key && !(kind == Kind.File && dictionarySource)) return null
        val token = key ?: value
        if (kind == Kind.File && token.contains(':') && !token.startsWith("ms-appx:", true)) return null
        val contentRange = ElementManipulators.getValueTextRange(element)
        val range = if (kind == Kind.Key && element is XmlAttributeValue)
            WinUIXamlMarkupHighlighting.parse(element)?.resourceKey ?: contentRange else contentRange
        return object : PsiPolyVariantReferenceBase<PsiElement>(element, range, true) {
            override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
                val targets = when (kind) {
                    Kind.File -> files(file, lookup, token)
                    Kind.String -> strings(file, lookup!!, token)
                    Kind.Uid -> strings(file, lookup!!, token, prefix = true)
                    Kind.Key -> resourceKeys(file, lookup, (attribute?.parent ?: (element.parent as? XmlTag)), token).map { it.element }
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
                return ElementManipulators.handleContentChange(element, contentRange, value.replaceRange(offset, offset + name.length, newElementName))
            }
        }
    }

    private fun xml(file: PsiFile, path: String): XmlFile? = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))
        ?.let { PsiManager.getInstance(file.project).findFile(it) as? XmlFile }

    private fun path(value: String): String? = runCatching {
        val normalized = value.replace('\\', '/')
        val uri = URI(normalized.replace(" ", "%20"))
        if (uri.scheme != null) {
            if (uri.rawAuthority.orEmpty().isNotEmpty()) return null // A foreign package has its own resource map.
            (uri.path ?: uri.schemeSpecificPart).trimStart('/')
        } else uri.path?.trimStart('/')
    }.getOrNull()

    private fun packagePath(value: String): String? {
        val parts = ArrayDeque<String>()
        for (part in value.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> parts.addLast(part)
        }
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun files(file: XmlFile, lookup: WinRTResourceLookup?, value: String): List<PsiElement> {
        val requested = path(value)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val normalized = value.replace('\\', '/')
        val relative = !normalized.startsWith('/') && !normalized.contains(':')
        val physical = file.originalFile.virtualFile?.path ?: return emptyList()
        // WinUI's BaseUri is the current dictionary's package URI, not the
        // caller's URI. XAMLC's MSBuild_Link may differ from its physical path.
        val links = lookup?.entries.orEmpty().filter { it.source.replace('\\', '/').equals(physical, true) }
        val logicals = if (relative && links.isNotEmpty()) links.mapNotNull {
            packagePath(it.target.substringBeforeLast('/', "") + "/" + requested)
        }.distinct() else listOfNotNull(packagePath(requested))
        if (relative && links.isEmpty()) {
            file.originalFile.virtualFile?.parent?.findFileByRelativePath(requested)?.let {
                return listOfNotNull(PsiManager.getInstance(file.project).findFile(it))
            }
        }
        return logicals.flatMap { logical ->
            val priTargets = lookup?.pri.orEmpty().filter { candidate ->
                runCatching { URI(candidate.resourceUri).path?.substringAfter("/Files/", "")?.equals(logical, true) == true }.getOrDefault(false)
            }.map { it.value.replace('\\', '/').trimStart('/') }.toSet()
            val matches = lookup?.entries.orEmpty().filter { entry -> entry.target.equals(logical, true) || entry.target in priTargets ||
                WinRTResourceCatalog.family(entry.target).equals(logical, true) }
            val xaml = if (matches.isNotEmpty()) emptyList() else lookup?.xamlFiles.orEmpty().filter { source ->
                source.replace('\\', '/').endsWith("/$logical", true) && lookup?.entries.orEmpty().none {
                    it.source.replace('\\', '/').equals(source.replace('\\', '/'), true) && !it.target.equals(logical, true)
                }
            }
            (matches.map { it.source } + xaml).distinct().mapNotNull { source ->
                LocalFileSystem.getInstance().findFileByPath(source.replace('\\', '/'))?.let { PsiManager.getInstance(file.project).findFile(it) }
            }
        }.distinct()
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

    private fun dictionary(file: XmlFile, lookup: WinRTResourceLookup?, tag: XmlTag, key: String?, visited: MutableSet<XmlTag>): List<XmlAttributeValue> {
        if (!visited.add(tag) || visited.size > 128) return emptyList()
        val found = tag.subTags.mapNotNull { child -> child.getAttribute("Key", WinUIXamlCatalog.XAML)?.valueElement?.takeIf { key == null || it.value == key } }
        val merged = tag.subTags.filter { it.localName.endsWith(".MergedDictionaries") || it.localName.endsWith(".ThemeDictionaries") }
            .flatMap { it.subTags.toList() }.flatMap { child -> dictionary(file, lookup, child, key, visited) }
        val source = tag.getAttributeValue("Source")?.let { uri -> files(file, lookup, uri).filterIsInstance<XmlFile>() }
            .orEmpty().flatMap { it.rootTag?.let { root -> dictionary(it.containingFile as XmlFile, lookup, root, key, visited) }.orEmpty() }
        val nested = tag.subTags.filter { it.localName == "ResourceDictionary" }.flatMap { dictionary(file, lookup, it, key, visited) }
        return found + merged + source + nested
    }

    /** Source-owned implicit styles. WinUI EnsureImplicitStyle/GetKeyNoRef
     * walk logical scopes, then merged dictionaries in reverse order. Runtime
     * theme/template/foreign-control scopes cannot be inferred from PSI: fail
     * closed instead of editing a possibly different runtime style.
     * Reference: microsoft-ui-xaml/docs/design-notes/resources.md. */
    internal fun implicitStyle(tag: XmlTag, catalog: WinUIXamlCatalog): XmlTag? {
        val type = catalog.resolve(tag.namespace, tag.localName) ?: return null
        val file = tag.containingFile as? XmlFile ?: return null
        val lookup = file.originalFile.virtualFile?.path?.let { file.project.service<WinRTResourceIndex>().forFile(it) }
        val visited = hashSetOf<XmlTag>()
        fun dictionary(owner: XmlTag): List<XmlTag>? {
            if (!visited.add(owner) || visited.size > 128) return null
            val direct = owner.subTags.filter { child ->
                if (child.namespace != WinUIXamlCatalog.PRESENTATION || child.localName != "Style" ||
                    child.getAttribute("Key", WinUIXamlCatalog.XAML) != null || child.getAttribute("Name", WinUIXamlCatalog.XAML) != null) false
                else child.getAttributeValue("TargetType")?.let { name ->
                    catalog.resolve(child.getNamespaceByPrefix(name.substringBefore(':', "")), name.substringAfter(':')) == type
                } == true
            }
            if (direct.isNotEmpty()) return direct
            for (nested in owner.subTags.filter { it.localName == "ResourceDictionary" }) {
                val found = dictionary(nested) ?: return null
                if (found.isNotEmpty()) return found
            }
            val source = owner.getAttributeValue("Source")
            if (source != null) {
                val target = files(owner.containingFile as XmlFile, lookup, source).filterIsInstance<XmlFile>().singleOrNull()?.rootTag ?: return null
                return dictionary(target)
            }
            for (merged in owner.subTags.filter { it.localName.endsWith(".MergedDictionaries") }.flatMap { it.subTags.toList() }.asReversed()) {
                val found = dictionary(merged) ?: return null
                if (found.isNotEmpty()) return found
            }
            if (owner.subTags.any { it.localName.endsWith(".ThemeDictionaries") }) return null
            return emptyList()
        }
        // Source parents are not logical parents across template instantiation.
        val owners = generateSequence(tag) { it.parentTag }.toList()
        if (owners.any { it.namespace == WinUIXamlCatalog.PRESENTATION && it.localName in setOf("ControlTemplate", "DataTemplate", "Style") }) return null
        for (owner in owners) {
            for (resources in owner.subTags.filter { it.localName.endsWith(".Resources") }) {
                val found = dictionary(resources) ?: return null
                if (found.isNotEmpty()) return found.singleOrNull()
            }
        }
        // Application/theme ownership may depend on the selected launch host.
        // Never choose a global source candidate without that runtime identity.
        return null
    }

    internal data class Key(val value: String, val element: PsiElement)

    internal fun resourceKeys(file: XmlFile, lookup: WinRTResourceLookup?, tag: XmlTag?, key: String?): List<Key> {
        val visited = hashSetOf<XmlTag>()
        val local = generateSequence(tag) { it.parentTag }.flatMap { owner ->
            val dictionaries = owner.subTags.filter { it.localName.endsWith(".Resources") } +
                if (owner.localName == "ResourceDictionary") listOf(owner) else emptyList()
            dictionaries.asSequence().flatMap { dictionary(file, lookup, it, key, visited) }
        }.toList()
        fun List<XmlAttributeValue>.candidates() = map { Key(it.value, it) }
        if (key != null && local.isNotEmpty()) return local.candidates()
        val application = lookup?.xamlFiles.orEmpty().mapNotNull { xml(file, it) }.filter { it.rootTag?.localName == "Application" }
            .flatMap { app -> app.rootTag!!.subTags.filter { it.localName.endsWith(".Resources") }
                .flatMap { dictionary(app, lookup, it, key, visited) } }
        if (key != null && application.isNotEmpty()) return application.distinct().candidates()
        val indexedSources = lookup?.frameworkKeys.orEmpty().map { it.source }.toSet()
        val framework = lookup?.frameworkDictionaries.orEmpty().filterNot { it in indexedSources }.mapNotNull { xml(file, it) }.flatMap { dictionary ->
            dictionary.rootTag?.let { dictionary(dictionary, lookup, it, key, visited) }.orEmpty()
        }
        val oversized = lookup?.frameworkKeys.orEmpty().filter { key == null || it.value == key }.mapNotNull { resource ->
            LocalFileSystem.getInstance().findFileByPath(resource.source.replace('\\', '/'))?.let { PsiManager.getInstance(file.project).findFile(it) }
                ?.let { source -> resource.element(source).takeIf { it.isValid }?.let { Key(resource.value, it) } }
        }
        val sources = (local + application + framework).distinct().candidates() + oversized
        if (key != null && sources.isNotEmpty()) return sources
        return sources + file.project.service<WinRTSystemResourceKeys>().candidates(key)
    }
}
