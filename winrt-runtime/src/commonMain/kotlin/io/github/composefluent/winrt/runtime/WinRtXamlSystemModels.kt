package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

enum class WinRtNotifyCollectionChangedAction(val abiValue: Int) {
    Add(0),
    Remove(1),
    Replace(2),
    Move(3),
    Reset(4),
    ;

    companion object Metadata {
        fun fromAbi(value: Int): WinRtNotifyCollectionChangedAction =
            entries.firstOrNull { it.abiValue == value }
                ?: error("Unknown NotifyCollectionChangedAction ABI value: $value")

        fun toAbi(value: WinRtNotifyCollectionChangedAction): Int = value.abiValue
    }
}

data class WinRtPropertyChangedEventArgs(
    val propertyName: String?,
)

data class WinRtDataErrorsChangedEventArgs(
    val propertyName: String?,
)

data class WinRtNotifyCollectionChangedEventArgs(
    val action: WinRtNotifyCollectionChangedAction,
    val newItems: List<Any?>? = null,
    val oldItems: List<Any?>? = null,
    val newStartingIndex: Int = -1,
    val oldStartingIndex: Int = -1,
)

typealias WinRtCanExecuteChangedHandler = EventHandlerCallback<Any?>
typealias WinRtPropertyChangedHandler = EventHandlerCallback<WinRtPropertyChangedEventArgs?>
typealias WinRtCollectionChangedHandler = EventHandlerCallback<WinRtNotifyCollectionChangedEventArgs?>
typealias WinRtDataErrorsChangedHandler = EventHandlerCallback<WinRtDataErrorsChangedEventArgs?>

interface WinRtCommand {
    fun canExecute(parameter: Any?): Boolean

    fun execute(parameter: Any?)

    fun addCanExecuteChanged(handler: WinRtCanExecuteChangedHandler)

    fun removeCanExecuteChanged(handler: WinRtCanExecuteChangedHandler)
}

interface WinRtPropertyChangedNotifier {
    fun addPropertyChanged(handler: WinRtPropertyChangedHandler)

    fun removePropertyChanged(handler: WinRtPropertyChangedHandler)
}

interface WinRtCollectionChangedNotifier {
    fun addCollectionChanged(handler: WinRtCollectionChangedHandler)

    fun removeCollectionChanged(handler: WinRtCollectionChangedHandler)
}

interface WinRtDataErrorInfo {
    val hasErrors: Boolean

    fun getErrors(propertyName: String?): Iterable<Any?>?

    fun addErrorsChanged(handler: WinRtDataErrorsChangedHandler)

    fun removeErrorsChanged(handler: WinRtDataErrorsChangedHandler)
}

interface WinRtServiceProvider {
    fun getService(type: KClass<*>?): Any?
}

interface WinRtStringable {
    override fun toString(): String
}

interface WinRtCustomProperty {
    val canRead: Boolean
    val canWrite: Boolean
    val name: String
    val type: KClass<*>?

    fun getValue(target: Any?): Any?

    fun setValue(target: Any?, value: Any?)

    fun getIndexedValue(target: Any?, index: Any?): Any?

    fun setIndexedValue(target: Any?, value: Any?, index: Any?)
}

interface WinRtCustomPropertyProvider {
    fun getCustomProperty(name: String): WinRtCustomProperty?

    fun getIndexedProperty(
        name: String,
        indexParameterType: KClass<*>?,
    ): WinRtCustomProperty?

    fun getStringRepresentation(): String

    val type: KClass<*>
}

interface WinRtBindableCustomPropertyImplementation {
    fun getCustomProperty(name: String): WinRtCustomProperty?

    fun getIndexedProperty(indexParameterType: KClass<*>?): WinRtCustomProperty?
}

/**
 * Kotlin owner for the explicit/source-generated `ICustomProperty` path mirrored from
 * `.cswinrt/src/WinRT.Runtime/Projections/ICustomPropertyProvider.net5.cs`.
 *
 * Kotlin cannot reuse the CLR reflection fallback from `.cswinrt`, so Runtime 1.19 keeps the
 * explicit provider/property contract and leaves any future DSL or codegen sugar above it.
 */
class WinRtBindableCustomProperty(
    override val canRead: Boolean,
    override val canWrite: Boolean,
    override val name: String,
    override val type: KClass<*>?,
    private val getValueCallback: ((Any?) -> Any?)? = null,
    private val setValueCallback: ((Any?, Any?) -> Unit)? = null,
    private val getIndexedValueCallback: ((Any?, Any?) -> Any?)? = null,
    private val setIndexedValueCallback: ((Any?, Any?, Any?) -> Unit)? = null,
) : WinRtCustomProperty {
    override fun getValue(target: Any?): Any? =
        getValueCallback?.invoke(target)
            ?: throw WinRtUnsupportedOperationException(
                "Custom property '$name' does not support GetValue.",
                KnownHResults.E_NOTIMPL,
            )

    override fun setValue(
        target: Any?,
        value: Any?,
    ) {
        val callback = setValueCallback
            ?: throw WinRtUnsupportedOperationException(
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
            ?: throw WinRtUnsupportedOperationException(
                "Custom property '$name' does not support GetIndexedValue.",
                KnownHResults.E_NOTIMPL,
            )

    override fun setIndexedValue(
        target: Any?,
        value: Any?,
        index: Any?,
    ) {
        val callback = setIndexedValueCallback
            ?: throw WinRtUnsupportedOperationException(
                "Custom property '$name' does not support SetIndexedValue.",
                KnownHResults.E_NOTIMPL,
            )
        callback(target, value, index)
    }
}
