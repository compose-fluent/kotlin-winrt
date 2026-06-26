package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

enum class WinRTNotifyCollectionChangedAction(val abiValue: Int) {
    Add(0),
    Remove(1),
    Replace(2),
    Move(3),
    Reset(4),
    ;

    companion object Metadata {
        fun fromAbi(value: Int): WinRTNotifyCollectionChangedAction =
            entries.firstOrNull { it.abiValue == value }
                ?: error("Unknown NotifyCollectionChangedAction ABI value: $value")

        fun toAbi(value: WinRTNotifyCollectionChangedAction): Int = value.abiValue
    }
}

data class WinRTPropertyChangedEventArgs(
    val propertyName: String?,
)

data class WinRTDataErrorsChangedEventArgs(
    val propertyName: String?,
)

data class WinRTNotifyCollectionChangedEventArgs(
    val action: WinRTNotifyCollectionChangedAction,
    val newItems: List<Any?>? = null,
    val oldItems: List<Any?>? = null,
    val newStartingIndex: Int = -1,
    val oldStartingIndex: Int = -1,
)

typealias WinRTCanExecuteChangedHandler = EventHandlerCallback<Any?>
typealias WinRTPropertyChangedHandler = EventHandlerCallback<WinRTPropertyChangedEventArgs?>
typealias WinRTCollectionChangedHandler = EventHandlerCallback<WinRTNotifyCollectionChangedEventArgs?>
typealias WinRTDataErrorsChangedHandler = EventHandlerCallback<WinRTDataErrorsChangedEventArgs?>

interface WinRTCommand {
    fun canExecute(parameter: Any?): Boolean

    fun execute(parameter: Any?)

    fun addCanExecuteChanged(handler: WinRTCanExecuteChangedHandler)

    fun removeCanExecuteChanged(handler: WinRTCanExecuteChangedHandler)
}

interface WinRTPropertyChangedNotifier {
    fun addPropertyChanged(handler: WinRTPropertyChangedHandler)

    fun removePropertyChanged(handler: WinRTPropertyChangedHandler)
}

interface WinRTCollectionChangedNotifier {
    fun addCollectionChanged(handler: WinRTCollectionChangedHandler)

    fun removeCollectionChanged(handler: WinRTCollectionChangedHandler)
}

interface WinRTDataErrorInfo {
    val hasErrors: Boolean

    fun getErrors(propertyName: String?): Iterable<Any?>?

    fun addErrorsChanged(handler: WinRTDataErrorsChangedHandler)

    fun removeErrorsChanged(handler: WinRTDataErrorsChangedHandler)
}

interface WinRTServiceProvider {
    fun getService(type: KClass<*>?): Any?
}

interface WinRTStringable {
    override fun toString(): String
}

interface WinRTCustomProperty {
    val canRead: Boolean
    val canWrite: Boolean
    val name: String
    val type: KClass<*>?

    fun getValue(target: Any?): Any?

    fun setValue(target: Any?, value: Any?)

    fun getIndexedValue(target: Any?, index: Any?): Any?

    fun setIndexedValue(target: Any?, value: Any?, index: Any?)
}

interface WinRTCustomPropertyProvider {
    fun getCustomProperty(name: String): WinRTCustomProperty?

    fun getIndexedProperty(
        name: String,
        indexParameterType: KClass<*>?,
    ): WinRTCustomProperty?

    fun getStringRepresentation(): String

    val type: KClass<*>
}

interface WinRTBindableCustomPropertyImplementation {
    fun getCustomProperty(name: String): WinRTCustomProperty?

    fun getIndexedProperty(indexParameterType: KClass<*>?): WinRTCustomProperty?
}

/**
 * Kotlin owner for the explicit/source-generated `ICustomProperty` path mirrored from
 * `.cswinrt/src/WinRT.Runtime/Projections/ICustomPropertyProvider.net5.cs`.
 *
 * Kotlin cannot reuse the CLR reflection fallback from `.cswinrt`, so Runtime 1.19 keeps the
 * explicit provider/property contract and leaves any future DSL or codegen sugar above it.
 */
class WinRTBindableCustomProperty(
    override val canRead: Boolean,
    override val canWrite: Boolean,
    override val name: String,
    override val type: KClass<*>?,
    private val getValueCallback: ((Any?) -> Any?)? = null,
    private val setValueCallback: ((Any?, Any?) -> Unit)? = null,
    private val getIndexedValueCallback: ((Any?, Any?) -> Any?)? = null,
    private val setIndexedValueCallback: ((Any?, Any?, Any?) -> Unit)? = null,
) : WinRTCustomProperty {
    override fun getValue(target: Any?): Any? =
        getValueCallback?.invoke(target)
            ?: throw WinRTUnsupportedOperationException(
                "Custom property '$name' does not support GetValue.",
                KnownHResults.E_NOTIMPL,
            )

    override fun setValue(
        target: Any?,
        value: Any?,
    ) {
        val callback = setValueCallback
            ?: throw WinRTUnsupportedOperationException(
                "Custom property '$name' does not support SetValue.",
                KnownHResults.E_NOTIMPL,
            )
        callback(target, value)
    }

    override fun getIndexedValue(
        target: Any?,
        index: Any?,
    ): Any? =
        getIndexedValueCallback?.invoke(target, index)
            ?: throw WinRTUnsupportedOperationException(
                "Custom property '$name' does not support GetIndexedValue.",
                KnownHResults.E_NOTIMPL,
            )

    override fun setIndexedValue(
        target: Any?,
        value: Any?,
        index: Any?,
    ) {
        val callback = setIndexedValueCallback
            ?: throw WinRTUnsupportedOperationException(
                "Custom property '$name' does not support SetIndexedValue.",
                KnownHResults.E_NOTIMPL,
            )
        callback(target, value, index)
    }
}
