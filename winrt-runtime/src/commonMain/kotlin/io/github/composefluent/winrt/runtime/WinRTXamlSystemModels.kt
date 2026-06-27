package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.data.ICustomProperty
import kotlin.reflect.KClass

interface WinRTBindableCustomPropertyImplementation {
    fun getCustomProperty(name: String): ICustomProperty?

    fun getIndexedProperty(indexParameterType: KClass<*>?): ICustomProperty?
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
) : ICustomProperty {
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
