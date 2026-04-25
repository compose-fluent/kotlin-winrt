package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.UInt

internal interface IJsonArray : IJsonValue {
  public fun GetObjectAt(index: UInt): JsonObject

  public fun GetArrayAt(index: UInt): JsonArray

  public fun GetStringAt(index: UInt): String

  public fun GetNumberAt(index: UInt): Double

  public fun GetBooleanAt(index: UInt): Boolean

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonArray"

    public val IID: Guid = Guid("08C1DDB6-0CBD-4A9A-B5D3-2F852DC37E81")

    internal const val GETOBJECTAT_METHOD_ROW_ID: Int = 9_638

    internal const val GETARRAYAT_METHOD_ROW_ID: Int = 9_639

    internal const val GETSTRINGAT_METHOD_ROW_ID: Int = 9_640

    internal const val GETNUMBERAT_METHOD_ROW_ID: Int = 9_641

    internal const val GETBOOLEANAT_METHOD_ROW_ID: Int = 9_642

    internal const val GETOBJECTAT_SLOT: Int = 13

    internal const val GETARRAYAT_SLOT: Int = 14

    internal const val GETSTRINGAT_SLOT: Int = 15

    internal const val GETNUMBERAT_SLOT: Int = 16

    internal const val GETBOOLEANAT_SLOT: Int = 17
  }
}
