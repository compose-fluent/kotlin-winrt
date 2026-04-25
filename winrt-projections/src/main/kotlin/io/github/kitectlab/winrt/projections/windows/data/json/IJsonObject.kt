package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String

internal interface IJsonObject : IJsonValue {
  public fun GetNamedValue(name: String): JsonValue

  public fun SetNamedValue(name: String, `value`: IJsonValue)

  public fun GetNamedObject(name: String): JsonObject

  public fun GetNamedArray(name: String): JsonArray

  public fun GetNamedString(name: String): String

  public fun GetNamedNumber(name: String): Double

  public fun GetNamedBoolean(name: String): Boolean

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonObject"

    public val IID: Guid = Guid("064E24DD-29C2-4F83-9AC1-9EE11578BEB3")

    internal const val GETNAMEDVALUE_METHOD_ROW_ID: Int = 9_646

    internal const val SETNAMEDVALUE_METHOD_ROW_ID: Int = 9_647

    internal const val GETNAMEDOBJECT_METHOD_ROW_ID: Int = 9_648

    internal const val GETNAMEDARRAY_METHOD_ROW_ID: Int = 9_649

    internal const val GETNAMEDSTRING_METHOD_ROW_ID: Int = 9_650

    internal const val GETNAMEDNUMBER_METHOD_ROW_ID: Int = 9_651

    internal const val GETNAMEDBOOLEAN_METHOD_ROW_ID: Int = 9_652

    internal const val GETNAMEDVALUE_SLOT: Int = 13

    internal const val SETNAMEDVALUE_SLOT: Int = 14

    internal const val GETNAMEDOBJECT_SLOT: Int = 15

    internal const val GETNAMEDARRAY_SLOT: Int = 16

    internal const val GETNAMEDSTRING_SLOT: Int = 17

    internal const val GETNAMEDNUMBER_SLOT: Int = 18

    internal const val GETNAMEDBOOLEAN_SLOT: Int = 19
  }
}
