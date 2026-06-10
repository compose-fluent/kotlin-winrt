package sample

import io.github.composefluent.winrt.runtime.WinRtAuthoredRuntimeClass
import windows.data.json.JsonArray
import windows.data.json.JsonObject
import windows.data.json.JsonValue
import windows.data.json.JsonValueType

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
