package io.github.composefluent.winrt.ide.xaml

import com.intellij.javaee.ImplicitNamespaceDescriptorProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.*
import com.intellij.xml.*

class WinUiXamlDescriptorProvider : XmlElementDescriptorProvider {
    override fun getDescriptor(tag: XmlTag): XmlElementDescriptor? {
        if (!WinUiXamlSymbols.isXaml(tag.containingFile)) return null
        return WinUiXamlElementDescriptor(tag, tag.name)
    }
}

class WinUiXamlNamespaceProvider : ImplicitNamespaceDescriptorProvider {
    override fun getNamespaceDescriptor(module: Module?, ns: String, file: PsiFile?): XmlNSDescriptor? =
        if (file != null && WinUiXamlSymbols.isXaml(file) && isXamlNamespace(ns))
            WinUiXamlNamespaceDescriptor(file, ns) else null
}

/** Namespace URIs identify a language, not an external XSD to download. Like
 * IDEA's JavaFX schema provider, resolve them only in this XML dialect. The
 * actual element/member vocabulary continues to come from WinMD and Kotlin. */
class WinUiXamlSchemaProvider : XmlSchemaProvider() {
    override fun isAvailable(file: XmlFile) = WinUiXamlSymbols.isXaml(file)
    override fun getSchema(url: String, module: Module?, baseFile: PsiFile): XmlFile? {
        if (!WinUiXamlSymbols.isXaml(baseFile) || !isXamlNamespace(url)) return null
        return PsiFileFactory.getInstance(baseFile.project).createFileFromText("WinUiXamlNamespace.xsd", XMLLanguage.INSTANCE,
            """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="${StringUtil.escapeXmlEntities(url)}"/>""") as XmlFile
    }
}

private fun isXamlNamespace(uri: String) = uri.substringBefore('?') in setOf(WinUiXamlCatalog.XAML,
    "http://schemas.microsoft.com/expression/blend/2008", "http://schemas.openxmlformats.org/markup-compatibility/2006") ||
    WinUiXamlCatalog.namespaces(uri).isNotEmpty()

/** XAMLC DirectUISchemaContext.GetProxyType and DirectUIXamlLanguage.LookupXamlObjects.
 * These language objects do not exist as WinRT runtime classes in WinMD. */
private fun isXamlLanguageElement(namespace: String, name: String): Boolean = when {
    namespace.substringBefore('?') == WinUiXamlCatalog.XAML -> name in setOf(
        "Null", "NullExtension", "String", "Double", "Int32", "Boolean", "Bind", "Object", "Properties", "Property")
    namespace.substringBefore('?') in setOf(WinUiXamlCatalog.PRESENTATION, "http://schemas.microsoft.com/windows/2010/directui") ->
        name.removeSuffix("Extension") in setOf("StaticResource", "ThemeResource", "CustomResource", "TemplateBinding")
    else -> false
}

private class WinUiXamlNamespaceDescriptor(private val file: PsiFile, private val namespace: String) : XmlNSDescriptor {
    override fun getElementDescriptor(tag: XmlTag) = WinUiXamlElementDescriptor(tag, tag.name)
    override fun getRootElementsDescriptors(document: XmlDocument?): Array<XmlElementDescriptor> {
        val tag = (file as? XmlFile)?.rootTag ?: return emptyArray()
        val prefix = tag.getPrefixByNamespace(namespace)?.takeIf { it.isNotEmpty() }?.plus(":").orEmpty()
        val metadata = WinUiXamlSymbols.catalog(file)?.candidates(namespace).orEmpty().map { prefix + it.name }
        val custom = WinUiXamlCatalog.namespaces(namespace).flatMap { ns ->
            WinUiXamlSymbols.classNames(file).filter { it.substringBeforeLast('.', "") == ns }.map { prefix + it.substringAfterLast('.') }
        }
        return (metadata + custom).distinct().map { WinUiXamlElementDescriptor(tag, it) }.toTypedArray()
    }
    override fun getDescriptorFile() = null
    override fun getDeclaration(): PsiElement = file
    override fun getName(context: PsiElement?) = namespace
    override fun getName() = namespace
    override fun init(element: PsiElement?) = Unit
    override fun getDependencies(): Array<Any> = arrayOf(file.project.service<WinUiXamlCatalogService>().modificationTracker, file)
}

