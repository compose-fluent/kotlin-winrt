package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Int
import kotlin.String

internal interface IJsonValueStatics2 {
  public fun CreateNullValue(): JsonValue

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonValueStatics2"

    public val IID: Guid = Guid("1D9ECBE4-3FE8-4335-8392-93D8E36865F0")

    internal const val CREATENULLVALUE_METHOD_ROW_ID: Int = 9_673

    internal const val CREATENULLVALUE_SLOT: Int = 6
  }
}
