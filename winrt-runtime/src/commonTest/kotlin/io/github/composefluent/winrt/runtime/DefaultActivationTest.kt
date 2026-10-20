package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultActivationTest {
    @Test
    fun typed_activation_queries_the_default_vtable_and_releases_the_untyped_result() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT code_writers.h casts objRef to the default IID. The Runtime2 fast-attach
        // assumption cannot be used for our ordinary interface slots when WinUI activation
        // returns a different IInspectable vtable (observed with TextBlock).
        val defaultIid = Guid("57f982a7-6d58-4e7b-a379-e7a54caf764e")
        PlatformAbi.confinedScope().use { scope ->
            val pointer = PlatformAbi.allocatePointerSlot(scope)
            val defaultPointer = PlatformAbi.allocatePointerSlot(scope)
            val vtable = PlatformAbi.allocatePointerArray(scope, 7)
            val defaultVtable = PlatformAbi.allocatePointerArray(scope, 7)
            PlatformAbi.writePointer(pointer, vtable)
            PlatformAbi.writePointer(defaultPointer, defaultVtable)
            var references = 1
            var defaultQueries = 0
            val query = ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.QueryInterface) { _, iidWord, outWord, _, _, _, _ ->
                val iid = PlatformAbi.readGuid(RawAddress(iidWord))
                if (iid == defaultIid) defaultQueries++
                if (iid == defaultIid || iid == IID.IInspectable || iid == IID.IUnknown || iid == IID.IAgileObject) {
                    references++
                    PlatformAbi.writePointer(RawAddress(outWord), if (iid == defaultIid) defaultPointer else pointer)
                    KnownHResults.S_OK.value
                } else {
                    PlatformAbi.writePointer(RawAddress(outWord), RawAddress.Null)
                    KnownHResults.E_NOINTERFACE.value
                }
            }
            val addRef = ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.AddRef) { _, _, _, _, _, _, _ -> ++references }
            val release = ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.Release) { _, _, _, _, _, _, _ -> --references }
            val getter = ComAbiInteropBridge.createRawWordComMethodCallback(ComMethodSignatures.HResult_Ptr) { _, outWord, _, _, _, _, _ ->
                PlatformAbi.writeInt32(RawAddress(outWord), 42)
                KnownHResults.S_OK.value
            }
            try {
                PlatformAbi.writePointerAt(vtable, 0, query.pointer)
                PlatformAbi.writePointerAt(vtable, 1, addRef.pointer)
                PlatformAbi.writePointerAt(vtable, 2, release.pointer)
                PlatformAbi.writePointerAt(defaultVtable, 0, query.pointer)
                PlatformAbi.writePointerAt(defaultVtable, 1, addRef.pointer)
                PlatformAbi.writePointerAt(defaultVtable, 2, release.pointer)
                PlatformAbi.writePointerAt(defaultVtable, 6, getter.pointer)
                WinRTInspectableComObject(
                    interfaceDefinitions = listOf(
                        WinRTInspectableInterfaceDefinition(
                            IID.IActivationFactory,
                            listOf(WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
                                references++
                                PlatformAbi.writePointer(args.single() as RawAddress, pointer)
                                KnownHResults.S_OK.value
                            }),
                        ),
                        WinRTInspectableInterfaceDefinition(IID.IAgileObject, emptyList(), baseKind = WinRTComInterfaceBaseKind.IUnknown),
                    ),
                    defaultInterfaceId = IID.IActivationFactory,
                ).use { host ->
                    ActivationFactoryReference(checkNotNull(host.tryCreateDetachedReference(IID.IActivationFactory)).asRawComPtr()).use { factory ->
                        for (direct in listOf(true, false)) {
                            val instance = if (direct) factory.activateDefaultInstance(defaultIid, WinRTObjectReferenceMarshalingType.Agile)
                            else factory.activateInstance(defaultIid, WinRTObjectReferenceMarshalingType.Agile)
                            instance.use {
                                assertEquals(defaultIid, it.interfaceId)
                                assertEquals(if (direct) 1 else 2, defaultQueries)
                                assertEquals(defaultPointer.asRawComPtr(), it.pointer)
                                assertEquals(2, references)
                                acquireNativeScalarScratchFrame().use { result ->
                                    assertEquals(KnownHResults.S_OK.value, ComVtableInvoker.invokeArgs(it.pointer, 6, result))
                                    assertEquals(42, result.readInt32())
                                }
                            }
                            assertEquals(1, references)
                        }
                    }
                }
            } finally {
                WinRTPlatformApi.releaseRaw(pointer)
                listOf(getter, release, addRef, query).forEach { it.close() }
            }
            assertEquals(0, references)
        }
    }
}
