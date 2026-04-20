package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.net.URI
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
    fun fromAbi(pointer: MemorySegment): URI? {
        if (pointer == MemorySegment.NULL) {
            return null
        }
        return IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use(::fromInspectable)
    }

    fun fromInspectable(inspectable: IInspectableReference): URI {
        val raw = getStringProperty(
            inspectable,
            slot = 16,
        )
        return URI(raw)
    }

    fun createReference(
        value: URI,
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
                            rawUri.handle,
                            resultOut,
                        )
                        WindowsRuntimePlatform.checkSucceeded(hr)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
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

private val uriTypeHandle = WinRtTypeHandle(URI::class.java.name, Guid("9E365E57-48B2-4160-956F-C7385120BBFC"))
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
        return if (value.kind == WinRtTypeKind.Custom.ordinal) {
            runCatching { Class.forName(name) }.getOrNull()
        } else {
            TypeNameSupport.findTypeByNameCached(name)
        }
    }

    fun fromManaged(value: Class<*>?): TypeAbi {
        if (value == null) {
            return TypeAbi()
        }
        val kind =
            when {
                value.isPrimitive -> WinRtTypeKind.Primitive
                Projections.isTypeWindowsRuntimeType(value) || value == Any::class.java || value == String::class.java || value == Guid::class.java || value == Class::class.java ->
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
            name = StringMarshaller.fromManaged(typeName)?.handle ?: MemorySegment.NULL,
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
        Projections.registerCustomAbiTypeMapping(
            publicType = Duration::class.java,
            helperType = TimeSpanProjection::class.java,
            abiTypeName = "Windows.Foundation.TimeSpan",
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = Exception::class.java,
            helperType = ExceptionProjection::class.java,
            abiTypeName = "Windows.Foundation.HResult",
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = URI::class.java,
            helperType = UriProjection::class.java,
            abiTypeName = "Windows.Foundation.Uri",
            isRuntimeClass = true,
        )
        Projections.registerDefaultInterfaceType(
            runtimeClass = URI::class.java,
            defaultInterface = IUriRuntimeClassProjection::class.java,
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = AutoCloseable::class.java,
            helperType = IClosableProjection::class.java,
            abiTypeName = "Windows.Foundation.IClosable",
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = Class::class.java,
            helperType = TypeProjection::class.java,
            abiTypeName = "Windows.UI.Xaml.Interop.TypeName",
        )
        registerStruct(Point::class.java, "Windows.Foundation.Point")
        registerStruct(Size::class.java, "Windows.Foundation.Size")
        registerStruct(Rect::class.java, "Windows.Foundation.Rect")
        registerStruct(Matrix3x2::class.java, "Windows.Foundation.Numerics.Matrix3x2")
        registerStruct(Matrix4x4::class.java, "Windows.Foundation.Numerics.Matrix4x4")
        registerStruct(Plane::class.java, "Windows.Foundation.Numerics.Plane")
        registerStruct(Quaternion::class.java, "Windows.Foundation.Numerics.Quaternion")
        registerStruct(Vector2::class.java, "Windows.Foundation.Numerics.Vector2")
        registerStruct(Vector3::class.java, "Windows.Foundation.Numerics.Vector3")
        registerStruct(Vector4::class.java, "Windows.Foundation.Numerics.Vector4")
    }

    private fun registerStruct(
        publicType: Class<*>,
        abiTypeName: String,
    ) {
        Projections.registerCustomAbiTypeMapping(
            publicType = publicType,
            helperType = publicType,
            abiTypeName = abiTypeName,
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
            is URI -> UriProjection.createReference(value, interfaceId ?: IID.IInspectable)
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
