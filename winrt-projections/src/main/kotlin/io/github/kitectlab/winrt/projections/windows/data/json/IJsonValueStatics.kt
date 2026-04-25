package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String

internal interface IJsonValueStatics {
  public fun Parse(input: String): JsonValue

  public fun TryParse(input: String, result: JsonValue): Boolean

  public fun CreateBooleanValue(input: Boolean): JsonValue

  public fun CreateNumberValue(input: Double): JsonValue

  public fun CreateStringValue(input: String): JsonValue

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonValueStatics"

    public val IID: Guid = Guid("5F6B544A-2F53-48E1-91A3-F78B50A6345C")

    internal const val PARSE_METHOD_ROW_ID: Int = 9_668

    internal const val TRYPARSE_METHOD_ROW_ID: Int = 9_669

    internal const val CREATEBOOLEANVALUE_METHOD_ROW_ID: Int = 9_670

    internal const val CREATENUMBERVALUE_METHOD_ROW_ID: Int = 9_671

    internal const val CREATESTRINGVALUE_METHOD_ROW_ID: Int = 9_672

    internal const val PARSE_SLOT: Int = 6

    internal const val TRYPARSE_SLOT: Int = 7

    internal const val CREATEBOOLEANVALUE_SLOT: Int = 8

    internal const val CREATENUMBERVALUE_SLOT: Int = 9

    internal const val CREATESTRINGVALUE_SLOT: Int = 10
  }
}
