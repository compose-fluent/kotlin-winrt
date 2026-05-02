package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration

@WindowsRuntimeType("struct(Windows.Foundation.TimeSpan;i8)")
internal object TimeSpanProjection {
    private const val NANOS_PER_TICK: Long = 100L

    fun fromAbi(value: Long): Duration =
        exactMultiply(value, NANOS_PER_TICK).toDuration(DurationUnit.NANOSECONDS)

    fun toAbi(value: Duration): Long =
        value.toLong(DurationUnit.NANOSECONDS) / NANOS_PER_TICK

    fun copyTo(value: Duration, destination: RawAddress) {
        PlatformAbi.writeInt64(destination, toAbi(value))
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.DateTime;i8)")
internal object DateTimeProjection {
    private val managedUtcTicksAtNativeZero: Long = 504_911_232_000_000_000L
    private const val TICKS_PER_SECOND: Long = 10_000_000L
    private const val NANOS_PER_TICK: Long = 100L
    private const val EPOCH_ADJUST_SECONDS: Long = 62_135_596_800L

    fun fromAbi(value: Long): Instant {
        val utcTicks = exactAdd(value, managedUtcTicksAtNativeZero)
        val seconds = utcTicks.floorDiv(TICKS_PER_SECOND) - EPOCH_ADJUST_SECONDS
        val nanos = utcTicks.mod(TICKS_PER_SECOND) * NANOS_PER_TICK
        return Instant.fromEpochSeconds(seconds, nanos)
    }

    fun toAbi(value: Instant): Long {
        val utcTicks = exactAdd(
            exactMultiply(exactAdd(value.epochSeconds, EPOCH_ADJUST_SECONDS), TICKS_PER_SECOND),
            value.nanosecondsOfSecond.toLong() / NANOS_PER_TICK,
        )
        return exactSubtract(utcTicks, managedUtcTicksAtNativeZero)
    }

    fun copyTo(value: Instant, destination: RawAddress) {
        PlatformAbi.writeInt64(destination, toAbi(value))
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.HResult;i4)")
internal object ExceptionProjection {
    fun fromAbi(value: Int): Exception = ExceptionHelpers.exceptionFor(HResult(value), "Windows.Foundation.HResult")

    fun toAbi(value: Exception): Int = ExceptionHelpers.getHRForException(value).value
}

/**
 * Public generator-facing facade for the built-in custom ABI mappings mirrored from
 * `.cswinrt/src/WinRT.Runtime/Projections.cs`.
 */
object WinRtSystemProjectionMarshalers {
    fun dateTimeFromAbi(source: RawAddress): Instant =
        DateTimeProjection.fromAbi(PlatformAbi.readInt64(source))

    fun copyDateTimeTo(
        value: Instant,
        destination: RawAddress,
    ) {
        DateTimeProjection.copyTo(value, destination)
    }

    fun timeSpanFromAbi(source: RawAddress): Duration =
        TimeSpanProjection.fromAbi(PlatformAbi.readInt64(source))

    fun copyTimeSpanTo(
        value: Duration,
        destination: RawAddress,
    ) {
        TimeSpanProjection.copyTo(value, destination)
    }

    fun hResultFromAbi(source: RawAddress): Exception =
        ExceptionProjection.fromAbi(PlatformAbi.readInt32(source))

    fun copyHResultTo(
        value: Exception,
        destination: RawAddress,
    ) {
        PlatformAbi.writeInt32(destination, ExceptionProjection.toAbi(value))
    }

    fun typeNameFromAbi(source: RawAddress): KClass<*>? =
        TypeProjection.fromAbi(source)

    fun copyTypeNameTo(
        value: KClass<*>?,
        destination: RawAddress,
    ) {
        TypeProjection.copyTo(value, destination)
    }

    fun disposeTypeNameAbi(source: RawAddress) {
        TypeProjection.disposeAbi(source)
    }

    fun uriFromAbi(pointer: RawAddress): WinRtUri? =
        UriProjection.fromAbi(pointer)

    fun <T : Any> objectFromAbi(
        pointer: RawAddress,
        typeHandle: WinRtTypeHandle,
        expectedType: KClass<T>,
    ): T? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }
        ComWrappersSupport.findObject(pointer, expectedType)?.let { return it }
        val projected = ComWrappersSupport.createRcwForComObject(pointer, typeHandle)
        if (expectedType.isInstance(projected)) {
            @Suppress("UNCHECKED_CAST")
            return projected as T
        }
        throw WinRtInvalidCastException(
            "Expected projected value assignable to ${expectedType.typeDisplayName()}.",
            KnownHResults.E_NOINTERFACE,
        )
    }

    fun createObjectReference(
        value: Any,
        interfaceId: Guid,
    ): ComObjectReference =
        ComWrappersSupport.createCCWForObject(value, interfaceId)
}

@WinRtGuid("9E365E57-48B2-4160-956F-C7385120BBFC")
internal interface IUriRuntimeClassProjection

@WinRtGuid("30D5A829-7FA4-4026-83BB-D75BAE4EA99E")
internal interface IClosableProjection

class WinRtClosableObject(
    private val inspectable: IInspectableReference,
) : AutoCloseable, IWinRTObject {
    override val nativeObject: ComObjectReference
        get() = inspectable

    override fun close() {
        inspectable.tryQueryInterface(IID.IDisposable)?.use { closable ->
            val hr = ComVtableInvoker.invoke(closable.pointer, slot = 6)
            WinRtPlatformApi.checkSucceededRaw(hr)
            return
        }
        throw WinRtUnsupportedOperationException(
            "Object does not implement Windows.Foundation.IClosable.",
            KnownHResults.E_NOINTERFACE,
        )
    }
}

