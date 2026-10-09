package io.github.composefluent.winrt.ide.xaml

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.fir.WinRTIdeTypeNames
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType

internal object WinRTXamlSymbols {
    fun isXaml(file: PsiFile?) = file?.name?.endsWith(".xaml", true) == true
    fun catalog(file: PsiFile): WinRTXamlCatalog? {
        val path = file.originalFile.virtualFile?.path ?: return null
        return file.project.service<WinRTXamlCatalogService>().forFile(path)
    }
    fun scope(file: PsiFile) = ModuleUtilCore.findModuleForPsiElement(file)?.let(GlobalSearchScope::moduleWithDependenciesAndLibrariesScope)
        ?: GlobalSearchScope.allScope(file.project)

    fun kotlinClass(file: PsiFile, name: String): KtClassOrObject? {
        if (DumbService.isDumb(file.project) || name.isBlank()) return null
        return sequenceOf(name, WinRTIdeTypeNames.projection(name)).distinct().firstNotNullOfOrNull {
            KotlinFullClassNameIndex.Helper.get(it, file.project, scope(file)).firstOrNull()
        }
    }

    fun classNames(file: PsiFile): List<String> {
        if (DumbService.isDumb(file.project)) return emptyList()
        return CachedValuesManager.getCachedValue(file.originalFile) {
            val names = mutableListOf<String>()
            KotlinFullClassNameIndex.Helper.processAllKeys(scope(file), null) { names += it; true }
            CachedValueProvider.Result.create(names.distinct().sorted(), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(file.project))
        }
    }

    fun ownerClass(tag: XmlTag): KtClassOrObject? {
        val root = (tag.containingFile as? XmlFile)?.rootTag ?: return null
        return root.getAttributeValue("Class", WinRTXamlCatalog.XAML)?.let { kotlinClass(tag.containingFile, it) }
    }

    fun tagClass(tag: XmlTag, elementName: String = tag.name): KtClassOrObject? {
        val catalog = catalog(tag.containingFile)
        val name = elementName.substringAfter(':').substringBefore('.')
        val namespace = tag.getNamespaceByPrefix(elementName.substringBefore(':', ""))
        val definition = catalog?.resolve(namespace, name)
        val qualified = definition?.qualifiedName ?: WinRTXamlCatalog.namespaces(namespace).firstOrNull()?.let { "$it.$name" }
        return qualified?.let { kotlinClass(tag.containingFile, it) }
    }

    fun members(tag: XmlTag): List<WinRTXamlMember> {
        val catalog = catalog(tag.containingFile) ?: return emptyList()
        val type = catalog.resolve(tag.namespace, tag.localName.substringBefore('.')) ?: return emptyList()
        return catalog.members(type)
    }

    /** XAML property elements use the enclosing type as owner, including its
     * inherited properties. Attached owners retain their declared XML prefix. */
    @OptIn(KaExperimentalApi::class)
    fun propertyElements(tag: XmlTag): Map<String, WinRTXamlMember> {
        if (tag.localName.contains('.')) return emptyMap()
        val catalog = catalog(tag.containingFile)
        val properties = members(tag).filterNot { it.isEvent }.toMutableList()
        tagClass(tag)?.let { owner ->
            analyze(owner) {
                val scope = (owner.classSymbol as? KaNamedClassSymbol)?.defaultType?.scope
                scope?.getCallableSignatures { true }?.forEach { signature ->
                    val variable = signature.symbol as? KaVariableSymbol ?: return@forEach
                    val name = variable.name.asString().takeUnless { it.startsWith('<') } ?: return@forEach
                    if (properties.none { it.name.equals(name, true) }) properties += WinRTXamlMember(
                        name.replaceFirstChar(Char::uppercase),
                        (signature.returnType as? KaClassType)?.classId?.asSingleFqName()?.asString().orEmpty(),
                        owner.fqName?.asString().orEmpty())
                }
            }
        }
        return buildMap {
            properties.forEach { put("${tag.name}.${it.name}", it) }
            tag.knownNamespaces().forEach { uri ->
                val prefix = tag.getPrefixByNamespace(uri)?.takeIf(String::isNotEmpty)?.plus(":").orEmpty()
                catalog?.candidates(uri).orEmpty().forEach { owner ->
                    catalog?.attachedMembers(owner).orEmpty().forEach { member ->
                        put("$prefix${owner.name}.${member.name}", member)
                    }
                }
            }
        }
    }

    fun member(tag: XmlTag, attributeName: String): WinRTXamlMember? {
        if (!attributeName.contains('.')) return members(tag).firstOrNull { it.name == attributeName }
        val catalog = catalog(tag.containingFile) ?: return null
        val ownerName = attributeName.substringBefore('.')
        val namespace = tag.getNamespaceByPrefix(ownerName.substringBefore(':', ""))
        val type = catalog.resolve(namespace, ownerName.substringAfter(':')) ?: return null
        return catalog.attachedMembers(type).firstOrNull { it.name == attributeName.substringAfter('.') }
    }

    fun isDirective(attribute: XmlAttribute, name: String) = attribute.localName == name && attribute.namespace == WinRTXamlCatalog.XAML
    fun memberNames(name: String) = listOf(name, name.replaceFirstChar(Char::lowercase)).distinct()
    fun memberTarget(tag: XmlTag, member: WinRTXamlMember): PsiElement? {
        return WinRTXamlAttributeAnalysis.forName(tag, member.name)?.primary
    }
}
