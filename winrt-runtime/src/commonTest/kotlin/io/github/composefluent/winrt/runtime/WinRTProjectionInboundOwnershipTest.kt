package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@WinRTProjectedInterface
private interface InboundOwnershipProjection : WinRTManagedProjectionStateAccess {
    companion object Metadata {
        val TYPE_HANDLE: WinRTTypeHandle = WinRTTypeHandle(
            INBOUND_OWNERSHIP_PROJECTION_TYPE_NAME,
            INBOUND_OWNERSHIP_PROJECTION_IID,
        )

        fun wrap(reference: IUnknownReference): InboundOwnershipProjection =
            InboundOwnershipProjectionImpl(reference)
    }
}

private class InboundOwnershipProjectionImpl(
    override val nativeObject: ComObjectReference,
) : InboundOwnershipProjection,
    IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = InboundOwnershipProjection.Metadata.TYPE_HANDLE
}

private class InboundOwnershipTarget(
    private val result: InboundOwnershipProjection,
) {
    var received: InboundOwnershipProjection? = null

    fun consume(value: InboundOwnershipProjection) {
        received = value
    }

    fun produce(): InboundOwnershipProjection = result
}

private class BorrowedObjectTarget {
    var received: Any? = null

    fun consume(value: Any?) {
        received = value
    }
}

@WinRTProjectionAbiType(
    name = "System.Object",
    kind = WinRTProjectionAbiTypeKind.PROJECTION,
    carrier = WinRTProjectionAbiCarrier.ADDRESS,
    reference = WinRTProjectionAbiReferenceKind.INSPECTABLE,
)
internal object BorrowedObjectCodec {
    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.CREATE_MARSHALER,
        type = "System.Object",
    )
    fun createMarshaler(value: Any?): WinRTObjectMarshaler =
        WinRTObjectMarshaller.createMarshaler(value)

    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.FROM_ABI,
        type = "System.Object",
    )
    fun fromAbi(address: RawAddress): Any? = WinRTObjectMarshaller.fromAbi(address)
}

@WinRTProjectionInboundCallSite
private fun consumeInboundOwnershipProjection(
    target: InboundOwnershipTarget,
    @WinRTProjectionParameter(abiType = INBOUND_OWNERSHIP_PROJECTION_TYPE_NAME)
    value: InboundOwnershipProjection,
): Unit = target.consume(value).also {
    TODO("Lowered while compiling the shared inbound ownership test")
}

@WinRTProjectionInboundCallSite(returnAbiType = INBOUND_OWNERSHIP_PROJECTION_TYPE_NAME)
private fun produceInboundOwnershipProjection(
    target: InboundOwnershipTarget,
): InboundOwnershipProjection = target.produce().also {
    TODO("Lowered while compiling the shared inbound ownership test")
}

@WinRTProjectionInboundCallSite
private fun consumeBorrowedObject(
    target: BorrowedObjectTarget,
    @WinRTProjectionParameter(abiType = "System.Object")
    value: Any?,
): Unit = target.consume(value).also {
    TODO("Lowered while compiling the shared inbound ownership test")
}

