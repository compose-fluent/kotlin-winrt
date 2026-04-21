package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration

internal enum class WinRtTypeKind {
    Primitive,
    Metadata,
    Custom,
}

@WindowsRuntimeType("struct(Windows.Foundation.TimeSpan;i8)")
internal object TimeSpanProjection {
    private const val TICKS_PER_SECOND: Long = 10_000_000L
    private const val NANOS_PER_TICK: Long = 100L

    fun fromAbi(value: Long): Duration =
        Math.multiplyExact(value, NANOS_PER_TICK).toDuration(DurationUnit.NANOSECONDS)

    fun toAbi(value: Duration): Long =
        value.toLong(DurationUnit.NANOSECONDS) / NANOS_PER_TICK

    fun copyTo(value: Duration, destination: MemorySegment) {
        destination.set(ValueLayout.JAVA_LONG, 0, toAbi(value))
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.DateTime;i8)")
internal object DateTimeProjection {
    private val managedUtcTicksAtNativeZero: Long = 504_911_232_000_000_000L
    private const val TICKS_PER_SECOND: Long = 10_000_000L
    private const val NANOS_PER_TICK: Long = 100L

    fun fromAbi(value: Long): Instant {
        val utcTicks = Math.addExact(value, managedUtcTicksAtNativeZero)
        val seconds = Math.floorDiv(utcTicks, TICKS_PER_SECOND) - EPOCH_ADJUST_SECONDS
        val nanos = Math.floorMod(utcTicks, TICKS_PER_SECOND) * NANOS_PER_TICK
        return Instant.fromEpochSeconds(seconds, nanos)
    }

    fun toAbi(value: Instant): Long {
        val utcTicks = Math.addExact(
            Math.multiplyExact(Math.addExact(value.epochSeconds, EPOCH_ADJUST_SECONDS), TICKS_PER_SECOND),
            value.nanosecondsOfSecond.toLong() / NANOS_PER_TICK,
        )
        return Math.subtractExact(utcTicks, managedUtcTicksAtNativeZero)
    }

    fun copyTo(value: Instant, destination: MemorySegment) {
        destination.set(ValueLayout.JAVA_LONG, 0, toAbi(value))
    }

    private const val EPOCH_ADJUST_SECONDS: Long = 62_135_596_800L
}

@WindowsRuntimeType("struct(Windows.Foundation.HResult;i4)")
internal object ExceptionProjection {
    fun fromAbi(value: Int): Exception = ExceptionHelpers.exceptionFor(HResult(value), "Windows.Foundation.HResult")

    fun toAbi(value: Exception): Int = ExceptionHelpers.getHRForException(value).value
}

@WinRtGuid("9E365E57-48B2-4160-956F-C7385120BBFC")
internal interface IUriRuntimeClassProjection

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

@WinRtGuid("30D5A829-7FA4-4026-83BB-D75BAE4EA99E")
internal interface IClosableProjection

private val uriTypeHandle = WinRtTypeHandle(WinRtUri::class.java.name, Guid("9E365E57-48B2-4160-956F-C7385120BBFC"))
private val closableTypeHandle = WinRtTypeHandle(AutoCloseable::class.java.name, IID.IDisposable)

internal class WinRtClosableObject(
    private val inspectable: IInspectableReference,
) : AutoCloseable, IWinRTObject {
    override val nativeObject: ComObjectReference
        get() = inspectable

    override fun close() {
        inspectable.tryQueryInterface(IID.IDisposable)?.use { closable ->
            closable.invokeUnitMethod(6)
            return
        }
        throw WinRtUnsupportedOperationException(
            "Object does not implement Windows.Foundation.IClosable.",
            KnownHResults.E_NOINTERFACE,
        )
    }
}

@WindowsRuntimeType("struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))")
internal object TypeProjection {
    val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("name"),
        ValueLayout.JAVA_INT.withName("kind"),
    )

    fun fromAbi(value: TypeAbi): Class<*>? {
        val name = StringMarshaller.fromAbi(value.name)
        if (name.isBlank()) {
            return null
        }
        return TypeNameSupport.findTypeByNameCached(name)
    }

