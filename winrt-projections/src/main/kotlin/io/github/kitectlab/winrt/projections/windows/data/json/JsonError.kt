package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.IUnknownReference
import kotlin.Int
import kotlin.LazyThreadSafetyMode
import kotlin.String
import kotlin.Suppress

/**
 * static WinRT class shell
 */
@Suppress("ClassName")
public class JsonError {
  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.JsonError"

    internal const val GETJSONSTATUS_METHOD_ROW_ID: Int = 9_703

    internal const val STATIC_GETJSONSTATUS_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonErrorStatics2"

    internal const val STATIC_GETJSONSTATUS_SLOT_OWNER_ACCESSOR: String = "iJsonErrorStatics2"

    internal const val STATIC_GETJSONSTATUS_SLOT_OWNER_CACHE: String = "_iJsonErrorStatics2"

    internal val STATIC_GETJSONSTATUS_SLOT: Int = IJsonErrorStatics2.Metadata.GETJSONSTATUS_SLOT
  }

  public object StaticInterfaces {
    public const val IJSONERRORSTATICS2: String = "Windows.Data.Json.IJsonErrorStatics2"

    public val IJSONERRORSTATICS2_IID: Guid = Guid("404030DA-87D0-436C-83AB-FC7B12C0CC26")

    private val _iJsonErrorStatics2: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ActivationFactory.get(Metadata.TYPE_NAME, IJSONERRORSTATICS2_IID) }

    public fun iJsonErrorStatics2(): IUnknownReference = _iJsonErrorStatics2
  }
}
