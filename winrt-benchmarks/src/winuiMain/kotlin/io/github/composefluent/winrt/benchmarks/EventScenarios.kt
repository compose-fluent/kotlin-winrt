package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.ClassWithMarshalingRoutines
import benchmarkcomponent.EventOperations
import benchmarkcomponent.IEvents
import io.github.composefluent.winrt.runtime.EventRegistrationTokenTable
import io.github.composefluent.winrt.runtime.WinRTEvent
import windows.foundation.EventHandler
import windows.foundation.EventRegistrationToken

private class ManagedEvents : IEvents {
    private val intHandlers = EventRegistrationTokenTable.create<EventHandler<Int>>()
    private val doubleHandlers = EventRegistrationTokenTable.create<EventHandler<Double>>()

    override val intPropertyChanged = WinRTEvent<EventHandler<Int>>(
        subscribe = intHandlers::addEventHandler,
        unsubscribe = { token -> intHandlers.removeEventHandler(token) },
    )

    override val doublePropertyChanged = WinRTEvent<EventHandler<Double>>(
        subscribe = doubleHandlers::addEventHandler,
        unsubscribe = { token -> doubleHandlers.removeEventHandler(token) },
    )

    override fun raiseDoubleChanged() {
        doubleHandlers.forEachHandler { handler -> handler(this, 2.2) }
    }

    override fun raiseIntChanged() {
        intHandlers.forEachHandler { handler -> handler(this, 4) }
    }

    override fun addIntPropertyChanged(handler: EventHandler<Int>): EventRegistrationToken =
        intPropertyChanged.add(handler)

    override fun removeIntPropertyChanged(token: EventRegistrationToken) {
        intPropertyChanged.remove(token)
    }

    override fun addDoublePropertyChanged(handler: EventHandler<Double>): EventRegistrationToken =
        doublePropertyChanged.add(handler)

    override fun removeDoublePropertyChanged(token: EventRegistrationToken) {
        doublePropertyChanged.remove(token)
    }
}

private class EventPerf {
    private lateinit var instance: ClassWithMarshalingRoutines
    private var z2 = 0
    private lateinit var instance2: ClassWithMarshalingRoutines
    private var z4 = 0
    private lateinit var events: ManagedEvents
    private lateinit var operations: EventOperations

    fun setup() {
        instance = ClassWithMarshalingRoutines()
        val handler: EventHandler<Int> = { _, value -> z2 = value }
        instance.intPropertyChanged += handler

        instance2 = ClassWithMarshalingRoutines()
        val checkedHandler: EventHandler<Int> = { sender, value ->
            z4 = if (sender == this) value else value * 3
        }
        instance2.intPropertyChanged += checkedHandler

        events = ManagedEvents()
        operations = EventOperations(events)
        operations.addIntEvent()
    }

    fun intEventOverhead(): Any {
        val instance = ClassWithMarshalingRoutines()
        var z = 0
        val handler: EventHandler<Int> = { _, value -> z = value }
        return instance
    }

    fun addIntEventToNewEventSource(): Any {
        val instance = ClassWithMarshalingRoutines()
        var z = 0
        val handler: EventHandler<Int> = { _, value -> z = value }
        instance.intPropertyChanged += handler
        return instance
    }

    fun addMultipleEventsToNewEventSource(): Any {
        val instance = ClassWithMarshalingRoutines()
        var z = 0
        var y = 0.0
        val intHandler: EventHandler<Int> = { _, value -> z = value }
        val doubleHandler: EventHandler<Double> = { _, value -> y = value }
        instance.intPropertyChanged += intHandler
        instance.doublePropertyChanged += doubleHandler
        return instance
    }

    fun addAndInvokeIntEventOnNewEventSource(): Any {
        val instance = ClassWithMarshalingRoutines()
        var z = 0
        val handler: EventHandler<Int> = { _, value -> z = value }
        instance.intPropertyChanged += handler
        instance.raiseIntChanged()
        return instance
    }

