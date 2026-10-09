package io.github.composefluent.winrt.ide.resources

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.LightVirtualFile

/** WinUI FrameworkTheming::RebuildColorAndBrushResources provides these keys
 * dynamically, outside generic.xaml. Keep the provider's color/brush pairing;
 * never infer valid keys from references in a theme dictionary.
 * https://github.com/microsoft/microsoft-ui-xaml/blob/main/docs/design-notes/resources.md#magic-keys
 */
@Service(Service.Level.PROJECT)
internal class WinRTSystemResourceKeys(private val project: Project) {
    private val file by lazy {
        val systemColors = listOf(
            "ActiveCaption" to "COLOR_ACTIVECAPTION", "Background" to "COLOR_BACKGROUND",
            "ButtonFace" to "COLOR_BTNFACE", "ButtonText" to "COLOR_BTNTEXT",
            "CaptionText" to "COLOR_CAPTIONTEXT", "GrayText" to "COLOR_GRAYTEXT",
            "Highlight" to "COLOR_HIGHLIGHT", "HighlightText" to "COLOR_HIGHLIGHTTEXT",
            "Hotlight" to "COLOR_HOTLIGHT", "InactiveCaption" to "COLOR_INACTIVECAPTION",
            "InactiveCaptionText" to "COLOR_INACTIVECAPTIONTEXT", "Window" to "COLOR_WINDOW",
            "WindowText" to "COLOR_WINDOWTEXT", "DisabledText" to "COLOR_GRAYTEXT",
        )
        val declarations = systemColors.flatMap { (name, source) -> listOf(
            Triple("SystemColor${name}Color", "Windows.UI.Color", "GetSysColor($source)"),
            Triple("SystemColor${name}Brush", "Microsoft.UI.Xaml.Media.SolidColorBrush", "GetSysColor($source)"),
        ) } + listOf(
            Triple("SystemColorControlAccentColor", "Windows.UI.Color", "System accent color"),
            Triple("SystemColorControlAccentBrush", "Microsoft.UI.Xaml.Media.SolidColorBrush", "System accent color"),
            Triple("SystemAccentColor", "Windows.UI.Color", "System accent color"),
        ) + listOf("Dark1", "Dark2", "Dark3", "Light1", "Light2", "Light3").map {
            Triple("SystemAccentColor$it", "Windows.UI.Color", "System variant accent color")
        } + listOf("Low", "Medium", "High").map {
            Triple("SystemListAccent${it}Color", "Windows.UI.Color", "System accent color with selection opacity")
        }
        // A read-only declaration view provides an honest navigation target.
        // Values depend on Windows settings and must not be invented here.
        val text = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<!-- WinUI runtime resource declarations. This is not an application dictionary. -->")
            appendLine("<runtime-resources provider=\"WinUI FrameworkTheming\"")
            appendLine("    documentation=\"https://github.com/microsoft/microsoft-ui-xaml/blob/main/docs/design-notes/resources.md#magic-keys\">")
            declarations.forEach { (key, type, source) ->
                appendLine("    <resource key=\"$key\" type=\"$type\" source=\"$source\" />")
            }
            appendLine("</runtime-resources>")
        }
        val virtualFile = LightVirtualFile("WinUI System Resources.xml", XMLLanguage.INSTANCE, text).apply { setWritable(false) }
        PsiManager.getInstance(project).findFile(virtualFile) as XmlFile
    }

    fun candidates(key: String?): List<WinRTResourceReferences.Key> = file.rootTag!!.subTags.mapNotNull { resource ->
        resource.getAttribute("key")?.valueElement?.takeIf { key == null || it.value == key }
            ?.let { WinRTResourceReferences.Key(it.value, it) }
    }
}