internal object CommonWinRtBuiltInProjectionMappings {
    fun register() {
        Projections.registerCustomAbiTypeMapping(
            publicType = Instant::class,
            helperType = DateTimeProjection::class,
            abiTypeName = "Windows.Foundation.DateTime",
        )
        registerMetadata(
            type = Instant::class,
            projectedTypeName = "Windows.Foundation.DateTime",
            helperType = DateTimeProjection::class,
            signature = "struct(Windows.Foundation.DateTime;i8)",
            isWindowsRuntimeType = true,
        )

        Projections.registerCustomAbiTypeMapping(
            publicType = Duration::class,
            helperType = TimeSpanProjection::class,
            abiTypeName = "Windows.Foundation.TimeSpan",
        )
        registerMetadata(
            type = Duration::class,
            projectedTypeName = "Windows.Foundation.TimeSpan",
            helperType = TimeSpanProjection::class,
            signature = "struct(Windows.Foundation.TimeSpan;i8)",
            isWindowsRuntimeType = true,
        )

        Projections.registerCustomAbiTypeMapping(
            publicType = Exception::class,
            helperType = ExceptionProjection::class,
            abiTypeName = "Windows.Foundation.HResult",
        )
        registerMetadata(
            type = Exception::class,
            projectedTypeName = "Windows.Foundation.HResult",
            helperType = ExceptionProjection::class,
            signature = "struct(Windows.Foundation.HResult;i4)",
            isWindowsRuntimeType = true,
        )

        Projections.registerCustomAbiTypeMapping(
            publicType = AutoCloseable::class,
            helperType = IClosableProjection::class,
            abiTypeName = "Windows.Foundation.IClosable",
        )
        registerMetadata(
            type = AutoCloseable::class,
            projectedTypeName = "Windows.Foundation.IClosable",
            helperType = IClosableProjection::class,
            isWindowsRuntimeType = true,
        )
        registerMetadata(
            type = IClosableProjection::class,
            projectedTypeName = IClosableProjection::class.typeDisplayName(),
            guid = IID.IDisposable,
            iid = IID.IDisposable,
            isWindowsRuntimeType = true,
        )

        registerStruct(EventRegistrationToken::class)

        registerReferenceArrayType(String::class, emptyArray<String>()::class)
        registerReferenceArrayType(Guid::class, emptyArray<Guid>()::class)
        registerReferenceArrayType(Instant::class, emptyArray<Instant>()::class)
        registerReferenceArrayType(Duration::class, emptyArray<Duration>()::class)
        registerReferenceArrayType(Exception::class, emptyArray<Exception>()::class)
    }

    internal fun <T : Any> registerMetadata(
        type: KClass<T>,
        projectedTypeName: String,
        helperType: KClass<*>? = null,
        guid: Guid? = null,
        iid: Guid? = null,
        signature: String? = null,
        boxedName: String? = null,
        runtimeClassName: String? = null,
        defaultInterface: KClass<*>? = null,
        isRuntimeClass: Boolean = false,
        isWindowsRuntimeType: Boolean = false,
    ) {
        WinRtTypeRegistry.update(type) { existing ->
            WinRtTypeId(
                kClass = type,
                projectedTypeName = projectedTypeName,
                guid = guid ?: existing?.guid,
                iid = iid ?: existing?.iid,
                signature = signature ?: existing?.signature,
                enumAbiValue = existing?.enumAbiValue,
                helperType = helperType ?: existing?.helperType,
                defaultInterface = defaultInterface ?: existing?.defaultInterface,
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

    internal fun registerStruct(publicType: KClass<*>) {
        val registeredType =
            publicType.registeredWinRtType()
                ?: publicType.windowsRuntimeStructType()
                ?: error("Struct type '${publicType.typeDisplayName()}' is missing WindowsRuntimeType metadata.")
        val signature =
            registeredType.signature
                ?: error("Struct type '${publicType.typeDisplayName()}' is missing a WinRT signature.")
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

    internal fun registerReferenceArrayType(
        elementType: KClass<*>,
        arrayType: KClass<*>,
    ) {
        TypeNameSupport.registerReferenceArrayType(elementType, arrayType)
    }

    private fun KClass<*>.windowsRuntimeStructType(): WinRtTypeId<*>? {
        val signature = builtInStructSignatures[this] ?: return null
        val projectedName = signature.removePrefix("struct(").substringBefore(';')
        return WinRtTypeId(
            kClass = this,
            projectedTypeName = projectedName,
            signature = signature,
            isWindowsRuntimeType = true,
        ).also(WinRtTypeRegistry::register)
    }

    private val builtInStructSignatures: Map<KClass<*>, String> = mapOf(
        EventRegistrationToken::class to "struct(Windows.Foundation.EventRegistrationToken;i8)",
    )
}

private fun exactAdd(left: Long, right: Long): Long {
    val result = left + right
    if (((left xor result) and (right xor result)) < 0) {
        throw ArithmeticException("long overflow")
    }
    return result
}

private fun exactSubtract(left: Long, right: Long): Long {
    val result = left - right
    if (((left xor right) and (left xor result)) < 0) {
        throw ArithmeticException("long overflow")
    }
    return result
}

private fun exactMultiply(left: Long, right: Long): Long {
    val result = left * right
    if (left != 0L && result / left != right) {
        throw ArithmeticException("long overflow")
    }
    return result
}
