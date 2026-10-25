package io.github.composefluent.winrt.runtime

/** Compiler-generated, typed event dispatch on the owning UI thread.
 * Like CsWinRT EventSource's stable native invoker and mutable managed handlers,
 * retargeting keeps the original subscription/token. No executable code or member
 * reflection crosses the development transport. Empty names disable dispatch.
 */
class WinUIXamlEventRoute(initialHandler: String, handlers: Array<String>) {
    private val allowed = handlers.toSet()
    var handlerName: String = initialHandler
        private set
    init { require(initialHandler in allowed) }
    internal fun validate(expected: String, replacement: String) {
        check(handlerName == expected) { "The compiled event handler changed; reconnect." }
        require(replacement.isEmpty() || replacement in allowed) {
            "Handler '$replacement' was not compiled with a compatible signature; rebuild."
        }
    }
    internal fun select(name: String) { handlerName = name }
}
