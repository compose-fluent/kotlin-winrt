package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Boolean
import kotlin.Int
import kotlin.String

internal interface IJsonArrayStatics {
  public fun Parse(input: String): JsonArray

  public fun TryParse(input: String, result: JsonArray): Boolean

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonArrayStatics"

    public val IID: Guid = Guid("DB1434A9-E164-499F-93E2-8A8F49BB90BA")

    internal const val PARSE_METHOD_ROW_ID: Int = 9_643

    internal const val TRYPARSE_METHOD_ROW_ID: Int = 9_644

    internal const val PARSE_SLOT: Int = 6

    internal const val TRYPARSE_SLOT: Int = 7
  }
}
