package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Boolean
import kotlin.Int
import kotlin.String

internal interface IJsonObjectStatics {
  public fun Parse(input: String): JsonObject

  public fun TryParse(input: String, result: JsonObject): Boolean

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonObjectStatics"

    public val IID: Guid = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0")

    internal const val PARSE_METHOD_ROW_ID: Int = 9_653

    internal const val TRYPARSE_METHOD_ROW_ID: Int = 9_654

    internal const val PARSE_SLOT: Int = 6

    internal const val TRYPARSE_SLOT: Int = 7
  }
}
