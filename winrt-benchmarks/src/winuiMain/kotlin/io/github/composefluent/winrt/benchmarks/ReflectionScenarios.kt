package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.BlittableStruct
import benchmarkcomponent.ClassWithMarshalingRoutines
import benchmarkcomponent.NonBlittable
import benchmarkcomponent.ProvideInt
import benchmarkcomponent.WrappedClass
import io.github.composefluent.winrt.runtime.WeakReference
import windows.foundation.EventHandler
import windows.foundation.Uri
import kotlin.time.Duration.Companion.microseconds

private class ReflectionPerf {
    private lateinit var instance: ClassWithMarshalingRoutines
    private lateinit var instanceDictionary: MutableMap<String, WrappedClass>
    private lateinit var managedObject: ManagedObjectWithInterfaces

    fun setup() {
        instance = ClassWithMarshalingRoutines()
        instanceDictionary = instance.existingDictionary
        managedObject = ManagedObjectWithInterfaces()
    }

    fun executeMarshalingForNewKeyValuePair(): Any? = instance.newTypeErasedKeyValuePairObject

    fun executeMarshalingForNewArray(): Any? = instance.newTypeErasedArrayObject

    fun executeMarshalingForNewNullable(): Any? = instance.newTypeErasedNullableObject

    fun executeMarshalingForExistingKeyvaluePair(): Any? = instance.existingTypeErasedKeyValuePairObject

    fun executeMarshalingForExistingArray(): Any? = instance.existingTypeErasedArrayObject

    fun executeMarshalingForExistingNullable(): Any? = instance.existingTypeErasedNullableObject

    fun executeMarshalingForString(): Any = instance.defaultStringProperty

    fun executeMarshalingForCustomObject(): Any? = instance.newWrappedClassObject

    fun executeMarshalingForDelegate(): Int {
        val y = 1
        instance.callForInt(ProvideInt { y })
        return y
    }

    fun intEventSource() {
        val instance = ClassWithMarshalingRoutines()
        val x = 0
        val y = 1
        var z = 0
        instance.intProperty = x
        instance.callForInt(ProvideInt { y })
        val handler: EventHandler<Int> = { _, value -> z = value }
        instance.intPropertyChanged += handler
        instance.raiseIntChanged()
    }

    fun existingDictionaryLookup(): Int {
        val dictionary = instance.existingDictionary
        var count = 0
        repeat(100) {
            if (dictionary["a"] != null) count++
        }
        return count
    }

    fun existingDictionaryLookupCached(): Any? {
        val dictionary = instance.existingDictionary
        var cache: WrappedClass? = null
        repeat(1_000) { cache = dictionary["a"] }
        return cache
    }

    fun existingDictionaryLookup2(): Any? = instanceDictionary["a"]

    fun existingDictionaryLookup3(): Any? = instance.existingDictionary["a"]

    fun getNullableInt(): Int = requireNotNull(instance.nullableInt)

    fun setNullableInt() {
        instance.nullableInt = 4
    }

    fun getNullableBittableStruct(): Any = requireNotNull(instance.nullableBlittableStruct)

    fun setNullableBittableStruct() {
        instance.nullableBlittableStruct = BlittableStruct(2)
    }

    fun getNullableTimeSpan(): Any = requireNotNull(instance.nullableTimeSpan)

    fun setNullableTimeSpan() {
        instance.nullableTimeSpan = 10.microseconds
    }

    fun getNullableNonBittableStruct(): Any = requireNotNull(instance.nullableNonBlittableStruct)

    fun setNullableNonBittableStruct() {
        instance.nullableNonBlittableStruct = NonBlittable(true, "beta")
    }

    fun setNullableDelegate() {
        var z = 0
        val handler: EventHandler<Int> = { _, value -> z = value }
        instance.newTypeErasedNullableObject = handler
    }

    fun setNullableIntDelegate() {
        instance.boxedDelegate = ProvideInt { 4 }
    }

    fun getNullableIntDelegate(): Any? = instance.boxedDelegate as? ProvideInt

    fun getNewIntDelegate(): Any? = instance.newIntDelegate

    fun getExistingIntDelegate(): Any? = instance.existingIntDelegate

    fun createAndIterateList(): String {
        val list = instance.newList()
        list.add("How")
        list.add("Are")
        list.add("You")
        var sentence = ""
        for (index in list.indices) sentence += list[index]
        return sentence
    }

    fun getUri(): Any? = instance.newUri

    fun setUri() {
        instance.newUri = Uri("https://github.com")
    }

    fun getExistingUri(): Any? = instance.existingUri

    fun getWinRTType(): Any? = instance.newType

    fun setWinRTType() {
        instance.newType = ClassWithMarshalingRoutines::class
    }

    fun setPrimitiveType() {
        instance.newType = Int::class
    }

    fun setNonWinRTType() {
        instance.newType = ReflectionPerf::class
    }

    fun getExistingWinRTType(): Any? = instance.existingType

