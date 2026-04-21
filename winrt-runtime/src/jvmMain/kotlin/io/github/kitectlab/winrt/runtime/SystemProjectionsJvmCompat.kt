package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.reflect.KClass

internal enum class WinRtTypeKind {
    Primitive,
    Metadata,
    Custom,
}

@WindowsRuntimeType("rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})")
internal object UriProjection {
    fun fromAbi(pointer: MemorySegment): WinRtUri? {
        if (pointer == MemorySegment.NULL) {
            return null
        }
        return IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use(::fromInspectable)
    }

    fun fromInspectable(inspectable: IInspectableReference): WinRtUri {
        val raw = getStringProperty(
            inspectable,
            slot = 16,
        )
        return WinRtUri(raw)
    }

    fun createReference(
        value: WinRtUri,
        interfaceId: Guid = IID.IInspectable,
    ): ComObjectReference {
        val inspectable =
            ActivationFactory.get("Windows.Foundation.Uri", IID.UriRuntimeClassFactory).use { factory ->
                HString.create(value.toString()).use { rawUri ->
                    Arena.ofConfined().use { arena ->
                        val resultOut = arena.allocate(ValueLayout.ADDRESS)
                        val hr = factory.invokeAbi(
                            slot = 6,
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                            rawUri.handle.asMemorySegment(),
                            resultOut,
                        )
                        WinRtPlatformApi.checkSucceededRaw(hr)
                        IInspectableReference(resultOut.get(ValueLayout.ADDRESS, 0), IID.IInspectable)
                    }
                }
            }

        if (interfaceId == IID.IInspectable) {
            return inspectable
        }

        return try {
            inspectable.queryInterface(interfaceId).getOrThrow()
        } finally {
            inspectable.close()
        }
    }

    private fun getStringProperty(
        inspectable: IInspectableReference,
        slot: Int,
    ): String =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = inspectable.invokeAbi(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            val handle = resultOut.get(ValueLayout.ADDRESS, 0)
            try {
                StringMarshaller.fromAbi(handle)
            } finally {
                if (handle != MemorySegment.NULL) {
                    StringMarshaller.disposeAbi(handle)
                }
            }
        }
}

private val uriTypeHandle = WinRtTypeHandle(WinRtUri::class.typeDisplayName(), Guid("9E365E57-48B2-4160-956F-C7385120BBFC"))
private val closableTypeHandle = WinRtTypeHandle(AutoCloseable::class.typeDisplayName(), IID.IDisposable)

@WindowsRuntimeType("struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))")
internal object TypeProjection {
    val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("name"),
        ValueLayout.JAVA_INT.withName("kind"),
    )

    fun fromAbi(value: TypeAbi): KClass<*>? {
        val name = StringMarshaller.fromAbi(value.name)
        if (name.isBlank()) {
            return null
        }
        return TypeNameSupport.findKClassByNameCached(name)
    }

    fun fromManaged(value: KClass<*>?): TypeAbi {
        if (value == null) {
            return TypeAbi()
        }
        val kind =
            when {
                value.javaPrimitiveType != null -> WinRtTypeKind.Primitive
                Projections.isTypeWindowsRuntimeType(value) -> WinRtTypeKind.Metadata
                else -> WinRtTypeKind.Custom
            }
        val typeName =
            if (kind == WinRtTypeKind.Custom) {
                value.qualifiedName ?: value.simpleName ?: "<anonymous>"
            } else {
                TypeNameSupport.getNameForType(value)
            }
        return TypeAbi(
            name = StringMarshaller.fromManaged(typeName)?.handle?.asMemorySegment() ?: MemorySegment.NULL,
            kind = kind.ordinal,
        )
    }

    fun copyTo(value: KClass<*>?, destination: MemorySegment) {
        val abi = fromManaged(value)
        destination.set(ValueLayout.ADDRESS, 0, abi.name)
        destination.set(ValueLayout.JAVA_INT, ValueLayout.ADDRESS.byteSize(), abi.kind)
    }

    fun fromAbi(source: MemorySegment): KClass<*>? =
        fromAbi(
            TypeAbi(
                name = source.get(ValueLayout.ADDRESS, 0),
                kind = source.get(ValueLayout.JAVA_INT, ValueLayout.ADDRESS.byteSize()),
            ),
        )

    fun disposeAbi(source: MemorySegment) {
        val name = source.get(ValueLayout.ADDRESS, 0)
        if (name != MemorySegment.NULL) {
            StringMarshaller.disposeAbi(name)
        }
    }

    data class TypeAbi(
        val name: MemorySegment = MemorySegment.NULL,
        val kind: Int = 0,
    )
}

