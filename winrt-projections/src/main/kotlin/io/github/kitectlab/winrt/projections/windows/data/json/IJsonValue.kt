package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String

public interface IJsonValue {
  public val valueType: JsonValueType

  public fun Stringify(): String

  public fun GetString(): String

  public fun GetNumber(): Double

  public fun GetBoolean(): Boolean

  public fun GetArray(): JsonArray

  public fun GetObject(): JsonObject

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonValue"

    public val IID: Guid = Guid("A3219ECB-F0B3-4DCD-BEEE-19D48CD3ED1E")

    internal const val STRINGIFY_METHOD_ROW_ID: Int = 9_662

    internal const val GETSTRING_METHOD_ROW_ID: Int = 9_663

    internal const val GETNUMBER_METHOD_ROW_ID: Int = 9_664

    internal const val GETBOOLEAN_METHOD_ROW_ID: Int = 9_665

    internal const val GETARRAY_METHOD_ROW_ID: Int = 9_666

    internal const val GETOBJECT_METHOD_ROW_ID: Int = 9_667

    internal const val VALUETYPE_GETTER_METHOD_ROW_ID: Int = 9_661

    internal const val VALUETYPE_GETTER_SLOT: Int = 6

    internal const val STRINGIFY_SLOT: Int = 7

    internal const val GETSTRING_SLOT: Int = 8

    internal const val GETNUMBER_SLOT: Int = 9

    internal const val GETBOOLEAN_SLOT: Int = 10

    internal const val GETARRAY_SLOT: Int = 11

    internal const val GETOBJECT_SLOT: Int = 12
  }
}
