package io.github.composefluent.winrt.runtime

/** Per-page guard matching XamlCompiler's pre-LoadComponent reentry guard; see XAML_CONTRACT.md. */
class WinRTXamlLoadState {
    private var started = false
    private var failure: Throwable? = null

    fun load(action: () -> Unit) {
        failure?.let { throw IllegalStateException("XAML component initialization previously failed", it) }
        if (started) return
        started = true
        try {
            action()
        } catch (error: Throwable) {
            failure = error
            throw error
        }
    }
}

fun <T : Any> requireXamlNamedElement(value: T?, page: String, element: String): T =
    requireNotNull(value) { "XAML element '$element' in '$page' has not been connected; call initializeComponent() first" }
