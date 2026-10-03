package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** ABI prerequisite only. The actual WinUI IID and LoadComponent integration are tested by Gallery. */
class XamlConnectorIdentityTest {
    @Test
    fun connector_shares_composed_outer_identity_and_receives_borrowed_target() {
        // CsWinRT ComWrappersSupport.GetInterfaceTableEntries and code_writers.h
        // write_composable_constructors: generated interfaces belong to the authored outer.
        // XamlCompiler CSharpPagePass2.tt: Connect(int, object), GetBindingConnector(int, object).
        ComWrappersSupport.clearRegistriesForTests()
        val overrideId = Guid("82297301-90b2-48da-9ac4-3b4d147aeda1")
        val connectorId = Guid("82297301-90b2-48da-9ac4-3b4d147aeda2")
        val nativeId = Guid("82297301-90b2-48da-9ac4-3b4d147aeda3")
        val connector = WinRTInspectableInterfaceDefinition(connectorId, listOf(
            WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Int32_Ptr) { managed, arguments ->
                val page = managed as Page
                page.connectionId = arguments[0] as Int
                page.element = WinRTObjectMarshaller.fromAbi(arguments[1] as RawAddress)
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Int32_Ptr_Ptr) { _, arguments ->
                // No template binding scope: returned connector is null, with no reference transferred.
                PlatformAbi.writePointer(arguments[2] as RawAddress, RawAddress.Null)
                KnownHResults.S_OK.value
            },
        ))
        ComWrappersSupport.registerStaticCcwDefinition(Page::class, WinRTCcwDefinition(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(overrideId, emptyList()), connector),
            defaultInterfaceId = overrideId,
            runtimeClassName = "test.XamlPage",
        ))
        val target = Any()
        val targetHost = WinRTInspectableComObject.inspectableBox(target, "test.Element")
        val targetReference = targetHost.createReference(IID.IInspectable)
        val pages = listOf(Page(), Page())
        try {
            for ((index, page) in pages.withIndex()) {
                val native = WinRTInspectableComObject(
                    interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(nativeId, emptyList())),
                    defaultInterfaceId = nativeId,
                )
                var factoryInstance = RawAddress.Null
                try {
                    ComWrappersSupport.createComposableCCWForObject(page, nativeId) { outer, innerOut, instanceOut ->
                        // The extra interface is available before the native composable factory returns.
                        IInspectableReference(outer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).use { base ->
                            base.queryInterface(connectorId).getOrThrow().use { assertTrue(it.sameIdentity(base)) }
                        }
                        // Each factory output needs its own reference. detachReference
                        // transfers the host baseline and cannot be used twice here.
                        PlatformAbi.writePointer(innerOut, native.acquireReference(IID.IInspectable))
                        factoryInstance = native.acquireReference(nativeId)
                        PlatformAbi.writePointer(instanceOut, factoryInstance)
                        KnownHResults.S_OK.value
                    }.use { composed ->
                        page.reference = composed
                        composed.outer.queryInterface(connectorId).getOrThrow().use { connection ->
                            assertTrue(connection.sameIdentity(composed.outer))
                            assertSame(page, ComWrappersSupport.findObject(connection.pointer.asRawAddress(), Page::class))
                            val targetCount = WinRTInspectableComObject.tryProbeReferenceCount(targetReference.pointer.asRawAddress())
                            HResult(ComVtableInvoker.invokeArgs(connection.pointer, 6, index + 1, targetReference.pointer.asRawAddress())).requireSuccess()
                            assertEquals(targetCount, WinRTInspectableComObject.tryProbeReferenceCount(targetReference.pointer.asRawAddress()))
                            assertSame(target, page.element)
                            assertEquals(index + 1, page.connectionId)
                            PlatformAbi.confinedScope().use { scope ->
                                val result = PlatformAbi.allocatePointerSlot(scope)
                                PlatformAbi.writePointer(result, targetReference.pointer.asRawAddress())
                                HResult(ComVtableInvoker.invokeArgs(connection.pointer, 7, 1, targetReference.pointer, result)).requireSuccess()
                                assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                            }
                        }
                    }
                } finally {
                    // This synthetic instance has a separate IUnknown, rather than
                    // WinUI's delegating outer identity and native aggregation lifetime.
                    if (!PlatformAbi.isNull(factoryInstance)) WinRTPlatformApi.releaseRaw(factoryInstance)
                    native.close()
                }
            }
            assertEquals(1, pages[0].connectionId)
            assertEquals(2, pages[1].connectionId)
        } finally {
            targetReference.close()
            targetHost.close()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    private class Page : WinRTComposableObject {
        var reference: WinRTComposableObjectReference? = null
        override val winRTComposableObjectReference get() = reference
        var element: Any? = null
        var connectionId: Int = 0
    }
}