internal object WinRtBuiltInProjectionMappings {
    fun register() {
        CommonWinRtBuiltInProjectionMappings.register()

        Projections.registerCustomAbiTypeMapping(
            publicType = WinRtUri::class,
            helperType = UriProjection::class,
            abiTypeName = "Windows.Foundation.Uri",
            isRuntimeClass = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = WinRtUri::class,
            projectedTypeName = "Windows.Foundation.Uri",
            helperType = UriProjection::class,
            signature = "rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})",
            runtimeClassName = "Windows.Foundation.Uri",
            defaultInterface = IUriRuntimeClassProjection::class,
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        Projections.registerDefaultInterfaceType(
            runtimeClass = WinRtUri::class,
            defaultInterface = IUriRuntimeClassProjection::class,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = IUriRuntimeClassProjection::class,
            projectedTypeName = IUriRuntimeClassProjection::class.typeDisplayName(),
            guid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            iid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            isWindowsRuntimeType = true,
        )

        Projections.registerCustomAbiTypeMapping(
            publicType = KClass::class,
            helperType = TypeProjection::class,
            abiTypeName = "Windows.UI.Xaml.Interop.TypeName",
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = KClass::class,
            projectedTypeName = "Windows.UI.Xaml.Interop.TypeName",
            helperType = TypeProjection::class,
            signature = "struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))",
            boxedName = WinRtReferenceTypeNames.boxedReference("Windows.UI.Xaml.Interop.TypeName"),
            isWindowsRuntimeType = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerReferenceArrayType(
            elementType = KClass::class,
            arrayType = emptyArray<KClass<*>>()::class,
        )
    }
}

internal object WinRtBuiltInProjectionRuntimeHooks {
    fun ensureRegistered() {
        ComWrappersSupport.registerRuntimeClassFactory("Windows.Foundation.Uri") { inspectable ->
            inspectable.use(UriProjection::fromInspectable)
        }
        ComWrappersSupport.registerTypedRcwFactory(uriTypeHandle) { inspectable ->
            inspectable.use(UriProjection::fromInspectable)
        }
        ComWrappersSupport.registerTypedRcwFactory(closableTypeHandle) { inspectable ->
            WinRtClosableObject(inspectable)
        }
    }

    fun tryCreateProjectedReference(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? =
        when (value) {
            is WinRtUri -> UriProjection.createReference(value, interfaceId ?: IID.IInspectable)
            else -> null
        }

    fun createSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? {
        WinRtValueBoxing.createInspectableBoxDefinition(value)?.let { return it }
        if (value is AutoCloseable) {
            return WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = IID.IDisposable,
                        methods = listOf(
                            WinRtInspectableMethodDefinition(
                                descriptor = FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS,
                                ),
                            ) { _ ->
                                value.close()
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                ),
                defaultInterfaceId = IID.IDisposable,
                runtimeClassName = runtimeClassNameFor(value),
            )
        }
        return null
    }

    fun runtimeClassNameFor(value: Any): String? {
        WinRtValueBoxing.boxedRuntimeClassNameForType(value::class)?.let { return it }
        val lookupName =
            TypeNameSupport.getNameForType(
                value::class,
                setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
            )
        return lookupName.takeIf(String::isNotBlank) ?: value::class.qualifiedName ?: value::class.toString()
    }
}
