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
                        factory.activateDefaultInstance(IID.IInspectable, type).use { instance ->
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
    fun composable_native_instances_use_metadata_and_release_the_inner_result() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT 3 WindowsRuntimeObject's DerivedComposed activation carries
        // get_marshaling_type_name through the non-aggregated initialization path.
        for (type in WinRTObjectReferenceMarshalingType.entries) {
            var probes = 0
            WinRTInspectableComObject(
                interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(IID.IInspectable, emptyList())),
                queryInterfaceFallback = { iid ->
                    if (iid == IID.IAgileObject || iid == IID.IMarshal) probes++
                    null
                },
            ).use { instanceHost ->
                WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(IID.IInspectable, emptyList()))).use { innerHost ->
                    val result = WinRTComposableFactoryResult(
                        instance = checkNotNull(instanceHost.tryCreateDetachedReference(IID.IInspectable)).asRawComPtr(),
                        inner = checkNotNull(innerHost.tryCreateDetachedReference(IID.IInspectable)).asRawComPtr(),
                    )
                    ComWrappersSupport.attachComposableFactoryResult(result, IID.IInspectable, type).use { instance ->
                        assertEquals(type == WinRTObjectReferenceMarshalingType.Agile, instance.comPtr.support.callsAreFreeThreaded)
                        assertEquals(if (type == WinRTObjectReferenceMarshalingType.Unknown) 2 else 0, probes)
                        assertEquals(2, instanceHost.state.currentReferenceCount())
                        assertEquals(1, innerHost.state.currentReferenceCount())
                    }
                    assertEquals(1, instanceHost.state.currentReferenceCount())
                }
            }
        }
    }

    @Test
    fun failed_unknown_classification_releases_owned_activation_results() {
        if (!PlatformRuntime.isWindows) return
        // Both activation routes must preserve CsWinRT 3 initialization's ownership
        // when GetUnmarshalClass fails, including directly attached default interfaces.
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
                    for (defaultInterface in listOf(false, true)) {
                        val failure = assertFailsWith<WinRTRuntimeException> {
                            if (defaultInterface) factory.activateDefaultInstance(IID.IInspectable)
                            else factory.activateInstance(IID.IInspectable)
                        }
                        assertEquals(KnownHResults.E_FAIL, failure.hResult)
                        assertEquals(1, instanceHost.state.currentReferenceCount())
                    }
                }
            }
            WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(IID.IInspectable, emptyList()))).use { innerHost ->
                val result = WinRTComposableFactoryResult(
                    instance = checkNotNull(instanceHost.tryCreateDetachedReference(IID.IInspectable)).asRawComPtr(),
                    inner = checkNotNull(innerHost.tryCreateDetachedReference(IID.IInspectable)).asRawComPtr(),
                )
                val failure = assertFailsWith<WinRTRuntimeException> {
                    ComWrappersSupport.attachComposableFactoryResult(result, IID.IInspectable)
                }
                assertEquals(KnownHResults.E_FAIL, failure.hResult)
                assertEquals(1, instanceHost.state.currentReferenceCount())
                assertEquals(1, innerHost.state.currentReferenceCount())
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