private class WinUiXamlElementDescriptor(private val tag: XmlTag, private val elementName: String) : XmlElementDescriptor {
    override fun getQualifiedName() = elementName
    override fun getDefaultName() = elementName
    override fun getName() = elementName
    override fun getName(context: PsiElement?) = elementName
    override fun init(element: PsiElement?) = Unit
    override fun getDeclaration(): PsiElement? {
        val name = elementName.substringAfter(':')
        if (name.contains('.')) {
            (WinUiXamlAttributeAnalysis.forName(tag, name.substringAfter('.'))?.primary
                ?: WinUiXamlAttributeAnalysis.forName(tag, elementName)?.primary)?.let { return it }
        } else WinUiXamlSymbols.tagClass(tag, elementName)?.let { return it }
        val namespace = tag.getNamespaceByPrefix(elementName.substringBefore(':', ""))
        if (isXamlLanguageElement(namespace, name)) return tag
        // WinMD can be ready before its Kotlin projection has been indexed. A
        // known SDK element remains valid during that boundary; unknown names
        // still produce the native XML unresolved-reference diagnostic.
        val catalog = WinUiXamlSymbols.catalog(tag.containingFile) ?: return null
        val owner = catalog.resolve(namespace, name.substringBefore('.')) ?: return null
        return tag.takeIf { !name.contains('.') || (catalog.members(owner) + catalog.attachedMembers(owner))
            .any { it.name == name.substringAfter('.') } }
    }
    override fun getDependencies(): Array<Any> = arrayOf(tag.project.service<WinUiXamlCatalogService>().modificationTracker, tag.containingFile)
    override fun getElementsDescriptors(context: XmlTag?): Array<XmlElementDescriptor> {
        val current = context ?: tag
        val types = current.knownNamespaces().flatMap {
            WinUiXamlNamespaceDescriptor(tag.containingFile, it).getRootElementsDescriptors(null).toList()
        }
        val properties = WinUiXamlSymbols.propertyElements(current).keys.map { WinUiXamlElementDescriptor(current, it) }
        return (types + properties).distinctBy { it.name }.toTypedArray()
    }
    override fun getElementDescriptor(child: XmlTag, context: XmlTag?) = WinUiXamlElementDescriptor(child, child.name)
    override fun getAttributesDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> {
        val current = context ?: tag
        val members = WinUiXamlSymbols.members(current).map { WinUiXamlAttributeDescriptor(current, it.name, it) }
        val prefix = current.getPrefixByNamespace(WinUiXamlCatalog.XAML)?.takeIf { it.isNotEmpty() } ?: "x"
        val directives = listOf("Class", "Name", "Key", "Uid", "DataType", "Load", "DeferLoadStrategy", "Phase", "DefaultBindMode")
            .map { WinUiXamlAttributeDescriptor(current, "$prefix:$it", null) }
        val custom = WinUiXamlSymbols.tagClass(current)?.declarations.orEmpty().filterIsInstance<org.jetbrains.kotlin.psi.KtProperty>()
            .mapNotNull { it.name }.map { WinUiXamlAttributeDescriptor(current, it, null) }
        return (members + directives + custom).distinctBy { it.name }.toTypedArray()
    }
    override fun getAttributeDescriptor(name: String, context: XmlTag?): XmlAttributeDescriptor? =
        getAttributesDescriptors(context).firstOrNull { it.name == name } ?:
            WinUiXamlAttributeDescriptor(context ?: tag, name, WinUiXamlSymbols.member(context ?: tag, name))
    override fun getAttributeDescriptor(attribute: XmlAttribute) = getAttributeDescriptor(attribute.name, attribute.parent)
    override fun getNSDescriptor(): XmlNSDescriptor = WinUiXamlNamespaceDescriptor(tag.containingFile, tag.namespace)
    override fun getTopGroup(): XmlElementsGroup? = null
    override fun getContentType() = XmlElementDescriptor.CONTENT_TYPE_ANY
    override fun getDefaultValue(): String? = null
}

private class WinUiXamlAttributeDescriptor(private val tag: XmlTag, private val attributeName: String,
    private val member: WinUiXamlMember?) : XmlAttributeDescriptor {
    override fun getName() = attributeName
    override fun getName(context: PsiElement?) = attributeName
    override fun init(element: PsiElement?) = Unit
    override fun getDeclaration(): PsiElement? = WinUiXamlAttributeAnalysis.forName(tag, attributeName)?.targets?.firstOrNull()
        ?: tag.getAttribute(attributeName)?.takeIf { member != null }
    override fun isRequired() = false
    override fun isFixed() = false
    override fun hasIdType() = attributeName.substringAfter(':') == "Name"
    override fun hasIdRefType() = attributeName == "ElementName"
    override fun getDefaultValue(): String? = null
    override fun isEnumerated() = false // Values may be markup extensions even for enums.
    override fun getEnumeratedValues(): Array<String>? = member?.typeName?.let { name ->
        WinUiXamlSymbols.catalog(tag.containingFile)?.types?.get(name)?.enumMembers?.map { it.name }?.toTypedArray()
    }
    override fun validateValue(context: XmlElement?, value: String?): String? = null
}
