package io.github.composefluent.winrt.ide.xaml

import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.metadata.WinRTXamlConnectionDeclaration
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import io.github.composefluent.winrt.metadata.WinRTXamlPageDeclaration
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName

/** Name/data scopes belong to XAMLC's BindUniverse. Use source-matched
 * connections when available; unfinished edits fall back to PSI template
 * boundaries from WinMD inheritance, never to a stale line-only connection.
 */
internal object WinRTXamlScopes {
    data class Context(val boundary: XmlTag, val dataType: String?, val connection: WinRTXamlConnectionDeclaration?,
        val page: WinRTXamlPageDeclaration?)

    fun context(tag: XmlTag): Context? {
        val file = tag.containingFile as? XmlFile ?: return null
        val root = file.rootTag ?: return null
        val boundary = generateSequence(tag) { it.parentTag }.firstOrNull(::template) ?: root
        val className = root.getAttributeValue("Class", WinRTXamlCatalog.XAML)
        val original = file.originalFile
        val hash = CachedValuesManager.getCachedValue(original) {
            CachedValueProvider.Result.create(WinRTXamlDeclarations.sourceFingerprint(original.text), PsiModificationTracker.MODIFICATION_COUNT)
        }
        val page = file.project.service<WinRTXamlSnapshotService>().state.value.values
            .firstNotNullOfOrNull { snapshot -> snapshot.declarations.pages.firstOrNull {
                it.className == className && it.sourceHash == hash
            } }
        val document = PsiDocumentManager.getInstance(file.project).getDocument(original)
        val connection = if (page != null && document != null) generateSequence(tag) { it.parentTag }
            .takeWhile { it != boundary.parentTag }.firstNotNullOfOrNull { ancestor ->
                val offset = (ancestor.textRange.startOffset + 1).coerceAtMost(document.textLength)
                val line = document.getLineNumber(offset)
                val column = offset - document.getLineStartOffset(line) + 1
                page.connections.firstOrNull { it.location.line == line + 1 && it.location.column == column }
            } else null
        val explicit = boundary.getAttributeValue("DataType", WinRTXamlCatalog.XAML)?.let { qualifiedType(boundary, it) }
        val dataType = connection?.dataTypeName ?: explicit ?: className.takeIf { boundary == root }
        return Context(boundary, dataType, connection, page)
    }

    fun namedElements(tag: XmlTag): List<Pair<String, XmlAttributeValue>> {
        val context = context(tag) ?: return emptyList()
        val file = tag.containingFile as? XmlFile ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(context.boundary, XmlAttribute::class.java)
            .filter { attribute -> WinRTXamlSymbols.isDirective(attribute, "Name") &&
                boundary(attribute.parent, file.rootTag!!) == context.boundary }
            .mapNotNull { attribute -> attribute.valueElement?.let { attribute.value!! to it } }
    }

    fun qualifiedType(tag: XmlTag, text: String): String? {
        val name = text.trim().removePrefix("{x:Type ").removeSuffix("}").trim()
        if (winRTFundamentalTypeForName(name) != null) return name
        val namespace = tag.getNamespaceByPrefix(name.substringBefore(':', ""))
        val local = name.substringAfter(':')
        return WinRTXamlSymbols.catalog(tag.containingFile)?.resolve(namespace, local)?.qualifiedName
            ?: WinRTXamlCatalog.namespaces(namespace).firstOrNull()?.let { "$it.$local" }
    }

    private fun boundary(tag: XmlTag, root: XmlTag) = generateSequence(tag) { it.parentTag }.firstOrNull(::template) ?: root
    private fun template(tag: XmlTag): Boolean {
        val catalog = WinRTXamlSymbols.catalog(tag.containingFile) ?: return false
        var definition = catalog.resolve(tag.namespace, tag.localName) ?: return false
        val seen = hashSetOf<String>()
        val bases = WinRTXamlCatalog.namespaces(WinRTXamlCatalog.PRESENTATION).map { "$it.FrameworkTemplate" }.toSet()
        while (seen.add(definition.qualifiedName)) {
            if (definition.qualifiedName in bases) return true
            definition = catalog.types[definition.baseTypeName] ?: return false
        }
        return false
    }
}
