package io.github.composefluent.winrt.ide.xaml

/** XAML's two keyed-resource expressions, shared by navigation and live updates. */
internal data class WinRTXamlResourceExpression(val key: String, val theme: Boolean) {
    companion object {
        private val expression = Regex("^\\{(StaticResource|ThemeResource)\\s+(?:ResourceKey\\s*=\\s*)?([^,}]+)\\s*}$")

        fun parse(value: String): WinRTXamlResourceExpression? {
            val match = expression.matchEntire(value.trim()) ?: return null
            val key = match.groupValues[2].trim().trim('\'', '"')
            return key.takeIf { it.isNotEmpty() }?.let { WinRTXamlResourceExpression(it, match.groupValues[1] == "ThemeResource") }
        }
    }
}
