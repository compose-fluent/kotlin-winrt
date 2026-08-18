package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.ClassWithMarshalingRoutines
import benchmarkcomponent.EventOperations
import benchmarkcomponent.IIntProperties
import benchmarkcomponent.NonBlittable
import benchmarkcomponent.ProvideInt
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.GuidGenerator
import io.github.composefluent.winrt.runtime.WinRTCollectionInterfaceIds
import windows.foundation.AsyncActionCompletedHandler
import windows.foundation.AsyncStatus
import windows.storage.FileAttributes

private class GuidPerf {
    fun getClassGuid(): Guid = GuidGenerator.getIID(ClassWithMarshalingRoutines::class)

    fun getDelegateGuid(): Guid = GuidGenerator.getIID(AsyncActionCompletedHandler::class)

    fun createListGuid(): Guid =
        GuidGenerator.createIID(
            WinRTCollectionInterfaceIds.iVector,
            ClassWithMarshalingRoutines::class,
        )

    fun createDictionaryWithStringKeyGuid(): Guid =
        GuidGenerator.createIID(
            WinRTCollectionInterfaceIds.iMap,
            String::class,
            ClassWithMarshalingRoutines::class,
        )

    fun createDictionaryWithBoolKeyGuid(): Guid =
        GuidGenerator.createIID(
            WinRTCollectionInterfaceIds.iMap,
            Boolean::class,
            ClassWithMarshalingRoutines::class,
        )

    fun createReadOnlyEnumListGuid(): Guid =
        GuidGenerator.createIID(WinRTCollectionInterfaceIds.iVectorView, AsyncStatus::class)

    fun createReadOnlyFlagEnumListGuid(): Guid =
        GuidGenerator.createIID(WinRTCollectionInterfaceIds.iVectorView, FileAttributes::class)

    fun createReadOnlyStructListGuid(): Guid =
        GuidGenerator.createIID(WinRTCollectionInterfaceIds.iVectorView, NonBlittable::class)

    fun createReadOnlyInterfaceListGuid(): Guid =
        GuidGenerator.createIID(WinRTCollectionInterfaceIds.iVectorView, IIntProperties::class)

    fun createReadOnlyClassListGuid(): Guid =
        GuidGenerator.createIID(WinRTCollectionInterfaceIds.iVectorView, EventOperations::class)

    fun createReadOnlyDelegateListGuid(): Guid =
        GuidGenerator.createIID(WinRTCollectionInterfaceIds.iVectorView, ProvideInt::class)
}

internal fun guidScenarios(): List<BenchmarkScenario> =
    listOf(
        scenario("GetClassGuid") { it.getClassGuid() },
        scenario("GetDelegateGuid") { it.getDelegateGuid() },
        scenario("CreateListGuid") { it.createListGuid() },
        scenario("CreateDictionaryWithStringKeyGuid") { it.createDictionaryWithStringKeyGuid() },
        scenario("CreateDictionaryWithBoolKeyGuid") { it.createDictionaryWithBoolKeyGuid() },
        scenario("CreateReadOnlyEnumListGuid") { it.createReadOnlyEnumListGuid() },
        scenario("CreateReadOnlyFlagEnumListGuid") { it.createReadOnlyFlagEnumListGuid() },
        scenario("CreateReadOnlyStructListGuid") { it.createReadOnlyStructListGuid() },
        scenario("CreateReadOnlyInterfaceListGuid") { it.createReadOnlyInterfaceListGuid() },
        scenario("CreateReadOnlyClassListGuid") { it.createReadOnlyClassListGuid() },
        scenario("CreateReadOnlyDelegateListGuid") { it.createReadOnlyDelegateListGuid() },
    )

private fun scenario(method: String, invoke: (GuidPerf) -> Guid): BenchmarkScenario =
    referenceObjectScenario("GuidPerf.$method", ::GuidPerf, invoke = invoke)