class WinRTProjectionInboundOwnershipTest {
    @Test
    fun constant_signature_borrowed_reference_preserves_iid_and_owned_lifetime() {
        val expectedInterfaceId = ParameterizedInterfaceId.createFromSignature(
            "pinterface({c50898f6-c536-5f47-8583-8b2c2438a13b};i4)",
        )
        WinRTInspectableComObject.inspectableBox(Any()).use { host ->
            host.createPrimaryReference().use { owner ->
                val pointer = owner.pointer.asRawAddress()
                val before = checkNotNull(WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                val acquired = requireNotNull(
                    acquireBorrowedInterfaceReference(
                        pointer,
                        ParameterizedInterfaceId.createFromSignature(
                            "pinterface({c50898f6-c536-5f47-8583-8b2c2438a13b};i4)",
                        ),
                    ),
                )

                acquired.use { reference ->
                    assertEquals(expectedInterfaceId, reference.interfaceId)
                    assertEquals(
                        before + 1u,
                        checkNotNull(WinRTInspectableComObject.tryProbeReferenceCount(pointer)),
                    )
                }
                assertEquals(before, checkNotNull(WinRTInspectableComObject.tryProbeReferenceCount(pointer)))
            }
        }
    }

    @Test
    fun registered_external_alias_preserves_managed_identity() {
        val managedValue = Any()
        PlatformAbi.confinedScope().use { scope ->
            val externalVtable = PlatformAbi.allocatePointerArray(scope, 3)
            val externalPointer = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writePointer(externalPointer, externalVtable)

            WinRTInspectableComObject.inspectableBox(managedValue).use { host ->
                host.registerExternalPointerAlias(externalPointer)

                assertSame(managedValue, WinRTObjectMarshaller.fromAbi(externalPointer))
            }
        }
    }

    @Test
    fun borrowed_projected_argument_is_retained_once_and_released_by_its_owned_wrapper() {
        ProjectionReferenceSource.create().use { argumentSource ->
            ProjectionReferenceSource.create().use { resultSource ->
                val target = InboundOwnershipTarget(resultSource.value)
                createInboundOwnershipHost(target).use { host ->
                    host.createPrimaryReference().use { receiver ->
                        val before = argumentSource.referenceCount()

                        HResult(
                            ComVtableInvoker.invokeArgs(
                                instance = receiver.pointer,
                                slot = CONSUME_PROJECTION_SLOT,
                                arg0 = argumentSource.pointer,
                            ),
                        ).requireSuccess()

                        val received = assertNotNull(target.received)
                        val receivedReference = (received as IWinRTObject).nativeObject
                        try {
                            assertTrue(
                                PlatformAbi.samePointer(
                                    receivedReference.pointer,
                                    argumentSource.pointer.asRawComPtr(),
                                ),
                            )
                            assertEquals(before + 1u, argumentSource.referenceCount())
                        } finally {
                            receivedReference.close()
                            target.received = null
                        }

                        assertEquals(before, argumentSource.referenceCount())
                    }
                }
            }
        }
    }

    @Test
    fun raw_address_from_abi_codec_does_not_retain_a_borrowed_managed_object() {
        val managedValue = Any()
        val target = BorrowedObjectTarget()
        WinRTInspectableComObject.inspectableBox(managedValue).use { argumentHost ->
            argumentHost.createPrimaryReference().use { argument ->
                createBorrowedObjectHost(target).use { receiverHost ->
                    receiverHost.createPrimaryReference().use { receiver ->
                        val before = checkNotNull(
                            WinRTInspectableComObject.tryProbeReferenceCount(argument.pointer.asRawAddress()),
                        )

                        HResult(
                            ComVtableInvoker.invokeArgs(
                                instance = receiver.pointer,
                                slot = CONSUME_BORROWED_OBJECT_SLOT,
                                arg0 = argument.pointer.asRawAddress(),
                            ),
                        ).requireSuccess()

                        assertSame(managedValue, target.received)
                        assertEquals(
                            before,
                            checkNotNull(WinRTInspectableComObject.tryProbeReferenceCount(argument.pointer.asRawAddress())),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun projected_result_is_published_with_one_caller_owned_reference() {
        ProjectionReferenceSource.create().use { resultSource ->
            val target = InboundOwnershipTarget(resultSource.value)
            createInboundOwnershipHost(target).use { host ->
                host.createPrimaryReference().use { receiver ->
                    PlatformAbi.confinedScope().use { scope ->
                        val resultOut = PlatformAbi.allocatePointerSlot(scope)
                        PlatformAbi.writePointer(resultOut, PlatformAbi.nullPointer)
                        val before = resultSource.referenceCount()

                        HResult(
                            ComVtableInvoker.invokeArgs(
                                instance = receiver.pointer,
                                slot = PRODUCE_PROJECTION_SLOT,
                                arg0 = resultOut,
                            ),
                        ).requireSuccess()

                        val published = PlatformAbi.readPointer(resultOut)
                        assertTrue(
                            PlatformAbi.samePointer(
                                published.asRawComPtr(),
                                resultSource.pointer.asRawComPtr(),
                            ),
                        )
                        assertEquals(before + 1u, resultSource.referenceCount())

                        val callerOwnedReference = try {
                            IUnknownReference(
                                pointer = published.asRawComPtr(),
                                interfaceId = INBOUND_OWNERSHIP_PROJECTION_IID,
                            )
                        } catch (failure: Throwable) {
                            WinRTPlatformApi.releaseRaw(published)
                            throw failure
                        }
                        callerOwnedReference.use {
                            assertEquals(before + 1u, resultSource.referenceCount())
                        }

                        assertEquals(before, resultSource.referenceCount())
                    }
                }
            }
        }
    }
}

private class ProjectionReferenceSource private constructor(
    private val host: WinRTInspectableComObject,
    private val reference: ComObjectReference,
    val value: InboundOwnershipProjection,
) : AutoCloseable {
    val pointer: RawAddress
        get() = reference.pointer.asRawAddress()

    fun referenceCount(): UInt =
        checkNotNull(WinRTInspectableComObject.tryProbeReferenceCount(pointer))

    override fun close() {
        reference.close()
        host.close()
    }

    companion object {
        fun create(): ProjectionReferenceSource {
            val host = WinRTInspectableComObject(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = INBOUND_OWNERSHIP_PROJECTION_IID,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = INBOUND_OWNERSHIP_PROJECTION_IID,
                managedValue = Any(),
            )
            return try {
                val reference = host.createPrimaryReference()
                ProjectionReferenceSource(
                    host = host,
                    reference = reference,
                    value = InboundOwnershipProjectionImpl(reference),
                )
            } catch (failure: Throwable) {
                host.close()
                throw failure
            }
        }
    }
}

private fun createInboundOwnershipHost(target: InboundOwnershipTarget): WinRTInspectableComObject =
    WinRTInspectableComObject(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = INBOUND_OWNERSHIP_RECEIVER_IID,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        abiEntryPoint = winRTProjectionInboundEntryPoint(::consumeInboundOwnershipProjection),
                    ),
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        abiEntryPoint = winRTProjectionInboundEntryPoint(::produceInboundOwnershipProjection),
                    ),
                ),
            ),
        ),
        defaultInterfaceId = INBOUND_OWNERSHIP_RECEIVER_IID,
        managedValue = target,
    )

private fun createBorrowedObjectHost(target: BorrowedObjectTarget): WinRTInspectableComObject =
    WinRTInspectableComObject(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = INBOUND_BORROWED_OBJECT_RECEIVER_IID,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        abiEntryPoint = winRTProjectionInboundEntryPoint(::consumeBorrowedObject),
                    ),
                ),
            ),
        ),
        defaultInterfaceId = INBOUND_BORROWED_OBJECT_RECEIVER_IID,
        managedValue = target,
    )

private const val INBOUND_OWNERSHIP_PROJECTION_TYPE_NAME =
    "io.github.composefluent.winrt.runtime.InboundOwnershipProjection"
private val INBOUND_OWNERSHIP_PROJECTION_IID = Guid("6b70666e-80f4-4aba-b4e6-703d993f4115")
private val INBOUND_OWNERSHIP_RECEIVER_IID = Guid("48bfd769-8c9d-4efd-aeb4-2b6d9f21c253")
private val INBOUND_BORROWED_OBJECT_RECEIVER_IID = Guid("e3d6e079-6947-45e4-a201-df203b158c57")
private const val CONSUME_PROJECTION_SLOT = IInspectableVftblSlots.FirstCustom
private const val PRODUCE_PROJECTION_SLOT = IInspectableVftblSlots.FirstCustom + 1
private const val CONSUME_BORROWED_OBJECT_SLOT = IInspectableVftblSlots.FirstCustom
