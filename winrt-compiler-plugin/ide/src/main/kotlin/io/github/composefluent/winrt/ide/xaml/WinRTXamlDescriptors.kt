package io.github.composefluent.winrt.ide.xaml

import com.intellij.javaee.ImplicitNamespaceDescriptorProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.*
import com.intellij.xml.*

class WinRTXamlDescriptorProvider : XmlElementDescriptorProvider {
    override fun getDescriptor(tag: XmlTag): XmlElementDescriptor? {
        if (!WinRTXamlSymbols.isXaml(tag.containingFile)) return null
        return WinRTXamlElementDescriptor(tag, tag.name)
    }
}

class WinRTXamlNamespaceProvider : ImplicitNamespaceDescriptorProvider {
    override fun getNamespaceDescriptor(module: Module?, ns: String, file: PsiFile?): XmlNSDescriptor? =
        if (file != null && WinRTXamlSymbols.isXaml(file) && WinRTXamlCatalog.namespaces(ns).isNotEmpty())
            WinRTXamlNamespaceDescriptor(file, ns) else null
}

private class WinRTXamlNamespaceDescriptor(private val file: PsiFile, private val namespace: String) : XmlNSDescriptor {
    override fun getElementDescriptor(tag: XmlTag) = WinRTXamlElementDescriptor(tag, tag.name)
    override fun getRootElementsDescriptors(document: XmlDocument?): Array<XmlElementDescriptor> {
        val tag = (file as? XmlFile)?.rootTag ?: return emptyArray()
        val prefix = tag.getPrefixByNamespace(namespace)?.takeIf { it.isNotEmpty() }?.plus(":").orEmpty()
        val metadata = WinRTXamlSymbols.catalog(file)?.candidates(namespace).orEmpty().map { prefix + it.name }
        val custom = WinRTXamlCatalog.namespaces(namespace).flatMap { ns ->
            WinRTXamlSymbols.classNames(file).filter { it.substringBeforeLast('.', "") == ns }.map { prefix + it.substringAfterLast('.') }
        }
        return (metadata + custom).distinct().map { WinRTXamlElementDescriptor(tag, it) }.toTypedArray()
    }
    override fun getDescriptorFile() = null
    override fun getDeclaration(): PsiElement = file
    override fun getName(context: PsiElement?) = namespace
    override fun getName() = namespace
    override fun init(element: PsiElement?) = Unit
    override fun getDependencies(): Array<Any> = arrayOf(file.project.service<WinRTXamlCatalogService>().modificationTracker, file)
}

private class WinRTXamlElementDescriptor(private val tag: XmlTag, private val elementName: String) : XmlElementDescriptor {
    override fun getQualifiedName() = elementName
    override fun getDefaultName() = elementName
    override fun getName() = elementName
    override fun getName(context: PsiElement?) = elementName
    override fun init(element: PsiElement?) = Unit
    override fun getDeclaration(): PsiElement? = WinRTXamlSymbols.tagClass(tag, elementName)
    override fun getDependencies(): Array<Any> = arrayOf(tag.project.service<WinRTXamlCatalogService>().modificationTracker, tag.containingFile)
    override fun getElementsDescriptors(context: XmlTag?): Array<XmlElementDescriptor> = (context ?: tag).knownNamespaces()
        .flatMap { WinRTXamlNamespaceDescriptor(tag.containingFile, it).getRootElementsDescriptors(null).toList() }.toTypedArray()
    override fun getElementDescriptor(child: XmlTag, context: XmlTag?) = WinRTXamlElementDescriptor(child, child.name)
    override fun getAttributesDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> {
        val current = context ?: tag
        val members = WinRTXamlSymbols.members(current).map { WinRTXamlAttributeDescriptor(current, it.name, it) }
        val prefix = current.getPrefixByNamespace(WinRTXamlCatalog.XAML)?.takeIf { it.isNotEmpty() } ?: "x"
        val directives = listOf("Class", "Name", "Key", "Uid", "DataType", "Load", "DeferLoadStrategy", "Phase", "DefaultBindMode")
            .map { WinRTXamlAttributeDescriptor(current, "$prefix:$it", null) }
        val custom = WinRTXamlSymbols.tagClass(current)?.declarations.orEmpty().filterIsInstance<org.jetbrains.kotlin.psi.KtProperty>()
            .mapNotNull { it.name }.map { WinRTXamlAttributeDescriptor(current, it, null) }
        return (members + directives + custom).distinctBy { it.name }.toTypedArray()
    }
    override fun getAttributeDescriptor(name: String, context: XmlTag?): XmlAttributeDescriptor? =
        getAttributesDescriptors(context).firstOrNull { it.name == name } ?:
            WinRTXamlAttributeDescriptor(context ?: tag, name, WinRTXamlSymbols.member(context ?: tag, name))
    override fun getAttributeDescriptor(attribute: XmlAttribute) = getAttributeDescriptor(attribute.name, attribute.parent)
    override fun getNSDescriptor(): XmlNSDescriptor = WinRTXamlNamespaceDescriptor(tag.containingFile, tag.namespace)
    override fun getTopGroup(): XmlElementsGroup? = null
    override fun getContentType() = XmlElementDescriptor.CONTENT_TYPE_ANY
    override fun getDefaultValue(): String? = null
}

private class WinRTXamlAttributeDescriptor(private val tag: XmlTag, private val attributeName: String,
    private val member: WinRTXamlMember?) : XmlAttributeDescriptor {
    override fun getName() = attributeName
    override fun getName(context: PsiElement?) = attributeName
    override fun init(element: PsiElement?) = Unit
    override fun getDeclaration(): PsiElement? = member?.let { WinRTXamlSymbols.memberTarget(tag, it) }
    override fun isRequired() = false
    override fun isFixed() = false
    override fun hasIdType() = attributeName.substringAfter(':') == "Name"
    override fun hasIdRefType() = attributeName == "ElementName"
    override fun getDefaultValue(): String? = null
    override fun isEnumerated() = false // Values may be markup extensions even for enums.
    override fun getEnumeratedValues(): Array<String>? = member?.typeName?.let { name ->
        WinRTXamlSymbols.catalog(tag.containingFile)?.types?.get(name)?.enumMembers?.map { it.name }?.toTypedArray()
    }
    override fun validateValue(context: XmlElement?, value: String?): String? = null
}
