package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.ClassWithFastAbi
import benchmarkcomponent.ClassWithFastAbiDerived
import benchmarkcomponent.ClassWithMarshalingRoutines
import benchmarkcomponent.ClassWithMultipleInterfaces
import benchmarkcomponent.Composable
import benchmarkcomponent.IBoolProperties
import benchmarkcomponent.IIntProperties
import windows.applicationmodel.chat.ChatMessage
import windows.system.power.PowerManager

internal class ManagedObjectWithInterfaces : IIntProperties, IBoolProperties {
    override var intProperty: Int = 0
    override var boolProperty: Boolean = false
}

internal class ManagedComposableObjectWithInterfaces : Composable(), IIntProperties {
    override var intProperty: Int = 0
}

private class QueryInterfacePerf {
    private lateinit var instance: ClassWithMultipleInterfaces
    private lateinit var message: ChatMessage
    private lateinit var managedObject: ManagedObjectWithInterfaces
    private lateinit var composableObject: ManagedComposableObjectWithInterfaces
    private lateinit var fastAbiInstance: ClassWithFastAbi
    private lateinit var fastAbiDerivedInstance: ClassWithFastAbiDerived

    fun setup() {
        instance = ClassWithMultipleInterfaces()
        message = ChatMessage()
        managedObject = ManagedObjectWithInterfaces()
        composableObject = ManagedComposableObjectWithInterfaces()
        fastAbiInstance = ClassWithFastAbi()
        fastAbiDerivedInstance = ClassWithFastAbiDerived()
    }

    fun queryDefaultInterface(): Int = instance.defaultIntProperty

    fun queryNonDefaultInterface(): Int = instance.intProperty

    fun queryFastAbiDefaultInterface(): Int = fastAbiInstance.defaultIntProperty

    fun queryFastAbiNonDefaultInterface(): Int = fastAbiInstance.nonDefaultIntProperty

    fun queryFastAbiDerivedDefaultInterface(): Int = fastAbiDerivedInstance.derivedDefaultIntProperty

    fun queryFastAbiComposedNonDefaultInterface(): Int = fastAbiDerivedInstance.derivedNonDefaultIntProperty

    fun queryFastAbiComposedBaseDefaultInterface(): Int = fastAbiDerivedInstance.defaultIntProperty

    fun queryFastAbiComposedBaseNonDefaultInterface(): Int = fastAbiDerivedInstance.nonDefaultIntProperty

    fun queryNonDefaultInterface2(): Boolean = instance.boolProperty

    fun queryDefaultInterfaceSetProperty() {
        instance.defaultIntProperty = 4
    }

    fun queryNonDefaultInterfaceSetProperty() {
        instance.intProperty = 4
    }

    fun querySDKDefaultInterface(): Boolean = message.isForwardingDisabled

    fun querySDKNonDefaultInterface(): Boolean = message.isSeen

    fun defaultObjectParameters(): Any? {
        instance.defaultObjectProperty = ClassWithMultipleInterfaces()
        return instance.defaultObjectProperty
    }

    fun defaultStringParameters(): Any {
        instance.defaultStringProperty = "Hello"
        return instance.defaultStringProperty
    }

    fun dynamicCast(): Any = instance.newObject() as ClassWithMarshalingRoutines

    fun constructAndQueryDefaultInterfaceFirstCall(): Int =
        ClassWithMultipleInterfaces().defaultIntProperty

    fun constructAndQueryNonDefaultInterfaceFirstCall(): Int =
        ClassWithMultipleInterfaces().intProperty

    fun constructAndQueryFastAbiDefaultInterfaceFirstCall(): Int =
        ClassWithFastAbi().defaultIntProperty

    fun constructAndQueryFastAbiNonDefaultInterfaceFirstCall(): Int =
        ClassWithFastAbi().nonDefaultIntProperty

    fun constructAndQueryFastAbiDerivedDefaultInterfaceFirstCall(): Int =
        ClassWithFastAbiDerived().derivedDefaultIntProperty

    fun constructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall(): Int =
        ClassWithFastAbiDerived().derivedNonDefaultIntProperty

    fun constructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall(): Int =
        ClassWithFastAbiDerived().defaultIntProperty

    fun constructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall(): Int =
        ClassWithFastAbiDerived().nonDefaultIntProperty

    fun staticPropertyCall(): Int = PowerManager.remainingChargePercent

    fun queryInterfaceOnManagedObject() {
        instance.queryBoolInterface(managedObject)
    }

    fun queryNativeInterfaceOnComposedObject() {
        instance.queryBoolInterface(composableObject)
    }
}

