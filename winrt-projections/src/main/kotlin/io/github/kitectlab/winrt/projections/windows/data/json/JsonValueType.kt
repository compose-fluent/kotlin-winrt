package io.github.kitectlab.winrt.projections.windows.`data`.json

import kotlin.Int

public enum class JsonValueType(
  internal val abiValue: Int,
) {
  Null(0),
  Boolean(1),
  Number(2),
  String(3),
  Array(4),
  Object(5),
  ;

  public companion object Metadata {
    internal fun fromAbi(`value`: Int): JsonValueType = JsonValueType.entries.firstOrNull {
        it.abiValue == value } ?:
        error("Unknown Windows.Data.Json.JsonValueType ABI value: ${'$'}value")

    internal fun toAbi(`value`: JsonValueType): Int = value.abiValue
  }
}
