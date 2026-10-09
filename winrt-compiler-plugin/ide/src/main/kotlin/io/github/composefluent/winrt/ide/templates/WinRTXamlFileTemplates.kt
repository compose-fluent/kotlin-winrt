package io.github.composefluent.winrt.ide.templates

import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.refactoring.KotlinNamesValidator

enum class WinRTXamlFileKind(val title: String, val rootTag: String, val kotlinBase: String? = null) {
    Page("XAML Page", "Page", "microsoft.ui.xaml.controls.Page"),
    UserControl("XAML UserControl", "UserControl", "microsoft.ui.xaml.controls.UserControl"),
    ResourceDictionary("XAML ResourceDictionary", "ResourceDictionary"),
}

/** Mirrors the paired XAML/Page shape in .cswinrt/src/Samples/WinUIDesktopSample/MainPage.xaml.cs.
 * Kotlin's existing XamlPageBodies construction hook owns component initialization. */
object WinRTXamlFileTemplates {
    private val names = KotlinNamesValidator()

    fun validate(kind: WinRTXamlFileKind, name: String, packageName: String) {
        val validName = PathUtil.isValidFileName(name) && PathUtil.isValidFileName(name.substringBefore('.')) &&
            !name.endsWith('.') && !name.endsWith(' ')
        require(validName && (if (kind.kotlinBase != null) identifier(name) else !name.endsWith(".xaml", true))) {
            "Enter a valid ${if (kind.kotlinBase != null) "Kotlin class" else "resource"} name without a file extension."
        }
        if (kind.kotlinBase != null) require(packageName.isNotBlank() && packageName.split('.').all(::identifier)) {
            "Enter a Kotlin package without keywords, spaces or empty segments."
        }
    }

    private fun identifier(value: String) = !value.startsWith('`') && names.isIdentifier(value, null) && !names.isKeyword(value, null)

    fun files(kind: WinRTXamlFileKind, name: String, packageName: String = ""): Map<String, ByteArray> {
        validate(kind, name, packageName)
        return buildMap {
            kind.kotlinBase?.let { base ->
                val baseName = if (name == kind.rootTag) "WinRT${kind.rootTag}" else kind.rootTag
                val alias = if (baseName != kind.rootTag) " as $baseName" else ""
                put("$name.kt", """
                    package $packageName

                    import $base$alias

                    class $name : $baseName() {
                    }
                """.trimIndent().plus("\n").toByteArray(Charsets.UTF_8))
            }
            val classAttribute = if (kind.kotlinBase != null) "\n    x:Class=\"$packageName.$name\"" else ""
            val content = if (kind.kotlinBase != null) "\n    <Grid />\n" else "\n"
            put("$name.xaml", """<${kind.rootTag}
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"$classAttribute>$content</${kind.rootTag}>
""".toByteArray(Charsets.UTF_8))
        }
    }
}
