package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import microsoft.ui.xaml.IXamlServiceProvider
import microsoft.ui.xaml.data.DataErrorsChangedEventArgs
import microsoft.ui.xaml.data.ICustomProperty
import microsoft.ui.xaml.data.ICustomPropertyProvider
import microsoft.ui.xaml.data.INotifyPropertyChanged
import microsoft.ui.xaml.data.PropertyChangedEventArgs
import microsoft.ui.xaml.data.PropertyChangedEventHandler
import microsoft.ui.xaml.input.CanExecuteChangedEventHandler
import microsoft.ui.xaml.input.ICommand
import microsoft.ui.xaml.interop.NotifyCollectionChangedAction
import microsoft.ui.xaml.interop.NotifyCollectionChangedEventArgs
import windows.foundation.IStringable

class XamlSystemProjectionRuntimeTest {
    @Test
    fun xaml_runtime_cache_close_is_idempotent() {
        XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
        XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
    }

    @Test
    fun command_projection_round_trips_methods_and_event_handlers() {
        val command = TestCommand()

        ComWrappersSupport.createCCWForObject(command, IID.ICommand).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer().asRawAddress(),
                    WinRTTypeHandle(TypeNameSupport.getNameForType(ICommand::class), IID.ICommand),
                ) as ICommand

            assertTrue(projected.canExecute("go"))
            assertFalse(projected.canExecute("stop"))

            projected.execute("payload")
            assertEquals(listOf<Any?>("payload"), command.executed)

            val received = mutableListOf<Pair<Any?, Any?>>()
            val handler: CanExecuteChangedEventHandler = { sender, args -> received += sender to args }
            projected.addCanExecuteChanged(handler)

            command.raise(sender = "sender", args = "args")
            assertEquals(listOf<Pair<Any?, Any?>>("sender" to "args"), received)