    fun fromManaged(value: Class<*>?): TypeAbi {
        if (value == null) {
            return TypeAbi()
        }
        val kind =
            when {
                value.isPrimitive -> WinRtTypeKind.Primitive
                Projections.isTypeWindowsRuntimeType(value) ->
                    WinRtTypeKind.Metadata
                else -> WinRtTypeKind.Custom
            }
        val typeName =
            if (kind == WinRtTypeKind.Custom) {
                value.name
            } else {
                TypeNameSupport.getNameForType(value)
            }
        return TypeAbi(
            name = StringMarshaller.fromManaged(typeName)?.handle?.asMemorySegment() ?: MemorySegment.NULL,
            kind = kind.ordinal,
        )
    }

    fun copyTo(value: Class<*>?, destination: MemorySegment) {
        val abi = fromManaged(value)
        destination.set(ValueLayout.ADDRESS, 0, abi.name)
        destination.set(ValueLayout.JAVA_INT, ValueLayout.ADDRESS.byteSize(), abi.kind)
    }

    fun fromAbi(source: MemorySegment): Class<*>? =
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
        Projections.registerCustomAbiTypeMapping(
            publicType = Instant::class.java,
            helperType = DateTimeProjection::class.java,
            abiTypeName = "Windows.Foundation.DateTime",
        )
        registerMetadata(
            type = Instant::class.java,
            projectedTypeName = "Windows.Foundation.DateTime",
            helperType = DateTimeProjection::class.java,
            signature = "struct(Windows.Foundation.DateTime;i8)",
            isWindowsRuntimeType = true,
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = Duration::class.java,
            helperType = TimeSpanProjection::class.java,
            abiTypeName = "Windows.Foundation.TimeSpan",
        )
        registerMetadata(
            type = Duration::class.java,
            projectedTypeName = "Windows.Foundation.TimeSpan",
            helperType = TimeSpanProjection::class.java,
            signature = "struct(Windows.Foundation.TimeSpan;i8)",
            isWindowsRuntimeType = true,
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = Exception::class.java,
            helperType = ExceptionProjection::class.java,
            abiTypeName = "Windows.Foundation.HResult",
        )
        registerMetadata(
            type = Exception::class.java,
            projectedTypeName = "Windows.Foundation.HResult",
            helperType = ExceptionProjection::class.java,
            signature = "struct(Windows.Foundation.HResult;i4)",
            isWindowsRuntimeType = true,
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = WinRtUri::class.java,
            helperType = UriProjection::class.java,
            abiTypeName = "Windows.Foundation.Uri",
            isRuntimeClass = true,
        )
        registerMetadata(
            type = WinRtUri::class.java,
            projectedTypeName = "Windows.Foundation.Uri",
            helperType = UriProjection::class.java,
            signature = "rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})",
            runtimeClassName = "Windows.Foundation.Uri",
            defaultInterface = IUriRuntimeClassProjection::class.java,
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        Projections.registerDefaultInterfaceType(
            runtimeClass = WinRtUri::class.java,
            defaultInterface = IUriRuntimeClassProjection::class.java,
        )
        registerMetadata(
            type = IUriRuntimeClassProjection::class.java,
            projectedTypeName = IUriRuntimeClassProjection::class.java.name,
            guid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            iid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            isWindowsRuntimeType = true,
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = AutoCloseable::class.java,
            helperType = IClosableProjection::class.java,
            abiTypeName = "Windows.Foundation.IClosable",
        )
        registerMetadata(
            type = AutoCloseable::class.java,
            projectedTypeName = "Windows.Foundation.IClosable",
            helperType = IClosableProjection::class.java,
            isWindowsRuntimeType = true,
        )
        registerMetadata(
            type = IClosableProjection::class.java,
            projectedTypeName = IClosableProjection::class.java.name,
            guid = IID.IDisposable,
            iid = IID.IDisposable,
            isWindowsRuntimeType = true,
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = Class::class.java,
            helperType = TypeProjection::class.java,
            abiTypeName = "Windows.UI.Xaml.Interop.TypeName",
        )
        registerMetadata(
            type = Class::class.java,
            projectedTypeName = "Windows.UI.Xaml.Interop.TypeName",
            helperType = TypeProjection::class.java,
            signature = "struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))",
            boxedName = WinRtReferenceTypeNames.boxedReference("Windows.UI.Xaml.Interop.TypeName"),
            isWindowsRuntimeType = true,
        )
        registerStruct(Point::class.java)
        registerStruct(Size::class.java)
        registerStruct(Rect::class.java)
        registerStruct(Matrix3x2::class.java)
        registerStruct(Matrix4x4::class.java)
        registerStruct(Plane::class.java)
        registerStruct(Quaternion::class.java)
        registerStruct(Vector2::class.java)
        registerStruct(Vector3::class.java)
        registerStruct(Vector4::class.java)

        registerReferenceArrayType(String::class.java, Array<String>::class.java)
        registerReferenceArrayType(Guid::class.java, Array<Guid>::class.java)
        registerReferenceArrayType(Instant::class.java, Array<Instant>::class.java)
        registerReferenceArrayType(Duration::class.java, Array<Duration>::class.java)
        registerReferenceArrayType(Exception::class.java, Array<Exception>::class.java)
        registerReferenceArrayType(Class::class.java, emptyArray<Class<*>>()::class.java)
        registerReferenceArrayType(Point::class.java, Array<Point>::class.java)
        registerReferenceArrayType(Size::class.java, Array<Size>::class.java)
        registerReferenceArrayType(Rect::class.java, Array<Rect>::class.java)
        registerReferenceArrayType(Matrix3x2::class.java, Array<Matrix3x2>::class.java)
        registerReferenceArrayType(Matrix4x4::class.java, Array<Matrix4x4>::class.java)
        registerReferenceArrayType(Plane::class.java, Array<Plane>::class.java)
        registerReferenceArrayType(Quaternion::class.java, Array<Quaternion>::class.java)
        registerReferenceArrayType(Vector2::class.java, Array<Vector2>::class.java)
        registerReferenceArrayType(Vector3::class.java, Array<Vector3>::class.java)
        registerReferenceArrayType(Vector4::class.java, Array<Vector4>::class.java)
    }

