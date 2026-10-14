package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.data.PropertyChangedEventArgs
import microsoft.ui.xaml.data.PropertyChangedEventHandler

/** The multicast event emitted per writable x:Property by CSharpPagePass1.tt.
 * Uses the existing mapped PropertyChanged delegate contract on both targets.
 */
class WinUIXamlPropertyChangedEvent {
    private val lock = PlatformLock()
    private val handlers = mutableListOf<PropertyChangedEventHandler>()

    fun add(handler: PropertyChangedEventHandler) = lock.withLock { handlers.add(handler); Unit }

    fun remove(handler: PropertyChangedEventHandler) = lock.withLock {
        // C# delegate subtraction removes the last matching registration.
        val index = handlers.lastIndexOf(handler)
        if (index >= 0) handlers.removeAt(index)
        Unit
    }

    fun raise(sender: Any, propertyName: String) {
        val snapshot = lock.withLock { handlers.toList() }
        if (snapshot.isEmpty()) return
        val arguments = PropertyChangedEventArgs(propertyName)
        snapshot.forEach { it.invoke(sender, arguments) }
    }
}
