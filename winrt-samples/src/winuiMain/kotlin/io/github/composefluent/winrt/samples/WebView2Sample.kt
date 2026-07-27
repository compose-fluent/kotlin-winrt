package io.github.composefluent.winrt.samples

internal fun normalizeWebView2Address(address: String): String? {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val normalized = if ("://" in trimmed) trimmed else "https://$trimmed"
    return normalized.takeIf {
        it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("http://", ignoreCase = true)
    }
}
