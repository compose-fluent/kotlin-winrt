package io.github.composefluent.winrt.metadata

/** DirectUISchemaContext.DirectUI2010Paths in XAMLC owns the presentation namespace aliases. */
object WinRTXamlNamespaces {
    const val PRESENTATION = "http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    const val XAML = "http://schemas.microsoft.com/winfx/2006/xaml"
    private val presentation = listOf("Microsoft.UI.Xaml", "Microsoft.UI.Xaml.Automation",
        "Microsoft.UI.Xaml.Automation.Peers", "Microsoft.UI.Xaml.Automation.Provider", "Microsoft.UI.Xaml.Controls",
        "Microsoft.UI.Xaml.Controls.Primitives", "Microsoft.UI.Xaml.Data", "Microsoft.UI.Xaml.Documents",
        "Microsoft.UI.Xaml.Input", "Microsoft.UI.Xaml.Interop", "Microsoft.UI.Xaml.Markup", "Microsoft.UI.Xaml.Media",
        "Microsoft.UI.Xaml.Media.Animation", "Microsoft.UI.Xaml.Media.Imaging", "Microsoft.UI.Xaml.Media.Media3D",
        "Microsoft.UI.Xaml.Navigation", "Microsoft.UI.Xaml.Resources", "Microsoft.UI.Xaml.Shapes",
        "Microsoft.UI.Xaml.Threading", "Windows.UI", "Windows.UI.Text")

    fun namespaces(uri: String): List<String> = when {
        uri.substringBefore('?') in setOf(PRESENTATION, "http://schemas.microsoft.com/windows/2010/directui") -> presentation
        uri.startsWith("using:") -> listOf(uri.removePrefix("using:").substringBefore('?')).filter { it.isNotBlank() }
        else -> emptyList()
    }
}
