package microsoft.ui.xaml.data

import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import kotlin.reflect.KClass
import windows.foundation.EventHandler

data class PropertyChangedEventArgs(
    val propertyName: String?,
)

data class DataErrorsChangedEventArgs(
    val propertyName: String?,
)

typealias PropertyChangedEventHandler = EventHandler<PropertyChangedEventArgs?>

typealias DataErrorsChangedEventHandler = EventHandler<DataErrorsChangedEventArgs?>

interface INotifyPropertyChanged {
    fun addPropertyChanged(handler: PropertyChangedEventHandler)

    fun removePropertyChanged(handler: PropertyChangedEventHandler)
}

interface INotifyDataErrorInfo {
    val hasErrors: Boolean

    fun getErrors(propertyName: String?): Iterable<Any?>?

    fun addErrorsChanged(handler: DataErrorsChangedEventHandler)

    fun removeErrorsChanged(handler: DataErrorsChangedEventHandler)
}

interface ICustomProperty {
    val canRead: Boolean
    val canWrite: Boolean
    val name: String
    val type: KClass<*>?

    fun getValue(target: Any?): Any?

    fun setValue(target: Any?, value: Any?)

    fun getIndexedValue(target: Any?, index: Any?): Any?

    fun setIndexedValue(target: Any?, value: Any?, index: Any?)

    /**
     * Shared XAML binding contract owned by the runtime, together with its RCW/CCW
     * factories. The generator maps SDK references to this declaration; emitting a
     * second SDK declaration would give the same Native symbol incompatible metadata.
     */
    companion object Metadata {
        const val TYPE_NAME: String = "Microsoft.UI.Xaml.Data.ICustomProperty"
        val IID: Guid = Guid("30DA92C0-23E8-42A0-AE7C-734A0E5D2782")
        val TYPE_HANDLE: WinRTTypeHandle = WinRTTypeHandle("microsoft.ui.xaml.data.ICustomProperty", IID)
    }
}

interface ICustomPropertyProvider {
    fun getCustomProperty(name: String): ICustomProperty?

    fun getIndexedProperty(
        name: String,
        indexParameterType: KClass<*>?,
    ): ICustomProperty?

    fun getStringRepresentation(): String

    val type: KClass<*>

    /** See [ICustomProperty.Metadata]. */
    companion object Metadata {
        const val TYPE_NAME: String = "Microsoft.UI.Xaml.Data.ICustomPropertyProvider"
        val IID: Guid = Guid("7C925755-3E48-42B4-8677-76372267033F")
        val TYPE_HANDLE: WinRTTypeHandle = WinRTTypeHandle("microsoft.ui.xaml.data.ICustomPropertyProvider", IID)
    }
}
