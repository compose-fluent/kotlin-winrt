package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ActivationMarshalingMetadataTest {
    @Test
    fun known_classification_skips_probes_and_unknown_preserves_them() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT 3 WindowsRuntimeObjectReference.Initialization trusts class metadata
        // for Agile/Standard and probes IAgileObject/IMarshal only for Unknown.
        for (type in WinRTObjectReferenceMarshalingType.entries) {
            var probes = 0
            WinRTInspectableComObject(
                interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(IID.IInspectable, emptyList())),
                queryInterfaceFallback = { iid ->
                    if (iid == IID.IAgileObject || iid == IID.IMarshal) probes++
                    null
                },
            ).use { instanceHost ->
                factoryFor(instanceHost).use { factoryHost ->
                    ActivationFactoryReference(
                        checkNotNull(factoryHost.tryCreateDetachedReference(IID.IActivationFactory)).asRawComPtr(),
                    ).use { factory ->
                        factory.activateInstance(IID.IInspectable, type).use { instance ->
                            assertEquals(type == WinRTObjectReferenceMarshalingType.Agile, instance.comPtr.support.callsAreFreeThreaded)
                            assertEquals(if (type == WinRTObjectReferenceMarshalingType.Unknown) 2 else 0, probes)
                            assertEquals(2, instanceHost.state.currentReferenceCount())
                        }
                        assertEquals(1, instanceHost.state.currentReferenceCount())
                    }
                }
            }
        }
    }

    @Test
    fun failed_unknown_classification_releases_both_activation_and_query_results() {
        if (!PlatformRuntime.isWindows) return
        // The new hint must not weaken InitializeObjectReferenceUnsafe's ownership:
        // both owned references must be released when GetUnmarshalClass fails.
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(IID.IInspectable, emptyList()),
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IMarshal,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(
                            ComAbiValueKind.Pointer, ComAbiValueKind.Pointer,
                            ComAbiValueKind.Int32, ComAbiValueKind.Pointer,
                            ComAbiValueKind.Int32, ComAbiValueKind.Pointer,
                        ),
                        handler = { _: List<Any?> -> KnownHResults.E_FAIL.value },
                    )),
                ),
            ),
        ).use { instanceHost ->
            factoryFor(instanceHost).use { factoryHost ->
                ActivationFactoryReference(
                    checkNotNull(factoryHost.tryCreateDetachedReference(IID.IActivationFactory)).asRawComPtr(),
                ).use { factory ->
                    val failure = assertFailsWith<WinRTRuntimeException> {
                        factory.activateInstance(IID.IInspectable)
                    }
                    assertEquals(KnownHResults.E_FAIL, failure.hResult)
                    assertEquals(1, instanceHost.state.currentReferenceCount())
                }
            }
        }
    }

    private fun factoryFor(instance: WinRTInspectableComObject): WinRTInspectableComObject =
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    IID.IActivationFactory,
                    listOf(WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
                        PlatformAbi.writePointer(
                            args.single() as RawAddress,
                            checkNotNull(instance.tryCreateDetachedReference(IID.IInspectable)),
                        )
                        KnownHResults.S_OK.value
                    }),
                ),
                WinRTInspectableInterfaceDefinition(
                    IID.IAgileObject, emptyList(), baseKind = WinRTComInterfaceBaseKind.IUnknown,
                ),
            ),
            defaultInterfaceId = IID.IActivationFactory,
        )
}
