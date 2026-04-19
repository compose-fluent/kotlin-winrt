package io.github.kitectlab.winrt.projections.windows.data.json

enum class JsonValueType(val abiValue: Int) {
    Null(0),
    Boolean(1),
    Number(2),
    String(3),
    Array(4),
    Object(5),
    ;

    companion object {
        fun fromAbiValue(value: Int): JsonValueType =
            entries.firstOrNull { it.abiValue == value }
                ?: error("Unknown Windows.Data.Json.JsonValueType ABI value: $value")
    }
}