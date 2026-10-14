package io.github.composefluent.winrt.ide.xaml

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

class WinUIXamlColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Kotlin WinRT XAML"
    override fun getIcon() = WinUIXamlFileType.INSTANCE.icon
    override fun getHighlighter() = SyntaxHighlighterFactory.getSyntaxHighlighter(XMLLanguage.INSTANCE, null, null)
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Markup extension", WinUIXamlMarkupColors.EXTENSION),
        AttributesDescriptor("Named argument", WinUIXamlMarkupColors.OPTION),
        AttributesDescriptor("Path awaiting resolution", WinUIXamlMarkupColors.PATH),
        AttributesDescriptor("Resolved binding member", WinUIXamlMarkupColors.MEMBER),
        AttributesDescriptor("Resolved binding method", WinUIXamlMarkupColors.METHOD),
        AttributesDescriptor("Binding type", WinUIXamlMarkupColors.TYPE),
        AttributesDescriptor("Resolved resource key", WinUIXamlMarkupColors.RESOURCE),
        AttributesDescriptor("String literal", WinUIXamlMarkupColors.STRING),
        AttributesDescriptor("Number literal", WinUIXamlMarkupColors.NUMBER),
        AttributesDescriptor("Punctuation", WinUIXamlMarkupColors.PUNCTUATION),
        AttributesDescriptor("Property attribute", WinUIXamlMarkupColors.ATTRIBUTE),
        AttributesDescriptor("Dependency property attribute", WinUIXamlMarkupColors.DEPENDENCY_PROPERTY),
    )
    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        "extension" to WinUIXamlMarkupColors.EXTENSION, "option" to WinUIXamlMarkupColors.OPTION,
        "path" to WinUIXamlMarkupColors.PATH, "member" to WinUIXamlMarkupColors.MEMBER,
        "method" to WinUIXamlMarkupColors.METHOD, "type" to WinUIXamlMarkupColors.TYPE,
        "resource" to WinUIXamlMarkupColors.RESOURCE, "string" to WinUIXamlMarkupColors.STRING,
        "number" to WinUIXamlMarkupColors.NUMBER, "punctuation" to WinUIXamlMarkupColors.PUNCTUATION,
        "attribute" to WinUIXamlMarkupColors.ATTRIBUTE, "dp" to WinUIXamlMarkupColors.DEPENDENCY_PROPERTY,
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
