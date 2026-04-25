package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.Guid
import kotlin.Int
import kotlin.String

internal interface IJsonErrorStatics2 {
  public fun GetJsonStatus(hresult: Int): JsonErrorStatus

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.IJsonErrorStatics2"

    public val IID: Guid = Guid("404030DA-87D0-436C-83AB-FC7B12C0CC26")

    internal const val GETJSONSTATUS_METHOD_ROW_ID: Int = 9_645

    internal const val GETJSONSTATUS_SLOT: Int = 6
  }
}
