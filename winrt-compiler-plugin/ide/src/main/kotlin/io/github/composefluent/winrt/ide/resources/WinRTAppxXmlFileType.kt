package io.github.composefluent.winrt.ide.resources

import com.intellij.ide.highlighter.XmlLikeFileType

object WinRTAppxXmlFileType : XmlLikeFileType(com.intellij.lang.xml.XMLLanguage.INSTANCE) {
    override fun getName() = "WinRT AppX XML"
    override fun getDisplayName() = "WinRT AppX XML"
    override fun getDescription() = "Windows package manifest and string resources"
    override fun getDefaultExtension() = "resw"
    override fun getIcon() = com.intellij.icons.AllIcons.FileTypes.Xml
}