    fun getWeakReferenceOfManagedObject() {
        instance.getWeakReference(managedObject)
    }

    fun getAndResolveWeakReferenceOfManagedObject(): Any? =
        instance.getAndResolveWeakReference(managedObject)

    fun getWeakReferenceOfNativeObject(): Any = WeakReference(instance)
}

internal fun reflectionScenarios(): List<BenchmarkScenario> =
    listOf(
        objectScenario("ExecuteMarshalingForNewKeyValuePair") { it.executeMarshalingForNewKeyValuePair() },
        objectScenario("ExecuteMarshalingForNewArray") { it.executeMarshalingForNewArray() },
        objectScenario("ExecuteMarshalingForNewNullable") { it.executeMarshalingForNewNullable() },
        objectScenario("ExecuteMarshalingForExistingKeyvaluePair") { it.executeMarshalingForExistingKeyvaluePair() },
        objectScenario("ExecuteMarshalingForExistingArray") { it.executeMarshalingForExistingArray() },
        objectScenario("ExecuteMarshalingForExistingNullable") { it.executeMarshalingForExistingNullable() },
        objectScenario("ExecuteMarshalingForString") { it.executeMarshalingForString() },
        objectScenario("ExecuteMarshalingForCustomObject") { it.executeMarshalingForCustomObject() },
        intScenario("ExecuteMarshalingForDelegate") { it.executeMarshalingForDelegate() },
        voidScenario("IntEventSource") { it.intEventSource() },
        intScenario("ExistingDictionaryLookup") { it.existingDictionaryLookup() },
        objectScenario("ExistingDictionaryLookupCached") { it.existingDictionaryLookupCached() },
        objectScenario("ExistingDictionaryLookup2") { it.existingDictionaryLookup2() },
        objectScenario("ExistingDictionaryLookup3") { it.existingDictionaryLookup3() },
        intScenario("GetNullableInt") { it.getNullableInt() },
        voidScenario("SetNullableInt") { it.setNullableInt() },
        objectScenario("GetNullableBittableStruct") { it.getNullableBittableStruct() },
        voidScenario("SetNullableBittableStruct") { it.setNullableBittableStruct() },
        objectScenario("GetNullableTimeSpan") { it.getNullableTimeSpan() },
        voidScenario("SetNullableTimeSpan") { it.setNullableTimeSpan() },
        objectScenario("GetNullableNonBittableStruct") { it.getNullableNonBittableStruct() },
        voidScenario("SetNullableNonBittableStruct") { it.setNullableNonBittableStruct() },
        voidScenario("SetNullableDelegate") { it.setNullableDelegate() },
        voidScenario("SetNullableIntDelegate") { it.setNullableIntDelegate() },
        objectScenario("GetNullableIntDelegate") { it.getNullableIntDelegate() },
        objectScenario("GetNewIntDelegate") { it.getNewIntDelegate() },
        objectScenario("GetExistingIntDelegate") { it.getExistingIntDelegate() },
        stringScenario("CreateAndIterateList") { it.createAndIterateList() },
        objectScenario("GetUri") { it.getUri() },
        voidScenario("SetUri") { it.setUri() },
        objectScenario("GetExistingUri") { it.getExistingUri() },
        objectScenario("GetWinRTType") { it.getWinRTType() },
        voidScenario("SetWinRTType") { it.setWinRTType() },
        voidScenario("SetPrimitiveType") { it.setPrimitiveType() },
        voidScenario("SetNonWinRTType") { it.setNonWinRTType() },
        objectScenario("GetExistingWinRTType") { it.getExistingWinRTType() },
        voidScenario("GetWeakReferenceOfManagedObject") { it.getWeakReferenceOfManagedObject() },
        objectScenario("GetAndResolveWeakReferenceOfManagedObject") { it.getAndResolveWeakReferenceOfManagedObject() },
        objectScenario("GetWeakReferenceOfNativeObject") { it.getWeakReferenceOfNativeObject() },
    )

private fun objectScenario(method: String, invoke: (ReflectionPerf) -> Any?): BenchmarkScenario =
    referenceObjectScenario("ReflectionPerf.$method", ::ReflectionPerf, ReflectionPerf::setup, invoke)

private fun intScenario(method: String, invoke: (ReflectionPerf) -> Int): BenchmarkScenario =
    referenceValueScenario("ReflectionPerf.$method", ::ReflectionPerf, ReflectionPerf::setup, invoke, Int::toLong)

private fun stringScenario(method: String, invoke: (ReflectionPerf) -> String): BenchmarkScenario =
    referenceValueScenario(
        name = "ReflectionPerf.$method",
        create = ::ReflectionPerf,
        setup = ReflectionPerf::setup,
        invoke = invoke,
        checksum = { value -> value.length.toLong() },
    )

private fun voidScenario(method: String, invoke: (ReflectionPerf) -> Unit): BenchmarkScenario =
    referenceVoidScenario("ReflectionPerf.$method", ::ReflectionPerf, ReflectionPerf::setup, invoke)
