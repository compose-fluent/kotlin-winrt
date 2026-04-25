package io.github.kitectlab.winrt.projections.windows.`data`.json

import kotlin.Int

public enum class JsonErrorStatus(
  internal val abiValue: Int,
) {
  Unknown(0),
  InvalidJsonString(1),
  InvalidJsonNumber(2),
  JsonValueNotFound(3),
  ImplementationLimit(4),
  ;

  public companion object Metadata {
    internal fun fromAbi(`value`: Int): JsonErrorStatus = JsonErrorStatus.entries.firstOrNull {
        it.abiValue == value } ?:
        error("Unknown Windows.Data.Json.JsonErrorStatus ABI value: ${'$'}value")

    internal fun toAbi(`value`: JsonErrorStatus): Int = value.abiValue
  }
}