    private fun registerStruct(publicType: Class<*>) {
        val registeredType =
            publicType.registeredWinRtType()
                ?: error("Struct type '${publicType.name}' is missing WindowsRuntimeType metadata.")
        val signature =
            registeredType.signature
                ?: error("Struct type '${publicType.name}' is missing a WinRT signature.")
        Projections.registerCustomAbiTypeMapping(
            publicType = publicType,
            helperType = publicType,
            abiTypeName = registeredType.projectedTypeName,
        )
        registerMetadata(
            type = publicType,
            projectedTypeName = registeredType.projectedTypeName,
            helperType = publicType,
            signature = signature,
            isWindowsRuntimeType = true,
        )
    }

    private fun registerMetadata(
        type: Class<*>,
        projectedTypeName: String,
        helperType: Class<*>? = null,
        guid: Guid? = null,
        iid: Guid? = null,
        signature: String? = null,
        boxedName: String? = null,
        runtimeClassName: String? = null,
        defaultInterface: Class<*>? = null,
        isRuntimeClass: Boolean = false,
        isWindowsRuntimeType: Boolean = false,
    ) {
        val kClass = type.registeredKClass()
        WinRtTypeRegistry.update(kClass) { existing ->
            WinRtTypeId(
                kClass = kClass,
                projectedTypeName = projectedTypeName,
                guid = guid ?: existing?.guid,
                iid = iid ?: existing?.iid,
                signature = signature ?: existing?.signature,
                enumAbiValue = existing?.enumAbiValue,
                helperType = helperType?.registeredKClass() ?: existing?.helperType,
                defaultInterface = defaultInterface?.registeredKClass() ?: existing?.defaultInterface,
                boxedName = boxedName ?: existing?.boxedName,
                runtimeClassName = runtimeClassName ?: existing?.runtimeClassName,
                vftblType = existing?.vftblType,
                isDelegate = existing?.isDelegate == true,
                isRuntimeClass = isRuntimeClass || existing?.isRuntimeClass == true,
                isWindowsRuntimeType = isWindowsRuntimeType || existing?.isWindowsRuntimeType == true,
                aliases = existing?.aliases.orEmpty(),
            )
        }
    }

    private fun registerReferenceArrayType(
        elementType: Class<*>,
        arrayType: Class<*>,
    ) {
        TypeNameSupport.registerReferenceArrayType(elementType, arrayType)
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
        WinRtValueBoxing.boxedRuntimeClassNameForType(value.javaClass)?.let { return it }
        val lookupName =
            TypeNameSupport.getNameForType(
                value.javaClass,
                setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
            )
        return lookupName.takeIf(String::isNotBlank) ?: value::class.qualifiedName ?: value.javaClass.name
    }
}
