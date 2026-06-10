package sample

import io.github.composefluent.winrt.runtime.WinRtAuthoredRuntimeClass
import windows.data.json.JsonArray
import windows.data.json.JsonObject
import windows.data.json.JsonValueType

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IClosable"])
class NativeClosableThing {
    fun close() = Unit
}

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
class NativeStringableThing {
    override fun toString(): String = "NativeStringableThing"
}

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.data.json.IJsonValue"])
class NativeJsonValueThing {
    val valueType: JsonValueType
        get() = JsonValueType.String

    fun stringify(): String = "\"NativeJsonValueThing\""

    fun getString(): String = "NativeJsonValueThing"

    fun getNumber(): Double = 42.5

    fun getBoolean(): Boolean = true

    fun getArray(): JsonArray {
        throw UnsupportedOperationException("NativeJsonValueThing does not expose an array value.")
    }

    fun getObject(): JsonObject {
        throw UnsupportedOperationException("NativeJsonValueThing does not expose an object value.")
    }
}
