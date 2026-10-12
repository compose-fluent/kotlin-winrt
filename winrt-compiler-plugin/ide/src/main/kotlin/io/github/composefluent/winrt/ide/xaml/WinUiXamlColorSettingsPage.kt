package io.github.composefluent.winrt.ide.xaml

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

class WinUiXamlColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Kotlin WinRT XAML"
    override fun getIcon() = WinUiXamlFileType.INSTANCE.icon
    override fun getHighlighter() = SyntaxHighlighterFactory.getSyntaxHighlighter(XMLLanguage.INSTANCE, null, null)
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Markup extension", WinUiXamlMarkupColors.EXTENSION),
        AttributesDescriptor("Named argument", WinUiXamlMarkupColors.OPTION),
        AttributesDescriptor("Path awaiting resolution", WinUiXamlMarkupColors.PATH),
        AttributesDescriptor("Resolved binding member", WinUiXamlMarkupColors.MEMBER),
        AttributesDescriptor("Resolved binding method", WinUiXamlMarkupColors.METHOD),
        AttributesDescriptor("Binding type", WinUiXamlMarkupColors.TYPE),
        AttributesDescriptor("Resolved resource key", WinUiXamlMarkupColors.RESOURCE),
        AttributesDescriptor("String literal", WinUiXamlMarkupColors.STRING),
        AttributesDescriptor("Number literal", WinUiXamlMarkupColors.NUMBER),
        AttributesDescriptor("Punctuation", WinUiXamlMarkupColors.PUNCTUATION),
        AttributesDescriptor("Property attribute", WinUiXamlMarkupColors.ATTRIBUTE),
        AttributesDescriptor("Dependency property attribute", WinUiXamlMarkupColors.DEPENDENCY_PROPERTY),
    )
    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        "extension" to WinUiXamlMarkupColors.EXTENSION, "option" to WinUiXamlMarkupColors.OPTION,
        "path" to WinUiXamlMarkupColors.PATH, "member" to WinUiXamlMarkupColors.MEMBER,
        "method" to WinUiXamlMarkupColors.METHOD, "type" to WinUiXamlMarkupColors.TYPE,
        "resource" to WinUiXamlMarkupColors.RESOURCE, "string" to WinUiXamlMarkupColors.STRING,
        "number" to WinUiXamlMarkupColors.NUMBER, "punctuation" to WinUiXamlMarkupColors.PUNCTUATION,
        "attribute" to WinUiXamlMarkupColors.ATTRIBUTE, "dp" to WinUiXamlMarkupColors.DEPENDENCY_PROPERTY,
    )
    override fun getDemoText() = """
        <Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
              xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
            <TextBlock <attribute>Text</attribute>="<punctuation>{</punctuation><extension>x:Bind</extension> <member>model</member><punctuation>.</punctuation><member>title</member><punctuation>,</punctuation> <option>Mode</option><punctuation>=</punctuation><path>OneWay</path><punctuation>}</punctuation>" <dp>Width</dp>="240" />
            <TextBlock Text="<punctuation>{</punctuation><extension>Binding</extension> <option>Path</option><punctuation>=</punctuation><path>Title</path><punctuation>}</punctuation>" />
            <TextBlock Style="<punctuation>{</punctuation><extension>StaticResource</extension> <resource>TitleTextBlockStyle</resource><punctuation>}</punctuation>" />
            <Border Background="<punctuation>{</punctuation><extension>ThemeResource</extension> <resource>CardBackgroundFillColorDefaultBrush</resource><punctuation>}</punctuation>" />
            <TextBlock Text="<punctuation>{</punctuation><extension>x:Bind</extension> <method>format</method><punctuation>(</punctuation><string>'Hello'</string><punctuation>,</punctuation> <number>42</number><punctuation>)}</punctuation>" />
        </Page>
    """.trimIndent()
}
