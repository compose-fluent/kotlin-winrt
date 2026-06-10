package sample

import io.github.composefluent.winrt.runtime.WinRtAuthoredRuntimeClass
import windows.data.json.JsonArray
import windows.data.json.JsonObject
import windows.data.json.JsonValue
import windows.data.json.JsonValueType
import windows.storage.streams.ByteOrder
import windows.storage.streams.DataReaderLoadOperation
import windows.storage.streams.IBuffer
import windows.storage.streams.IInputStream
import windows.storage.streams.InputStreamOptions
import windows.storage.streams.UnicodeEncoding
import kotlin.time.Duration
import kotlin.time.Instant

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IClosable"])
class NativeClosableThing {
    fun close() = Unit
}

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
class NativeStringableThing {
    override fun toString(): String = "NativeStringableThing"
}

@WinRtAuthoredRuntimeClass(
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

    fun getArray(): JsonArray {
        throw UnsupportedOperationException("NativeJsonValueThing does not expose an array value.")
    }

    fun getObject(): JsonObject {
        throw UnsupportedOperationException("NativeJsonValueThing does not expose an object value.")
    }

    companion object {
        fun parse(input: String): NativeJsonValueThing = NativeJsonValueThing(input)

        fun tryParse(input: String, result: JsonValue): Boolean = input.isNotEmpty() && result.getString().isNotEmpty()

        fun createBooleanValue(input: Boolean): NativeJsonValueThing = NativeJsonValueThing(input.toString())

        fun createNumberValue(input: Double): NativeJsonValueThing = NativeJsonValueThing(input.toString())

        fun createStringValue(input: String): NativeJsonValueThing = NativeJsonValueThing(input)
    }
}

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.storage.streams.IDataReader"])
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

    fun readDateTime(): Instant {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose readDateTime.")
    }

    fun readTimeSpan(): Duration {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose readTimeSpan.")
    }

    fun loadAsync(count: UInt): DataReaderLoadOperation {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose loadAsync.")
    }

    fun detachBuffer(): IBuffer {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose detachBuffer.")
    }

    fun detachStream(): IInputStream {
        throw UnsupportedOperationException("NativeDataReaderThing does not expose detachStream.")
    }
}
