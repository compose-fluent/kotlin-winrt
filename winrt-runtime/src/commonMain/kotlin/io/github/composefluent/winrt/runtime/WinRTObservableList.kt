package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.data.INotifyPropertyChanged
import microsoft.ui.xaml.data.PropertyChangedEventArgs
import microsoft.ui.xaml.data.PropertyChangedEventHandler
import microsoft.ui.xaml.interop.INotifyCollectionChanged
import microsoft.ui.xaml.interop.NotifyCollectionChangedAction
import microsoft.ui.xaml.interop.NotifyCollectionChangedEventArgs
import microsoft.ui.xaml.interop.NotifyCollectionChangedEventHandler

/**
 * Kotlin counterpart of ObservableCollection<T>. CsWinRT projects its BCL
 * notification interfaces in Projections/INotifyCollectionChanged.net5.cs and
 * INotifyPropertyChanged.net5.cs; the existing runtime owns their CCW/token ABI.
 * List projection and notification identity therefore remain on the same object.
 */
class WinRTObservableList<T>(items: Collection<T> = emptyList()) : AbstractMutableList<T>(),
    INotifyCollectionChanged, INotifyPropertyChanged {
    private val values = items.toMutableList()
    private val collectionHandlers = mutableListOf<NotifyCollectionChangedEventHandler>()
    private val propertyHandlers = mutableListOf<PropertyChangedEventHandler>()
    private var notificationDepth = 0

    override val size: Int get() = values.size
    override fun get(index: Int): T = values[index]

    override fun add(index: Int, element: T) {
        checkReentrancy()
        values.add(index, element)
        changed(NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Add,
            newItems = listOf(element), newStartingIndex = index), countChanged = true)
    }

    override fun removeAt(index: Int): T {
        checkReentrancy()
        val old = values.removeAt(index)
        changed(NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Remove,
            oldItems = listOf(old), oldStartingIndex = index), countChanged = true)
        return old
    }

    override fun set(index: Int, element: T): T {
        checkReentrancy()
        val old = values.set(index, element)
        changed(NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Replace,
            newItems = listOf(element), oldItems = listOf(old), newStartingIndex = index, oldStartingIndex = index))
        return old
    }

    override fun clear() {
        checkReentrancy()
        values.clear()
        changed(NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset), countChanged = true)
    }

    fun move(oldIndex: Int, newIndex: Int) {
        checkReentrancy()
        require(newIndex in values.indices) { "Destination index $newIndex is outside this collection" }
        val item = values.removeAt(oldIndex)
        values.add(newIndex, item)
        changed(NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Move,
            newItems = listOf(item), oldItems = listOf(item), newStartingIndex = newIndex, oldStartingIndex = oldIndex))
    }

    override fun addCollectionChanged(handler: NotifyCollectionChangedEventHandler) { collectionHandlers.add(handler) }
    override fun removeCollectionChanged(handler: NotifyCollectionChangedEventHandler) { collectionHandlers.remove(handler) }
    override fun addPropertyChanged(handler: PropertyChangedEventHandler) { propertyHandlers.add(handler) }
    override fun removePropertyChanged(handler: PropertyChangedEventHandler) { propertyHandlers.remove(handler) }

    private fun checkReentrancy() {
        check(notificationDepth == 0 || collectionHandlers.size <= 1) {
            "Cannot change an observable collection during a collection notification with multiple subscribers"
        }
    }

    private fun changed(args: NotifyCollectionChangedEventArgs, countChanged: Boolean = false) {
        if (countChanged) propertyChanged("Count")
        propertyChanged("Item[]")
        notificationDepth++
        try { collectionHandlers.toList().forEach { it(this, args) } } finally { notificationDepth-- }
    }

    private fun propertyChanged(name: String) {
        propertyHandlers.toList().forEach { it(this, PropertyChangedEventArgs(name)) }
    }
}
