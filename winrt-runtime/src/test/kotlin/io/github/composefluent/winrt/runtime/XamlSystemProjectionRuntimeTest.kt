package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class XamlSystemProjectionRuntimeTest {
    @Test
    fun xaml_runtime_cache_close_is_idempotent() {
        XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
        XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
    }

    @Test
    fun xaml_application_exit_complete_is_idempotent() {
        try {
            XamlSystemProjectionRuntimeHooks.resetApplicationExitForTests()
            XamlSystemProjectionRuntimeHooks.completeApplicationExit()
            XamlSystemProjectionRuntimeHooks.completeApplicationExit()
        } finally {
            XamlSystemProjectionRuntimeHooks.resetApplicationExitForTests()
        }
    }

    @Test
    fun xaml_application_exit_prepare_marks_dispatcher_shutdown_phase() {
        try {
            XamlSystemProjectionRuntimeHooks.resetApplicationExitForTests()
            assertFalse(XamlSystemProjectionRuntimeHooks.isApplicationExitInProgress())

            XamlSystemProjectionRuntimeHooks.prepareForApplicationExit()

            assertTrue(XamlSystemProjectionRuntimeHooks.isApplicationExitInProgress())
        } finally {
            XamlSystemProjectionRuntimeHooks.resetApplicationExitForTests()
        }
    }

    @Test
    fun xaml_application_start_without_runtime_scope_does_not_defer_outer_close() {
        assertFalse(XamlSystemProjectionRuntimeHooks.consumeApplicationStartOwnedRuntimeScopeClose())

        XamlSystemProjectionRuntimeHooks.runWithApplicationStart {}

        assertFalse(XamlSystemProjectionRuntimeHooks.consumeApplicationStartOwnedRuntimeScopeClose())
    }

    @Test
    fun xaml_application_start_transfers_active_runtime_scope_close() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeSingleThreaded().use {
            XamlSystemProjectionRuntimeHooks.runWithApplicationStart {}
        }

        assertFalse(XamlSystemProjectionRuntimeHooks.consumeApplicationStartOwnedRuntimeScopeClose())
    }

    @Test
    fun xaml_application_exit_prepare_closes_active_event_registrations() {
        EventSourceShutdownRegistry.clearForTests()
        var closed = 0
        EventSourceShutdownRegistry.register {
            closed += 1
        }

        try {
            XamlSystemProjectionRuntimeHooks.prepareForApplicationExit()

            assertEquals(1, closed)
            EventSourceShutdownRegistry.closeAllForTests()
            assertEquals(1, closed)
        } finally {
            EventSourceShutdownRegistry.clearForTests()
        }
    }

    @Test
    fun command_projection_round_trips_methods_and_event_handlers() {
        val command = TestCommand()

        ComWrappersSupport.createCCWForObject(command, IID.ICommand).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer(),
                    WinRtTypeHandle(TypeNameSupport.getNameForType(WinRtCommand::class), IID.ICommand),
                ) as WinRtCommand

            assertTrue(projected.canExecute("go"))
            assertFalse(projected.canExecute("stop"))

            projected.execute("payload")
            assertEquals(listOf("payload"), command.executed)

            val received = mutableListOf<Pair<Any?, Any?>>()
            val handler: WinRtCanExecuteChangedHandler = { sender, args -> received += sender to args }
            projected.addCanExecuteChanged(handler)

            command.raise(sender = "sender", args = "args")
            assertEquals(listOf("sender" to "args"), received)

            projected.removeCanExecuteChanged(handler)
            command.raise(sender = "sender2", args = "args2")
            assertEquals(listOf("sender" to "args"), received)
        }
    }

    @Test
    fun property_changed_notifier_round_trips_projected_event_args() {
        val notifier = TestPropertyChangedNotifier()

        ComWrappersSupport.createCCWForObject(notifier, IID.MUX_INotifyPropertyChanged).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer(),
                    WinRtTypeHandle(
                        TypeNameSupport.getNameForType(WinRtPropertyChangedNotifier::class),
                        IID.MUX_INotifyPropertyChanged,
                    ),
                ) as WinRtPropertyChangedNotifier

            val received = mutableListOf<Pair<Any?, String?>>()
            val handler: WinRtPropertyChangedHandler = { sender, args -> received += sender to args?.propertyName }
            projected.addPropertyChanged(handler)

            notifier.raise("vm", WinRtPropertyChangedEventArgs("Title"))
            assertEquals(listOf("vm" to "Title"), received)

            projected.removePropertyChanged(handler)
            notifier.raise("vm2", WinRtPropertyChangedEventArgs("Ignored"))
            assertEquals(listOf("vm" to "Title"), received)
        }
    }

    @Test
    fun runtime_class_event_args_project_from_runtime_name() {
        val propertyChanged = WinRtPropertyChangedEventArgs("Name")
        val collectionChanged =
            WinRtNotifyCollectionChangedEventArgs(
                action = WinRtNotifyCollectionChangedAction.Replace,
                newItems = listOf("new"),
                oldItems = listOf("old"),
                newStartingIndex = 3,
                oldStartingIndex = 1,
            )
        val dataErrors = WinRtDataErrorsChangedEventArgs("Field")

        ComWrappersSupport.createCCWForObject(propertyChanged).use { reference ->
            val projected = ComWrappersSupport.createRcwForComObject(reference.getRefPointer().asRawAddress()) as WinRtPropertyChangedEventArgs
            assertEquals("Name", projected.propertyName)
        }

        ComWrappersSupport.createCCWForObject(collectionChanged).use { reference ->
            val projected = ComWrappersSupport.createRcwForComObject(reference.getRefPointer().asRawAddress()) as WinRtNotifyCollectionChangedEventArgs
            assertEquals(WinRtNotifyCollectionChangedAction.Replace, projected.action)
            assertEquals(listOf("new"), projected.newItems)
            assertEquals(listOf("old"), projected.oldItems)
            assertEquals(3, projected.newStartingIndex)
            assertEquals(1, projected.oldStartingIndex)
        }

        ComWrappersSupport.createCCWForObject(dataErrors).use { reference ->
            val projected = ComWrappersSupport.createRcwForComObject(reference.getRefPointer().asRawAddress()) as WinRtDataErrorsChangedEventArgs
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
                    reference.getRefPointer(),
                    WinRtTypeHandle(
                        TypeNameSupport.getNameForType(WinRtCustomPropertyProvider::class),
                        IID.ICustomPropertyProvider,
                    ),
                ) as WinRtCustomPropertyProvider

            val property = projected.getCustomProperty("value")
            assertNotNull(property)
            assertEquals("value", property!!.name)
            assertEquals(Int::class, property.type)
            assertEquals(5, property.getValue(target))

            property.setValue(target, 9)
            assertEquals(9, target.value)
        }

        ComWrappersSupport.createCCWForObject(serviceProvider, IID.IServiceProvider).use { reference ->
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    reference.getRefPointer(),
                    WinRtTypeHandle(TypeNameSupport.getNameForType(WinRtServiceProvider::class), IID.IServiceProvider),
                ) as WinRtServiceProvider

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
                    reference.getRefPointer(),
                    WinRtTypeHandle(TypeNameSupport.getNameForType(WinRtStringable::class), IID.IStringable),
                ) as WinRtStringable

            assertEquals("42", projected.stringRepresentation())
        }
    }

    private class TestCommand : WinRtCommand {
        val executed = mutableListOf<Any?>()
        private val handlers = linkedSetOf<WinRtCanExecuteChangedHandler>()

        override fun canExecute(parameter: Any?): Boolean = parameter != "stop"

        override fun execute(parameter: Any?) {
            executed += parameter
        }

        override fun addCanExecuteChanged(handler: WinRtCanExecuteChangedHandler) {
            handlers += handler
        }

        override fun removeCanExecuteChanged(handler: WinRtCanExecuteChangedHandler) {
            handlers -= handler
        }

        fun raise(
            sender: Any?,
            args: Any?,
        ) {
            handlers.toList().forEach { handler -> handler(sender, args) }
        }
    }

    private class TestPropertyChangedNotifier : WinRtPropertyChangedNotifier {
        private val handlers = linkedSetOf<WinRtPropertyChangedHandler>()

        override fun addPropertyChanged(handler: WinRtPropertyChangedHandler) {
            handlers += handler
        }

        override fun removePropertyChanged(handler: WinRtPropertyChangedHandler) {
            handlers -= handler
        }

        fun raise(
            sender: Any?,
            args: WinRtPropertyChangedEventArgs?,
        ) {
            handlers.toList().forEach { handler -> handler(sender, args) }
        }
    }

    private class TestBindableTarget : WinRtBindableCustomPropertyImplementation {
        var value: Int = 5

        override fun getCustomProperty(name: String): WinRtCustomProperty? =
            if (name == "value") {
                WinRtBindableCustomProperty(
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

        override fun getIndexedProperty(indexParameterType: KClass<*>?): WinRtCustomProperty? = null
    }

    private class TestServiceProvider : WinRtServiceProvider {
        override fun getService(type: KClass<*>?): Any? =
            when (type) {
                String::class -> "string-service"
                Int::class -> 7
                else -> null
            }
    }
}
