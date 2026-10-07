package io.github.composefluent.winrt.ide.xaml

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

class WinRTXamlColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Kotlin WinRT XAML"
    override fun getIcon() = WinRTXamlFileType.INSTANCE.icon
    override fun getHighlighter() = SyntaxHighlighterFactory.getSyntaxHighlighter(XMLLanguage.INSTANCE, null, null)
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Markup extension", WinRTXamlMarkupColors.EXTENSION),
        AttributesDescriptor("Named argument", WinRTXamlMarkupColors.OPTION),
        AttributesDescriptor("Path awaiting resolution", WinRTXamlMarkupColors.PATH),
        AttributesDescriptor("Resolved binding member", WinRTXamlMarkupColors.MEMBER),
        AttributesDescriptor("Resolved binding method", WinRTXamlMarkupColors.METHOD),
        AttributesDescriptor("Binding type", WinRTXamlMarkupColors.TYPE),
        AttributesDescriptor("Resolved resource key", WinRTXamlMarkupColors.RESOURCE),
        AttributesDescriptor("String literal", WinRTXamlMarkupColors.STRING),
        AttributesDescriptor("Number literal", WinRTXamlMarkupColors.NUMBER),
        AttributesDescriptor("Punctuation", WinRTXamlMarkupColors.PUNCTUATION),
        AttributesDescriptor("Property attribute", WinRTXamlMarkupColors.ATTRIBUTE),
        AttributesDescriptor("Dependency property attribute", WinRTXamlMarkupColors.DEPENDENCY_PROPERTY),
    )
    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        "extension" to WinRTXamlMarkupColors.EXTENSION, "option" to WinRTXamlMarkupColors.OPTION,
        "path" to WinRTXamlMarkupColors.PATH, "member" to WinRTXamlMarkupColors.MEMBER,
        "method" to WinRTXamlMarkupColors.METHOD, "type" to WinRTXamlMarkupColors.TYPE,
        "resource" to WinRTXamlMarkupColors.RESOURCE, "string" to WinRTXamlMarkupColors.STRING,
        "number" to WinRTXamlMarkupColors.NUMBER, "punctuation" to WinRTXamlMarkupColors.PUNCTUATION,
        "attribute" to WinRTXamlMarkupColors.ATTRIBUTE, "dp" to WinRTXamlMarkupColors.DEPENDENCY_PROPERTY,
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
