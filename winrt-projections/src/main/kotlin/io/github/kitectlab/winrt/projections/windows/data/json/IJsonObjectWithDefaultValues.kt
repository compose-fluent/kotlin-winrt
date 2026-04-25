package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String

internal interface IJsonObjectWithDefaultValues : IJsonObject, IJsonValue {
  public fun GetNamedValue(name: String, defaultValue: JsonValue): JsonValue

  public fun GetNamedObject(name: String, defaultValue: JsonObject): JsonObject

  public fun GetNamedString(name: String, defaultValue: String): String

  public fun GetNamedArray(name: String, defaultValue: JsonArray): JsonArray

  public fun GetNamedNumber(name: String, defaultValue: Double): Double

  public fun GetNamedBoolean(name: String, defaultValue: Boolean): Boolean

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonObjectWithDefaultValues"

    public val IID: Guid = Guid("D960D2A2-B7F0-4F00-8E44-D82CF415EA13")

    internal const val GETNAMEDVALUE_METHOD_ROW_ID: Int = 9_655

    internal const val GETNAMEDOBJECT_METHOD_ROW_ID: Int = 9_656

    internal const val GETNAMEDSTRING_METHOD_ROW_ID: Int = 9_657

    internal const val GETNAMEDARRAY_METHOD_ROW_ID: Int = 9_658

    internal const val GETNAMEDNUMBER_METHOD_ROW_ID: Int = 9_659

    internal const val GETNAMEDBOOLEAN_METHOD_ROW_ID: Int = 9_660

    internal const val GETNAMEDVALUE_SLOT: Int = 27

    internal const val GETNAMEDOBJECT_SLOT: Int = 28

    internal const val GETNAMEDSTRING_SLOT: Int = 29

    internal const val GETNAMEDARRAY_SLOT: Int = 30

    internal const val GETNAMEDNUMBER_SLOT: Int = 31

    internal const val GETNAMEDBOOLEAN_SLOT: Int = 32
  }
}