internal fun queryInterfaceScenarios(): List<BenchmarkScenario> =
    listOf(
        intScenario("QueryDefaultInterface") { it.queryDefaultInterface() },
        intScenario("QueryNonDefaultInterface") { it.queryNonDefaultInterface() },
        intScenario("QueryFastAbiDefaultInterface") { it.queryFastAbiDefaultInterface() },
        intScenario("QueryFastAbiNonDefaultInterface") { it.queryFastAbiNonDefaultInterface() },
        intScenario("QueryFastAbiDerivedDefaultInterface") { it.queryFastAbiDerivedDefaultInterface() },
        intScenario("QueryFastAbiComposedNonDefaultInterface") { it.queryFastAbiComposedNonDefaultInterface() },
        intScenario("QueryFastAbiComposedBaseDefaultInterface") { it.queryFastAbiComposedBaseDefaultInterface() },
        intScenario("QueryFastAbiComposedBaseNonDefaultInterface") { it.queryFastAbiComposedBaseNonDefaultInterface() },
        boolScenario("QueryNonDefaultInterface2") { it.queryNonDefaultInterface2() },
        voidScenario("QueryDefaultInterfaceSetProperty") { it.queryDefaultInterfaceSetProperty() },
        voidScenario("QueryNonDefaultInterfaceSetProperty") { it.queryNonDefaultInterfaceSetProperty() },
        boolScenario("QuerySDKDefaultInterface") { it.querySDKDefaultInterface() },
        boolScenario("QuerySDKNonDefaultInterface") { it.querySDKNonDefaultInterface() },
        objectScenario("DefaultObjectParameters") { it.defaultObjectParameters() },
        objectScenario("DefaultStringParameters") { it.defaultStringParameters() },
        objectScenario("DynamicCast") { it.dynamicCast() },
        intScenario("ConstructAndQueryDefaultInterfaceFirstCall") { it.constructAndQueryDefaultInterfaceFirstCall() },
        intScenario("ConstructAndQueryNonDefaultInterfaceFirstCall") { it.constructAndQueryNonDefaultInterfaceFirstCall() },
        intScenario("ConstructAndQueryFastAbiDefaultInterfaceFirstCall") { it.constructAndQueryFastAbiDefaultInterfaceFirstCall() },
        intScenario("ConstructAndQueryFastAbiNonDefaultInterfaceFirstCall") { it.constructAndQueryFastAbiNonDefaultInterfaceFirstCall() },
        intScenario("ConstructAndQueryFastAbiDerivedDefaultInterfaceFirstCall") { it.constructAndQueryFastAbiDerivedDefaultInterfaceFirstCall() },
        intScenario("ConstructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall") { it.constructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall() },
        intScenario("ConstructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall") { it.constructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall() },
        intScenario("ConstructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall") { it.constructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall() },
        intScenario("StaticPropertyCall") { it.staticPropertyCall() },
        voidScenario("QueryInterfaceOnManagedObject") { it.queryInterfaceOnManagedObject() },
        voidScenario("QueryNativeInterfaceOnComposedObject") { it.queryNativeInterfaceOnComposedObject() },
    )

private fun intScenario(
    method: String,
    invoke: (QueryInterfacePerf) -> Int,
): BenchmarkScenario =
    referenceValueScenario(
        name = "QueryInterfacePerf.$method",
        create = ::QueryInterfacePerf,
        setup = QueryInterfacePerf::setup,
        invoke = invoke,
        checksum = Int::toLong,
    )

private fun boolScenario(
    method: String,
    invoke: (QueryInterfacePerf) -> Boolean,
): BenchmarkScenario =
    referenceValueScenario(
        name = "QueryInterfacePerf.$method",
        create = ::QueryInterfacePerf,
        setup = QueryInterfacePerf::setup,
        invoke = invoke,
        checksum = { value -> if (value) 1L else 0L },
    )

private fun objectScenario(
    method: String,
    invoke: (QueryInterfacePerf) -> Any?,
): BenchmarkScenario =
    referenceObjectScenario(
        name = "QueryInterfacePerf.$method",
        create = ::QueryInterfacePerf,
        setup = QueryInterfacePerf::setup,
        invoke = invoke,
    )

private fun voidScenario(
    method: String,
    invoke: (QueryInterfacePerf) -> Unit,
): BenchmarkScenario =
    referenceVoidScenario(
        name = "QueryInterfacePerf.$method",
        create = ::QueryInterfacePerf,
        setup = QueryInterfacePerf::setup,
        invoke = invoke,
    )