    fun addAndInvokeMultipleIntEventsToSameEventSource(): Any {
        val instance = ClassWithMarshalingRoutines()
        var z = 0
        val first: EventHandler<Int> = { _, value -> z = value }
        val second: EventHandler<Int> = { _, value -> z = value * 2 }
        val third: EventHandler<Int> = { _, value -> z = value * 3 }
        instance.intPropertyChanged += first
        instance.intPropertyChanged += second
        instance.intPropertyChanged += third
        instance.raiseIntChanged()
        return instance
    }

    fun invokeIntEvent(): Int {
        instance.raiseIntChanged()
        return z2
    }

    fun invokeIntEventWithSenderCheck(): Int {
        instance2.raiseIntChanged()
        return z4
    }

    fun addAndRemoveIntEventOnNewEventSource(): Any {
        val instance = ClassWithMarshalingRoutines()
        var z = 0
        val handler: EventHandler<Int> = { _, value -> z = value }
        instance.intPropertyChanged += handler
        instance.intPropertyChanged -= handler
        return instance
    }

    fun nativeIntEventOverhead(): Any = EventOperations(ManagedEvents())

    fun addNativeIntEventToNewEventSource(): Any =
        EventOperations(ManagedEvents()).also(EventOperations::addIntEvent)

    fun addMultipleNativeEventsToNewEventSource(): Any =
        EventOperations(ManagedEvents()).also {
            it.addIntEvent()
            it.addDoubleEvent()
        }

    fun addAndInvokeNativeIntEventOnNewEventSource(): Any =
        EventOperations(ManagedEvents()).also {
            it.addIntEvent()
            it.fireIntEvent()
        }

    fun invokeNativeIntEvent() {
        operations.fireIntEvent()
    }

    fun addAndRemoveNativeIntEventOnNewEventSource(): Any =
        EventOperations(ManagedEvents()).also {
            it.addIntEvent()
            it.removeIntEvent()
        }
}

internal fun eventScenarios(): List<BenchmarkScenario> =
    listOf(
        objectScenario("IntEventOverhead") { it.intEventOverhead() },
        objectScenario("AddIntEventToNewEventSource") { it.addIntEventToNewEventSource() },
        objectScenario("AddMultipleEventsToNewEventSource") { it.addMultipleEventsToNewEventSource() },
        objectScenario("AddAndInvokeIntEventOnNewEventSource") { it.addAndInvokeIntEventOnNewEventSource() },
        objectScenario("AddAndInvokeMultipleIntEventsToSameEventSource") { it.addAndInvokeMultipleIntEventsToSameEventSource() },
        intScenario("InvokeIntEvent") { it.invokeIntEvent() },
        intScenario("InvokeIntEventWithSenderCheck") { it.invokeIntEventWithSenderCheck() },
        objectScenario("AddAndRemoveIntEventOnNewEventSource") { it.addAndRemoveIntEventOnNewEventSource() },
        objectScenario("NativeIntEventOverhead") { it.nativeIntEventOverhead() },
        objectScenario("AddNativeIntEventToNewEventSource") { it.addNativeIntEventToNewEventSource() },
        objectScenario("AddMultipleNativeEventsToNewEventSource") { it.addMultipleNativeEventsToNewEventSource() },
        objectScenario("AddAndInvokeNativeIntEventOnNewEventSource") { it.addAndInvokeNativeIntEventOnNewEventSource() },
        voidScenario("InvokeNativeIntEvent") { it.invokeNativeIntEvent() },
        objectScenario("AddAndRemoveNativeIntEventOnNewEventSource") { it.addAndRemoveNativeIntEventOnNewEventSource() },
    )

private fun objectScenario(method: String, invoke: (EventPerf) -> Any?): BenchmarkScenario =
    referenceObjectScenario("EventPerf.$method", ::EventPerf, EventPerf::setup, invoke)

private fun intScenario(method: String, invoke: (EventPerf) -> Int): BenchmarkScenario =
    referenceValueScenario("EventPerf.$method", ::EventPerf, EventPerf::setup, invoke, Int::toLong)

private fun voidScenario(method: String, invoke: (EventPerf) -> Unit): BenchmarkScenario =
    referenceVoidScenario("EventPerf.$method", ::EventPerf, EventPerf::setup, invoke)
