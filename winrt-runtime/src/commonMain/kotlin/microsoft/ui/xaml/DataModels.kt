package microsoft.ui.xaml.data

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
}

interface ICustomPropertyProvider {
    fun getCustomProperty(name: String): ICustomProperty?

    fun getIndexedProperty(
        name: String,
        indexParameterType: KClass<*>?,
    ): ICustomProperty?

    fun getStringRepresentation(): String

    val type: KClass<*>
}
