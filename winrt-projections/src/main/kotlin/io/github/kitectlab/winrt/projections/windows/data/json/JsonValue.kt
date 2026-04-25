package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.projections.windows.foundation.IStringable
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.InspectableReference
import io.github.kitectlab.winrt.runtime.PlatformAbi
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.LazyThreadSafetyMode
import kotlin.String

/**
 * WinRT sealed runtime class shell emitted as a regular Kotlin class because Kotlin sealed
 * constructors would block RCW wrapping and activation.
 */
public class JsonValue internal constructor(
  private val _inner: InspectableReference,
) : IJsonValue,
    IStringable,
    IWinRTObject {
  override val nativeObject: ComObjectReference
    get() = _inner

  private val _defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
      Metadata.acquireDefaultInterface(_inner) }

  private val _iStringable: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
      Metadata.acquireInterface(_inner, IStringable.Metadata.IID) }

  override val valueType: JsonValueType
    get() {
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocateInt32Slot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.VALUETYPE_GETTER_SLOT, arg0 = __resultOut)
        HResult(__hr).requireSuccess()
        return JsonValueType.Metadata.fromAbi(PlatformAbi.readInt32(__resultOut))
      }

    }

  override fun Stringify(): String {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.STRINGIFY_SLOT, arg0 = __resultOut)
      HResult(__hr).requireSuccess()
      return HString.fromHandle(PlatformAbi.readPointer(__resultOut), owner = true).use {
          it.toKString() }
    }

  }

  override fun GetString(): String {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETSTRING_SLOT, arg0 = __resultOut)
      HResult(__hr).requireSuccess()
      return HString.fromHandle(PlatformAbi.readPointer(__resultOut), owner = true).use {
          it.toKString() }
    }

  }

  override fun GetNumber(): Double {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocateDoubleSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETNUMBER_SLOT, arg0 = __resultOut)
      HResult(__hr).requireSuccess()
      return PlatformAbi.readDouble(__resultOut)
    }

  }

  override fun GetBoolean(): Boolean {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocateInt8Slot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETBOOLEAN_SLOT, arg0 = __resultOut)
      HResult(__hr).requireSuccess()
      return PlatformAbi.readInt8(__resultOut).toInt() != 0
    }

  }

  override fun GetArray(): JsonArray {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETARRAY_SLOT, arg0 = __resultOut)
      HResult(__hr).requireSuccess()
      val __resultRef =
          IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
      return JsonArray.Metadata.wrap(__resultRef.asInspectable())
    }

  }

  override fun GetObject(): JsonObject {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETOBJECT_SLOT, arg0 = __resultOut)
      HResult(__hr).requireSuccess()
      val __resultRef =
          IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
      return JsonObject.Metadata.wrap(__resultRef.asInspectable())
    }

  }

  override fun ToString(): String {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _iStringable.pointer, slot =
          Metadata.TOSTRING_SLOT, arg0 = __resultOut)
      HResult(__hr).requireSuccess()
      return HString.fromHandle(PlatformAbi.readPointer(__resultOut), owner = true).use {
          it.toKString() }
    }

  }

  public companion object Metadata {
    public const val TYPE_NAME: String = "Windows.Data.Json.JsonValue"

    public const val DEFAULT_INTERFACE: String = "Windows.Data.Json.IJsonValue"

    public val DEFAULT_INTERFACE_IID: Guid = Guid("A3219ECB-F0B3-4DCD-BEEE-19D48CD3ED1E")

    internal const val STRINGIFY_METHOD_ROW_ID: Int = 9_737

    internal const val GETSTRING_METHOD_ROW_ID: Int = 9_738

    internal const val GETNUMBER_METHOD_ROW_ID: Int = 9_739

    internal const val GETBOOLEAN_METHOD_ROW_ID: Int = 9_740

    internal const val GETARRAY_METHOD_ROW_ID: Int = 9_741

    internal const val GETOBJECT_METHOD_ROW_ID: Int = 9_742

    internal const val TOSTRING_METHOD_ROW_ID: Int = 9_743

    internal const val CREATENULLVALUE_METHOD_ROW_ID: Int = 9_744

    internal const val PARSE_METHOD_ROW_ID: Int = 9_745

    internal const val TRYPARSE_METHOD_ROW_ID: Int = 9_746

    internal const val CREATEBOOLEANVALUE_METHOD_ROW_ID: Int = 9_747

    internal const val CREATENUMBERVALUE_METHOD_ROW_ID: Int = 9_748

    internal const val CREATESTRINGVALUE_METHOD_ROW_ID: Int = 9_749

    internal const val VALUETYPE_GETTER_METHOD_ROW_ID: Int = 9_736

    internal const val STRINGIFY_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonValue"

    internal const val STRINGIFY_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val STRINGIFY_SLOT: Int = IJsonValue.Metadata.STRINGIFY_SLOT

    internal const val GETSTRING_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonValue"

    internal const val GETSTRING_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETSTRING_SLOT: Int = IJsonValue.Metadata.GETSTRING_SLOT

    internal const val GETNUMBER_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonValue"

    internal const val GETNUMBER_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNUMBER_SLOT: Int = IJsonValue.Metadata.GETNUMBER_SLOT

    internal const val GETBOOLEAN_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonValue"

    internal const val GETBOOLEAN_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETBOOLEAN_SLOT: Int = IJsonValue.Metadata.GETBOOLEAN_SLOT

    internal const val GETARRAY_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonValue"

    internal const val GETARRAY_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETARRAY_SLOT: Int = IJsonValue.Metadata.GETARRAY_SLOT

    internal const val GETOBJECT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonValue"

    internal const val GETOBJECT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETOBJECT_SLOT: Int = IJsonValue.Metadata.GETOBJECT_SLOT

    internal const val TOSTRING_SLOT_OWNER_INTERFACE: String = "Windows.Foundation.IStringable"

    internal const val TOSTRING_SLOT_OWNER_CACHE: String = "_iStringable"

    internal val TOSTRING_SLOT: Int = IStringable.Metadata.TOSTRING_SLOT

    internal const val VALUETYPE_GETTER_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonValue"

    internal const val VALUETYPE_GETTER_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val VALUETYPE_GETTER_SLOT: Int = IJsonValue.Metadata.VALUETYPE_GETTER_SLOT

    internal const val STATIC_CREATENULLVALUE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonValueStatics2"

    internal const val STATIC_CREATENULLVALUE_SLOT_OWNER_ACCESSOR: String = "iJsonValueStatics2"

    internal const val STATIC_CREATENULLVALUE_SLOT_OWNER_CACHE: String = "_iJsonValueStatics2"

    internal val STATIC_CREATENULLVALUE_SLOT: Int = IJsonValueStatics2.Metadata.CREATENULLVALUE_SLOT

    internal const val STATIC_PARSE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonValueStatics"

    internal const val STATIC_PARSE_SLOT_OWNER_ACCESSOR: String = "iJsonValueStatics"

    internal const val STATIC_PARSE_SLOT_OWNER_CACHE: String = "_iJsonValueStatics"

    internal val STATIC_PARSE_SLOT: Int = IJsonValueStatics.Metadata.PARSE_SLOT

    internal const val STATIC_TRYPARSE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonValueStatics"

    internal const val STATIC_TRYPARSE_SLOT_OWNER_ACCESSOR: String = "iJsonValueStatics"

    internal const val STATIC_TRYPARSE_SLOT_OWNER_CACHE: String = "_iJsonValueStatics"

    internal val STATIC_TRYPARSE_SLOT: Int = IJsonValueStatics.Metadata.TRYPARSE_SLOT

    internal const val STATIC_CREATEBOOLEANVALUE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonValueStatics"

    internal const val STATIC_CREATEBOOLEANVALUE_SLOT_OWNER_ACCESSOR: String = "iJsonValueStatics"

    internal const val STATIC_CREATEBOOLEANVALUE_SLOT_OWNER_CACHE: String = "_iJsonValueStatics"

    internal val STATIC_CREATEBOOLEANVALUE_SLOT: Int =
        IJsonValueStatics.Metadata.CREATEBOOLEANVALUE_SLOT

    internal const val STATIC_CREATENUMBERVALUE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonValueStatics"

    internal const val STATIC_CREATENUMBERVALUE_SLOT_OWNER_ACCESSOR: String = "iJsonValueStatics"

    internal const val STATIC_CREATENUMBERVALUE_SLOT_OWNER_CACHE: String = "_iJsonValueStatics"

    internal val STATIC_CREATENUMBERVALUE_SLOT: Int =
        IJsonValueStatics.Metadata.CREATENUMBERVALUE_SLOT

    internal const val STATIC_CREATESTRINGVALUE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonValueStatics"

    internal const val STATIC_CREATESTRINGVALUE_SLOT_OWNER_ACCESSOR: String = "iJsonValueStatics"

    internal const val STATIC_CREATESTRINGVALUE_SLOT_OWNER_CACHE: String = "_iJsonValueStatics"

    internal val STATIC_CREATESTRINGVALUE_SLOT: Int =
        IJsonValueStatics.Metadata.CREATESTRINGVALUE_SLOT

    internal fun acquireInterface(instance: InspectableReference, iid: Guid): IUnknownReference =
        instance.queryInterface(iid).getOrThrow().use { IUnknownReference(it.getRefPointer(), iid) }

    public fun acquireDefaultInterface(instance: InspectableReference): IUnknownReference =
        acquireInterface(instance, DEFAULT_INTERFACE_IID)

    internal fun wrap(instance: InspectableReference): JsonValue = JsonValue(instance)

    public fun CreateNullValue(): JsonValue {
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance =
            StaticInterfaces.iJsonValueStatics2().pointer, slot = STATIC_CREATENULLVALUE_SLOT, arg0
            = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonValue.Metadata.wrap(__resultRef.asInspectable())
      }

    }

    public fun Parse(input: String): JsonValue {
      HString.create(input).use { __inputAbi ->
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance =
              StaticInterfaces.iJsonValueStatics().pointer, slot = STATIC_PARSE_SLOT, arg0 =
              __inputAbi.handle, arg1 = __resultOut)
          HResult(__hr).requireSuccess()
          val __resultRef =
              IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
          return JsonValue.Metadata.wrap(__resultRef.asInspectable())
        }
      }

    }

    public fun TryParse(input: String, result: JsonValue): Boolean {
      HString.create(input).use { __inputAbi ->
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocateInt8Slot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance =
              StaticInterfaces.iJsonValueStatics().pointer, slot = STATIC_TRYPARSE_SLOT, arg0 =
              __inputAbi.handle, arg1 = PlatformAbi.fromRawComPtr((result as
              IWinRTObject).nativeObject.pointer), arg2 = __resultOut)
          HResult(__hr).requireSuccess()
          return PlatformAbi.readInt8(__resultOut).toInt() != 0
        }
      }

    }

    public fun CreateBooleanValue(input: Boolean): JsonValue {
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance =
            StaticInterfaces.iJsonValueStatics().pointer, slot = STATIC_CREATEBOOLEANVALUE_SLOT,
            arg0 = if (input) 1 else 0, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonValue.Metadata.wrap(__resultRef.asInspectable())
      }

    }

    public fun CreateNumberValue(input: Double): JsonValue {
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance =
            StaticInterfaces.iJsonValueStatics().pointer, slot = STATIC_CREATENUMBERVALUE_SLOT, arg0
            = input, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonValue.Metadata.wrap(__resultRef.asInspectable())
      }

    }

    public fun CreateStringValue(input: String): JsonValue {
      HString.create(input).use { __inputAbi ->
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance =
              StaticInterfaces.iJsonValueStatics().pointer, slot = STATIC_CREATESTRINGVALUE_SLOT,
              arg0 = __inputAbi.handle, arg1 = __resultOut)
          HResult(__hr).requireSuccess()
          val __resultRef =
              IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
          return JsonValue.Metadata.wrap(__resultRef.asInspectable())
        }
      }

    }
  }

  public object StaticInterfaces {
    public const val IJSONVALUESTATICS: String = "Windows.Data.Json.IJsonValueStatics"

    public val IJSONVALUESTATICS_IID: Guid = Guid("5F6B544A-2F53-48E1-91A3-F78B50A6345C")

    private val _iJsonValueStatics: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ActivationFactory.get(Metadata.TYPE_NAME, IJSONVALUESTATICS_IID) }

    public const val IJSONVALUESTATICS2: String = "Windows.Data.Json.IJsonValueStatics2"

    public val IJSONVALUESTATICS2_IID: Guid = Guid("1D9ECBE4-3FE8-4335-8392-93D8E36865F0")

    private val _iJsonValueStatics2: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ActivationFactory.get(Metadata.TYPE_NAME, IJSONVALUESTATICS2_IID) }

    public fun iJsonValueStatics(): IUnknownReference = _iJsonValueStatics

    public fun iJsonValueStatics2(): IUnknownReference = _iJsonValueStatics2
  }
}
