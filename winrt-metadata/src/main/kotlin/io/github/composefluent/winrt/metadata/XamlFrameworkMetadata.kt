package io.github.composefluent.winrt.metadata

import io.github.composefluent.winrt.runtime.XamlFramework
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The compiler's framework selection mirrors CsWinRT's distinct WUX/MUX metadata names.
 * Only SDK XAML names are constructed here; user and Windows.Foundation names are unchanged.
 */
val XamlFramework.xamlNamespace: String
    get() = when (this) {
        XamlFramework.WinUi -> "Microsoft.UI.Xaml"
        XamlFramework.Uwp -> "Windows.UI.Xaml"
    }

val XamlFramework.xamlKotlinNamespace: String get() = xamlNamespace.lowercase()
fun XamlFramework.xamlTypeName(relativeName: String): String = "$xamlNamespace.$relativeName"
fun XamlFramework.xamlKotlinTypeName(relativeName: String): String =
    xamlTypeName(relativeName).let { it.substringBeforeLast('.').lowercase() + "." + it.substringAfterLast('.') }

/** UWP preserves the versioned IComponentConnector2 ABI; WinUI merges that member.
 * Windows SDK windows.ui.xaml.markup.h defines Connect and GetBindingConnector separately.
 */
val XamlFramework.xamlBindingConnectorTypeName: String
    get() = xamlTypeName(if (this == XamlFramework.Uwp) "Markup.IComponentConnector2" else "Markup.IComponentConnector")

val XamlFramework.xamlComponentInterfaceNames: List<String>
    get() = listOf(xamlTypeName("Markup.IComponentConnector"), xamlBindingConnectorTypeName).distinct()

/** Shared Kotlin typealiases expand to their implementation class in IR. As in
 * CsWinRT Projections.cs, choose the matching ABI name from the existing mapped-type table.
 */
fun XamlFramework.xamlMappedTypeName(typeName: String): String {
    val mapping = winRTMappedTypeForAbiName(typeName)?.takeIf { it.isXamlAlias } ?: return typeName
    val source = XamlFramework.entries.first { typeName.startsWith(it.xamlNamespace + ".") }
    val selected = xamlTypeName(typeName.removePrefix(source.xamlNamespace + "."))
    val target = winRTMappedTypeForAbiName(selected) ?: return typeName
    return if (target.mappedQualifiedName == mapping.mappedQualifiedName) selected else typeName
}

fun parseXamlFramework(value: String): XamlFramework = when (value.lowercase()) {
    "winui" -> XamlFramework.WinUi
    "uwp" -> XamlFramework.Uwp
    else -> throw IllegalArgumentException("Unknown XAML framework '$value'; expected winui or uwp.")
}

fun isXamlTypeName(name: String?, relativeName: String): Boolean =
    XamlFramework.entries.any { name == it.xamlTypeName(relativeName) }

internal object XamlFrameworkSerializer : KSerializer<XamlFramework> {
    override val descriptor = PrimitiveSerialDescriptor("XamlFramework", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: XamlFramework) = encoder.encodeString(value.name.lowercase())
    override fun deserialize(decoder: Decoder): XamlFramework = parseXamlFramework(decoder.decodeString())
}