            projected.removeCanExecuteChanged(handler)
            command.raise(sender = "sender2", args = "args2")
            assertEquals(listOf<Pair<Any?, Any?>>("sender" to "args"), received)
        }
    }

    @Test
    fun property_changed_notifier_round_trips_projected_event_args() {
        val notifier = TestPropertyChangedNotifier()

        ComWrappersSupport.createCCWForObject(notifier, IID.MUX_INotifyPropertyChanged).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer().asRawAddress(),
                    WinRTTypeHandle(
                        TypeNameSupport.getNameForType(INotifyPropertyChanged::class),
                        IID.MUX_INotifyPropertyChanged,
                    ),
                ) as INotifyPropertyChanged

            val received = mutableListOf<Pair<Any?, String?>>()
            val handler: PropertyChangedEventHandler = { sender, args -> received += sender to args?.propertyName }
            projected.addPropertyChanged(handler)

            notifier.raise("vm", PropertyChangedEventArgs("Title"))
            assertEquals(listOf<Pair<Any?, String?>>("vm" to "Title"), received)

            projected.removePropertyChanged(handler)
            notifier.raise("vm2", PropertyChangedEventArgs("Ignored"))
            assertEquals(listOf<Pair<Any?, String?>>("vm" to "Title"), received)
        }
    }

    @Test
    fun runtime_class_event_args_project_from_runtime_name() {
        val propertyChanged = PropertyChangedEventArgs("Name")
        val collectionChanged =
            NotifyCollectionChangedEventArgs(
                action = NotifyCollectionChangedAction.Replace,
                newItems = listOf("new"),
                oldItems = listOf("old"),
                newStartingIndex = 3,
                oldStartingIndex = 1,
            )
        val dataErrors = DataErrorsChangedEventArgs("Field")

        ComWrappersSupport.createCCWForObject(propertyChanged).use { reference ->
            val projected = ComWrappersSupport.createRcwForComObject(reference.getRefPointer().asRawAddress()) as PropertyChangedEventArgs
            assertEquals("Name", projected.propertyName)
        }

        ComWrappersSupport.createCCWForObject(collectionChanged).use { reference ->
            val projected = ComWrappersSupport.createRcwForComObject(reference.getRefPointer().asRawAddress()) as NotifyCollectionChangedEventArgs
            assertEquals(NotifyCollectionChangedAction.Replace, projected.action)
            assertEquals(listOf("new"), projected.newItems)
            assertEquals(listOf("old"), projected.oldItems)
            assertEquals(3, projected.newStartingIndex)
            assertEquals(1, projected.oldStartingIndex)
        }

        ComWrappersSupport.createCCWForObject(dataErrors).use { reference ->
            val projected = ComWrappersSupport.createRcwForComObject(reference.getRefPointer().asRawAddress()) as DataErrorsChangedEventArgs
            assertEquals("Field", projected.propertyName)
        }
    }

    @Test
    fun custom_property_provider_and_service_provider_round_trip() {
        val target = TestBindableTarget()
        val serviceProvider = TestServiceProvider()

        ComWrappersSupport.createCCWForObject(target, IID.ICustomPropertyProvider).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer().asRawAddress(),
                    WinRTTypeHandle(
                        TypeNameSupport.getNameForType(ICustomPropertyProvider::class),
                        IID.ICustomPropertyProvider,
                    ),
                ) as ICustomPropertyProvider

            val property = projected.getCustomProperty("value")
            assertNotNull(property)
            assertEquals("value", property.name)
            assertEquals(Int::class, property.type)
            assertEquals(5, property.getValue(target))

            property.setValue(target, 9)
            assertEquals(9, target.value)
        }

        ComWrappersSupport.createCCWForObject(serviceProvider, IID.IServiceProvider).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer().asRawAddress(),
                    WinRTTypeHandle(TypeNameSupport.getNameForType(IXamlServiceProvider::class), IID.IServiceProvider),
                ) as IXamlServiceProvider

            assertEquals("string-service", projected.getService(String::class))
            assertEquals(7, projected.getService(Int::class))
            assertNull(projected.getService(Double::class))
        }
    }

    @Test
    fun plain_objects_gain_istringable_projection() {
        ComWrappersSupport.createCCWForObject(42).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer().asRawAddress(),
                    WinRTTypeHandle(TypeNameSupport.getNameForType(IStringable::class), IID.IStringable),
                ) as IStringable

            assertEquals("42", projected.toString())
        }
    }

    @Test
    fun plain_objects_gain_default_custom_property_provider_projection() {
        val target = PlainBindableTarget("plain-value")

        ComWrappersSupport.createCCWForObject(target, IID.ICustomPropertyProvider).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer().asRawAddress(),
                    WinRTTypeHandle(
                        TypeNameSupport.getNameForType(ICustomPropertyProvider::class),
                        IID.ICustomPropertyProvider,
                    ),
                ) as ICustomPropertyProvider

            assertNull(projected.getCustomProperty("value"))
            assertNull(projected.getIndexedProperty("value", Int::class))
            assertEquals("plain-value", projected.getStringRepresentation())
        }
    }

    private class TestCommand : ICommand {
        val executed = mutableListOf<Any?>()
        private val handlers = linkedSetOf<CanExecuteChangedEventHandler>()

        override fun canExecute(parameter: Any?): Boolean = parameter != "stop"

        override fun execute(parameter: Any?) {
            executed += parameter
        }

        override fun addCanExecuteChanged(handler: CanExecuteChangedEventHandler) {
            handlers += handler
        }

        override fun removeCanExecuteChanged(handler: CanExecuteChangedEventHandler) {
            handlers -= handler
        }

        fun raise(
            sender: Any?,
            args: Any?,
        ) {
            handlers.toList().forEach { handler -> handler(sender, args) }
        }
    }

    private class TestPropertyChangedNotifier : INotifyPropertyChanged {
        private val handlers = linkedSetOf<PropertyChangedEventHandler>()

        override fun addPropertyChanged(handler: PropertyChangedEventHandler) {
            handlers += handler
        }

        override fun removePropertyChanged(handler: PropertyChangedEventHandler) {
            handlers -= handler
        }

        fun raise(
            sender: Any?,
            args: PropertyChangedEventArgs?,
        ) {
            handlers.toList().forEach { handler -> handler(sender, args) }
        }
    }

    private class TestBindableTarget : WinRTBindableCustomPropertyImplementation {
        var value: Int = 5

        override fun getCustomProperty(name: String): ICustomProperty? =
            if (name == "value") {
                WinRTBindableCustomProperty(
                    canRead = true,
                    canWrite = true,
                    name = "value",
                    type = Int::class,
                    getValueCallback = { target -> (target as TestBindableTarget).value },
                    setValueCallback = { target, newValue ->
                        (target as TestBindableTarget).value = (newValue as Number).toInt()
                    },
                )
            } else {
                null
            }

        override fun getIndexedProperty(indexParameterType: KClass<*>?): ICustomProperty? = null
    }

    private class TestServiceProvider : IXamlServiceProvider {
        override fun getService(type: KClass<*>?): Any? =
            when (type) {
                String::class -> "string-service"
                Int::class -> 7
                else -> null
            }
    }

    private data class PlainBindableTarget(val label: String) {
        override fun toString(): String = label
    }
}
