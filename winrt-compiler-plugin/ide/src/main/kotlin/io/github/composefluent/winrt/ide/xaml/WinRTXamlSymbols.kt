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
