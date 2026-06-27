package microsoft.ui.xaml.interop

import windows.foundation.EventHandler

typealias ICommand = microsoft.ui.xaml.input.ICommand

enum class NotifyCollectionChangedAction(val abiValue: Int) {
    Add(0),
    Remove(1),
    Replace(2),
    Move(3),
    Reset(4),
    ;

    companion object Metadata {
        fun fromAbi(value: Int): NotifyCollectionChangedAction =
            entries.firstOrNull { it.abiValue == value }
                ?: error("Unknown NotifyCollectionChangedAction ABI value: $value")

        fun toAbi(value: NotifyCollectionChangedAction): Int = value.abiValue
    }
}

data class NotifyCollectionChangedEventArgs(
    val action: NotifyCollectionChangedAction,
    val newItems: List<Any?>? = null,
    val oldItems: List<Any?>? = null,
    val newStartingIndex: Int = -1,
    val oldStartingIndex: Int = -1,
)

typealias NotifyCollectionChangedEventHandler = EventHandler<NotifyCollectionChangedEventArgs?>

interface INotifyCollectionChanged {
    fun addCollectionChanged(handler: NotifyCollectionChangedEventHandler)

    fun removeCollectionChanged(handler: NotifyCollectionChangedEventHandler)
}
