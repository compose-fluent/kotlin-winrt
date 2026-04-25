package io.github.kitectlab.winrt.projections.windows.`data`.json

import io.github.kitectlab.winrt.projections.windows.foundation.IStringable
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.InspectableReference
import io.github.kitectlab.winrt.runtime.PlatformAbi
import kotlin.Array
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.LazyThreadSafetyMode
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlin.collections.Iterator
import kotlin.collections.List

/**
 * WinRT sealed runtime class shell emitted as a regular Kotlin class because Kotlin sealed
 * constructors would block RCW wrapping and activation.
 */
public class JsonArray internal constructor(
  private val _inner: InspectableReference,
) : IJsonArray,
    IJsonValue,
    IStringable,
    IWinRTObject {
  override val nativeObject: ComObjectReference
    get() = _inner

  private val _defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
      Metadata.acquireDefaultInterface(_inner) }

  private val _iJsonValue: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
      Metadata.acquireInterface(_inner, IJsonValue.Metadata.IID) }

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

  public val size: UInt
    get() = error("Not yet bound to winrt-runtime")

  public constructor() :
      this(io.github.kitectlab.winrt.runtime.ActivationFactory.activateInstance(Metadata.TYPE_NAME))

  override fun GetObjectAt(index: UInt): JsonObject {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETOBJECTAT_SLOT, arg0 = index.toInt(), arg1 = __resultOut)
      HResult(__hr).requireSuccess()
      val __resultRef =
          IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
      return JsonObject.Metadata.wrap(__resultRef.asInspectable())
    }

  }

  override fun GetArrayAt(index: UInt): JsonArray {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETARRAYAT_SLOT, arg0 = index.toInt(), arg1 = __resultOut)
      HResult(__hr).requireSuccess()
      val __resultRef =
          IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
      return JsonArray.Metadata.wrap(__resultRef.asInspectable())
    }

  }

  override fun GetStringAt(index: UInt): String {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETSTRINGAT_SLOT, arg0 = index.toInt(), arg1 = __resultOut)
      HResult(__hr).requireSuccess()
      return HString.fromHandle(PlatformAbi.readPointer(__resultOut), owner = true).use {
          it.toKString() }
    }

  }

  override fun GetNumberAt(index: UInt): Double {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocateDoubleSlot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETNUMBERAT_SLOT, arg0 = index.toInt(), arg1 = __resultOut)
      HResult(__hr).requireSuccess()
      return PlatformAbi.readDouble(__resultOut)
    }

  }

  override fun GetBooleanAt(index: UInt): Boolean {
    return PlatformAbi.confinedScope().use { __scope ->
      val __resultOut = PlatformAbi.allocateInt8Slot(__scope)
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.GETBOOLEANAT_SLOT, arg0 = index.toInt(), arg1 = __resultOut)
      HResult(__hr).requireSuccess()
      return PlatformAbi.readInt8(__resultOut).toInt() != 0
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

  public fun GetAt(index: UInt): IJsonValue = error("Not yet bound to winrt-runtime")

  public fun GetView(): List<IJsonValue> = error("Not yet bound to winrt-runtime")

  public fun IndexOf(`value`: IJsonValue, index: UInt): Boolean =
      error("Not yet bound to winrt-runtime")

  public fun SetAt(index: UInt, `value`: IJsonValue): Unit = error("Not yet bound to winrt-runtime")

  public fun InsertAt(index: UInt, `value`: IJsonValue): Unit =
      error("Not yet bound to winrt-runtime")

  public fun RemoveAt(index: UInt): Unit = error("Not yet bound to winrt-runtime")

  public fun Append(`value`: IJsonValue): Unit = error("Not yet bound to winrt-runtime")

  public fun RemoveAtEnd(): Unit = error("Not yet bound to winrt-runtime")

  public fun Clear(): Unit = error("Not yet bound to winrt-runtime")

  public fun GetMany(startIndex: UInt, items: Array<IJsonValue>): UInt =
      error("Not yet bound to winrt-runtime")

  public fun ReplaceAll(items: Array<IJsonValue>): Unit = error("Not yet bound to winrt-runtime")

  public fun First(): Iterator<IJsonValue> = error("Not yet bound to winrt-runtime")

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
    public const val TYPE_NAME: String = "Windows.Data.Json.JsonArray"

    public const val DEFAULT_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    public val DEFAULT_INTERFACE_IID: Guid = Guid("08C1DDB6-0CBD-4A9A-B5D3-2F852DC37E81")

    internal const val GETOBJECTAT_METHOD_ROW_ID: Int = 9_675

    internal const val GETARRAYAT_METHOD_ROW_ID: Int = 9_676

    internal const val GETSTRINGAT_METHOD_ROW_ID: Int = 9_677

    internal const val GETNUMBERAT_METHOD_ROW_ID: Int = 9_678

    internal const val GETBOOLEANAT_METHOD_ROW_ID: Int = 9_679

    internal const val STRINGIFY_METHOD_ROW_ID: Int = 9_681

    internal const val GETSTRING_METHOD_ROW_ID: Int = 9_682

    internal const val GETNUMBER_METHOD_ROW_ID: Int = 9_683

    internal const val GETBOOLEAN_METHOD_ROW_ID: Int = 9_684

    internal const val GETARRAY_METHOD_ROW_ID: Int = 9_685

    internal const val GETOBJECT_METHOD_ROW_ID: Int = 9_686

    internal const val GETAT_METHOD_ROW_ID: Int = 9_687

    internal const val GETVIEW_METHOD_ROW_ID: Int = 9_689

    internal const val INDEXOF_METHOD_ROW_ID: Int = 9_690

    internal const val SETAT_METHOD_ROW_ID: Int = 9_691

    internal const val INSERTAT_METHOD_ROW_ID: Int = 9_692

    internal const val REMOVEAT_METHOD_ROW_ID: Int = 9_693

    internal const val APPEND_METHOD_ROW_ID: Int = 9_694

    internal const val REMOVEATEND_METHOD_ROW_ID: Int = 9_695

    internal const val CLEAR_METHOD_ROW_ID: Int = 9_696

    internal const val GETMANY_METHOD_ROW_ID: Int = 9_697

    internal const val REPLACEALL_METHOD_ROW_ID: Int = 9_698

    internal const val FIRST_METHOD_ROW_ID: Int = 9_699

    internal const val TOSTRING_METHOD_ROW_ID: Int = 9_700

    internal const val PARSE_METHOD_ROW_ID: Int = 9_701

    internal const val TRYPARSE_METHOD_ROW_ID: Int = 9_702

    internal const val VALUETYPE_GETTER_METHOD_ROW_ID: Int = 9_680

    internal const val SIZE_GETTER_METHOD_ROW_ID: Int = 9_688

    internal const val GETOBJECTAT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETOBJECTAT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETOBJECTAT_SLOT: Int = IJsonArray.Metadata.GETOBJECTAT_SLOT

    internal const val GETARRAYAT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETARRAYAT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETARRAYAT_SLOT: Int = IJsonArray.Metadata.GETARRAYAT_SLOT

    internal const val GETSTRINGAT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETSTRINGAT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETSTRINGAT_SLOT: Int = IJsonArray.Metadata.GETSTRINGAT_SLOT

    internal const val GETNUMBERAT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETNUMBERAT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNUMBERAT_SLOT: Int = IJsonArray.Metadata.GETNUMBERAT_SLOT

    internal const val GETBOOLEANAT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETBOOLEANAT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETBOOLEANAT_SLOT: Int = IJsonArray.Metadata.GETBOOLEANAT_SLOT

    internal const val STRINGIFY_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val STRINGIFY_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val STRINGIFY_SLOT: Int = IJsonValue.Metadata.STRINGIFY_SLOT

    internal const val GETSTRING_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETSTRING_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETSTRING_SLOT: Int = IJsonValue.Metadata.GETSTRING_SLOT

    internal const val GETNUMBER_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETNUMBER_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNUMBER_SLOT: Int = IJsonValue.Metadata.GETNUMBER_SLOT

    internal const val GETBOOLEAN_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETBOOLEAN_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETBOOLEAN_SLOT: Int = IJsonValue.Metadata.GETBOOLEAN_SLOT

    internal const val GETARRAY_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETARRAY_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETARRAY_SLOT: Int = IJsonValue.Metadata.GETARRAY_SLOT

    internal const val GETOBJECT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonArray"

    internal const val GETOBJECT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETOBJECT_SLOT: Int = IJsonValue.Metadata.GETOBJECT_SLOT

    internal const val TOSTRING_SLOT_OWNER_INTERFACE: String = "Windows.Foundation.IStringable"

    internal const val TOSTRING_SLOT_OWNER_CACHE: String = "_iStringable"

    internal val TOSTRING_SLOT: Int = IStringable.Metadata.TOSTRING_SLOT

    internal const val VALUETYPE_GETTER_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonArray"

    internal const val VALUETYPE_GETTER_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val VALUETYPE_GETTER_SLOT: Int = IJsonValue.Metadata.VALUETYPE_GETTER_SLOT

    internal const val STATIC_PARSE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonArrayStatics"

    internal const val STATIC_PARSE_SLOT_OWNER_ACCESSOR: String = "iJsonArrayStatics"

    internal const val STATIC_PARSE_SLOT_OWNER_CACHE: String = "_iJsonArrayStatics"

    internal val STATIC_PARSE_SLOT: Int = IJsonArrayStatics.Metadata.PARSE_SLOT

    internal const val STATIC_TRYPARSE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonArrayStatics"

    internal const val STATIC_TRYPARSE_SLOT_OWNER_ACCESSOR: String = "iJsonArrayStatics"

    internal const val STATIC_TRYPARSE_SLOT_OWNER_CACHE: String = "_iJsonArrayStatics"

    internal val STATIC_TRYPARSE_SLOT: Int = IJsonArrayStatics.Metadata.TRYPARSE_SLOT

    internal fun acquireInterface(instance: InspectableReference, iid: Guid): IUnknownReference =
        instance.queryInterface(iid).getOrThrow().use { IUnknownReference(it.getRefPointer(), iid) }

    public fun acquireDefaultInterface(instance: InspectableReference): IUnknownReference =
        acquireInterface(instance, DEFAULT_INTERFACE_IID)

    internal fun wrap(instance: InspectableReference): JsonArray = JsonArray(instance)

    public fun Parse(input: String): JsonArray {
      HString.create(input).use { __inputAbi ->
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance =
              StaticInterfaces.iJsonArrayStatics().pointer, slot = STATIC_PARSE_SLOT, arg0 =
              __inputAbi.handle, arg1 = __resultOut)
          HResult(__hr).requireSuccess()
          val __resultRef =
              IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
          return JsonArray.Metadata.wrap(__resultRef.asInspectable())
        }
      }

    }

    public fun TryParse(input: String, result: JsonArray): Boolean {
      HString.create(input).use { __inputAbi ->
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocateInt8Slot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance =
              StaticInterfaces.iJsonArrayStatics().pointer, slot = STATIC_TRYPARSE_SLOT, arg0 =
              __inputAbi.handle, arg1 = PlatformAbi.fromRawComPtr((result as
              IWinRTObject).nativeObject.pointer), arg2 = __resultOut)
          HResult(__hr).requireSuccess()
          return PlatformAbi.readInt8(__resultOut).toInt() != 0
        }
      }

    }
  }

  public object ActivationFactory {
    public const val RUNTIME_CLASS: String = "Windows.Data.Json.JsonArray"

    public const val FACTORY_INTERFACE: String = "Any"

    public fun acquire(): IUnknownReference =
        io.github.kitectlab.winrt.runtime.ActivationFactory.get(RUNTIME_CLASS)

    public fun activate(): InspectableReference =
        io.github.kitectlab.winrt.runtime.ActivationFactory.activateInstance(RUNTIME_CLASS)
  }

  public object StaticInterfaces {
    public const val IJSONARRAYSTATICS: String = "Windows.Data.Json.IJsonArrayStatics"

    public val IJSONARRAYSTATICS_IID: Guid = Guid("DB1434A9-E164-499F-93E2-8A8F49BB90BA")

    private val _iJsonArrayStatics: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
        io.github.kitectlab.winrt.runtime.ActivationFactory.get(Metadata.TYPE_NAME,
        IJSONARRAYSTATICS_IID) }

    public fun iJsonArrayStatics(): IUnknownReference = _iJsonArrayStatics
  }
}
