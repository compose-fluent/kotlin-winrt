package io.github.composefluent.winrt.ide.xaml

/** Resolve the markup-extension namespace, including aliased XAML prefixes. */
internal fun isCompiledXamlBinding(value: String, namespace: (String) -> String?): Boolean {
    val prefix = Regex("^\\{\\s*([A-Za-z_][A-Za-z0-9_.-]*):Bind(?=[\\s,}])").find(value)?.groupValues?.get(1) ?: return false
    return namespace(prefix) == WinUIXamlCatalog.XAML
}
