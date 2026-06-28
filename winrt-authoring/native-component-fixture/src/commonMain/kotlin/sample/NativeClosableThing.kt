package sample

import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass
import io.github.composefluent.winrt.runtime.AsyncInfo
import io.github.composefluent.winrt.runtime.EventRegistrationTokenTable
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncResultWriter
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import windows.data.json.JsonArray
import windows.data.json.JsonObject
import windows.data.json.JsonValue
import windows.data.json.JsonValueType
import windows.foundation.EventRegistrationToken
import windows.foundation.collections.MapChangedEventHandler
import windows.storage.streams.ByteOrder
import windows.storage.streams.IBuffer
import windows.storage.streams.IInputStream
import windows.storage.streams.InputStreamOptions
import windows.storage.streams.UnicodeEncoding
import kotlin.time.Duration
import kotlin.time.Instant

@WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IClosable"])
class NativeClosableThing {
    fun close() = Unit
}

@WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
class NativeStringableThing {
    override fun toString(): String = "NativeStringableThing"
}

@WinRTAuthoredRuntimeClass(
    interfaceNames = ["windows.data.json.IJsonValue"],
    staticFactoryInterfaceNames = ["windows.data.json.IJsonValueStatics"],
)
class NativeJsonValueThing private constructor(
    private val value: String,
) {
    constructor() : this("NativeJsonValueThing")
    val valueType: JsonValueType
        get() = JsonValueType.String

    fun stringify(): String = "\"$value\""

    fun getString(): String = value

    fun getNumber(): Double = 42.5

    fun getBoolean(): Boolean = true

    fun getArray(): JsonArray = JsonArray()

    fun getObject(): JsonObject = JsonObject()

    companion object {
        fun parse(input: String): NativeJsonValueThing = NativeJsonValueThing(input)

        fun tryParse(input: String, result: JsonValue): Boolean = input.isNotEmpty() && result.getString().isNotEmpty()

        fun createBooleanValue(input: Boolean): NativeJsonValueThing = NativeJsonValueThing(input.toString())

        fun createNumberValue(input: Double): NativeJsonValueThing = NativeJsonValueThing(input.toString())

        fun createStringValue(input: String): NativeJsonValueThing = NativeJsonValueThing(input)
    }
}

@WinRTAuthoredRuntimeClass(interfaceNames = ["windows.storage.streams.IDataReader"])
class NativeDataReaderThing {
    val unconsumedBufferLength: UInt
        get() = 4u

    var unicodeEncoding: UnicodeEncoding = UnicodeEncoding.Utf8

    var byteOrder: ByteOrder = ByteOrder.LittleEndian

    var inputStreamOptions: InputStreamOptions = InputStreamOptions.Metadata.None

    fun readByte(): UByte = 0x41u

    fun readBytes(value: Array<UByte>) {
        val bytes = arrayOf(0x57u, 0x69u, 0x6Eu, 0x52u).map { it.toUByte() }
        value.indices.forEach { index ->
            value[index] = bytes.getOrElse(index) { 0u.toUByte() }
        }
    }

    fun readBuffer(length: UInt): IBuffer {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose readBuffer.")
    }

    fun readBoolean(): Boolean = true

    fun readGuid() = io.github.composefluent.winrt.runtime.Guid("11111111-2222-3333-4444-555555555555")

    fun readInt16(): Short = 16

    fun readInt32(): Int = 32

    fun readInt64(): Long = 64

    fun readUInt16(): UShort = 16u

    fun readUInt32(): UInt = 32u

    fun readUInt64(): ULong = 64u

    fun readSingle(): Float = 1.25f

    fun readDouble(): Double = 2.5

    fun readString(codeUnitCount: UInt): String = "WinR".take(codeUnitCount.toInt())

    fun readDateTime(): Instant =
        Instant.fromEpochSeconds(1_700_000_000L, 123_456_700)

    fun readTimeSpan(): Duration =
        Duration.parse("PT1H2M3.4567S")

    fun loadAsync(count: UInt): WinRTAsyncOperationReference<UInt> =
        AsyncInfo.fromResult(
            result = count.coerceAtMost(unconsumedBufferLength),
            resultSignature = WinRTTypeSignature.uint32(),
            resultWriter = WinRTAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value.toInt())
            },
        )

    fun detachBuffer(): IBuffer {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose detachBuffer.")
    }

    fun detachStream(): IInputStream {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose detachStream.")
    }
}

@WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.collections.IPropertySet"])
class NativePropertySetThing {
    private val values = linkedMapOf<String, Any?>("existing" to "value")
    private val mapChangedHandlers =
        EventRegistrationTokenTable.create<MapChangedEventHandler<String, Any?>>()

    val size: UInt
        get() = values.size.toUInt()

    fun lookup(key: String): Any? = values.getValue(key)

    fun hasKey(key: String): Boolean = values.containsKey(key)

    fun getView(): Map<String, Any?> = values.toMap()

    fun iterator(): Iterator<Map.Entry<String, Any?>> =
        values.entries.map { entry -> object : Map.Entry<String, Any?> {
            override val key: String = entry.key
            override val value: Any? = entry.value
        } }.iterator()

    fun insert(key: String, value: Any?): Boolean {
        val replaced = values.containsKey(key)
        values[key] = value
        return replaced
    }

    fun remove(key: String) {
        values.remove(key)
    }

    fun clear() {
        values.clear()
    }

    fun addMapChanged(handler: MapChangedEventHandler<String, Any?>): EventRegistrationToken =
        mapChangedHandlers.addEventHandler(handler)

    fun removeMapChanged(token: EventRegistrationToken) {
        mapChangedHandlers.removeEventHandler(token)
    }
}
