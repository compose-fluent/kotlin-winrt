package io.github.composefluent.winrt.ide.xaml

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlLikeFileType
import com.intellij.lang.xml.XMLLanguage

class WinRTXamlFileType private constructor() : XmlLikeFileType(XMLLanguage.INSTANCE) {
    override fun getName() = "WinRT XAML"
    override fun getDisplayName() = "WinRT XAML"
    override fun getDescription() = "XAML document"
    override fun getDefaultExtension() = "xaml"
    override fun getIcon() = AllIcons.FileTypes.Xml

    companion object { @JvmField val INSTANCE = WinRTXamlFileType() }
}
