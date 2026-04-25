package io.github.kitectlab.winrt.projections.windows.foundation

import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.PlatformAbi
import kotlin.Int
import kotlin.String

public interface IStringable {
  public fun ToString(): String

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Foundation.IStringable"

    public val IID: Guid = Guid("96369F54-8EB6-48F0-ABCE-C1B211E627C3")

    internal const val TOSTRING_METHOD_ROW_ID: Int = 20_121

    internal const val TOSTRING_SLOT: Int = 6

    internal fun wrap(instance: IUnknownReference): IStringable = object : IStringable, IWinRTObject
        {
      override val nativeObject: ComObjectReference
        get() = instance
      override fun ToString(): String {
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance = nativeObject.pointer, slot =
              Metadata.TOSTRING_SLOT, arg0 = __resultOut)
          HResult(__hr).requireSuccess()
          return HString.fromHandle(PlatformAbi.readPointer(__resultOut), owner = true).use {
              it.toKString() }
        }

      }

    }
  }
}
