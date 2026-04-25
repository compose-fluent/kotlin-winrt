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
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.LazyThreadSafetyMode
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlin.collections.Iterator
import kotlin.collections.Map

/**
 * WinRT sealed runtime class shell emitted as a regular Kotlin class because Kotlin sealed
 * constructors would block RCW wrapping and activation.
 */
public class JsonObject internal constructor(
  private val _inner: InspectableReference,
) : IJsonObject,
    IJsonObjectWithDefaultValues,
    IJsonValue,
    IStringable,
    IWinRTObject {
  override val nativeObject: ComObjectReference
    get() = _inner

  private val _defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
      Metadata.acquireDefaultInterface(_inner) }

  private val _iJsonObjectWithDefaultValues: IUnknownReference by
      lazy(LazyThreadSafetyMode.PUBLICATION) { Metadata.acquireInterface(_inner,
      IJsonObjectWithDefaultValues.Metadata.IID) }

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

  override fun GetNamedValue(name: String): JsonValue {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDVALUE_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonValue.Metadata.wrap(__resultRef.asInspectable())
      }
    }

  }

  override fun SetNamedValue(name: String, `value`: IJsonValue) {
    HString.create(name).use { __nameAbi ->
      val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
          Metadata.SETNAMEDVALUE_SLOT, arg0 = __nameAbi.handle, arg1 =
          PlatformAbi.fromRawComPtr((value as IWinRTObject).nativeObject.pointer))
      HResult(__hr).requireSuccess()
    }

  }

  override fun GetNamedObject(name: String): JsonObject {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDOBJECT_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonObject.Metadata.wrap(__resultRef.asInspectable())
      }
    }

  }

  override fun GetNamedArray(name: String): JsonArray {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDARRAY_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonArray.Metadata.wrap(__resultRef.asInspectable())
      }
    }

  }

  override fun GetNamedString(name: String): String {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDSTRING_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        return HString.fromHandle(PlatformAbi.readPointer(__resultOut), owner = true).use {
            it.toKString() }
      }
    }

  }

  override fun GetNamedNumber(name: String): Double {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocateDoubleSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDNUMBER_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        return PlatformAbi.readDouble(__resultOut)
      }
    }

  }

  override fun GetNamedBoolean(name: String): Boolean {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocateInt8Slot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDBOOLEAN_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        return PlatformAbi.readInt8(__resultOut).toInt() != 0
      }
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

  public fun Lookup(key: String): IJsonValue = error("Not yet bound to winrt-runtime")

  public fun HasKey(key: String): Boolean = error("Not yet bound to winrt-runtime")

  public fun GetView(): Map<String, IJsonValue> = error("Not yet bound to winrt-runtime")

  public fun Insert(key: String, `value`: IJsonValue): Boolean =
      error("Not yet bound to winrt-runtime")

  public fun Remove(key: String): Unit = error("Not yet bound to winrt-runtime")

  public fun Clear(): Unit = error("Not yet bound to winrt-runtime")

  public fun First(): Iterator<Map.Entry<String, IJsonValue>> =
      error("Not yet bound to winrt-runtime")

  override fun GetNamedValue(name: String, defaultValue: JsonValue): JsonValue {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDVALUE_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonValue.Metadata.wrap(__resultRef.asInspectable())
      }
    }

  }

  override fun GetNamedObject(name: String, defaultValue: JsonObject): JsonObject {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDOBJECT_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonObject.Metadata.wrap(__resultRef.asInspectable())
      }
    }

  }

  override fun GetNamedString(name: String, defaultValue: String): String {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDSTRING_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        return HString.fromHandle(PlatformAbi.readPointer(__resultOut), owner = true).use {
            it.toKString() }
      }
    }

  }

  override fun GetNamedArray(name: String, defaultValue: JsonArray): JsonArray {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDARRAY_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        val __resultRef =
            IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
        return JsonArray.Metadata.wrap(__resultRef.asInspectable())
      }
    }

  }

  override fun GetNamedNumber(name: String, defaultValue: Double): Double {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocateDoubleSlot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDNUMBER_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        return PlatformAbi.readDouble(__resultOut)
      }
    }

  }

  override fun GetNamedBoolean(name: String, defaultValue: Boolean): Boolean {
    HString.create(name).use { __nameAbi ->
      return PlatformAbi.confinedScope().use { __scope ->
        val __resultOut = PlatformAbi.allocateInt8Slot(__scope)
        val __hr = ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer, slot =
            Metadata.GETNAMEDBOOLEAN_SLOT, arg0 = __nameAbi.handle, arg1 = __resultOut)
        HResult(__hr).requireSuccess()
        return PlatformAbi.readInt8(__resultOut).toInt() != 0
      }
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
    public const val TYPE_NAME: String = "Windows.Data.Json.JsonObject"

    public const val DEFAULT_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    public val DEFAULT_INTERFACE_IID: Guid = Guid("064E24DD-29C2-4F83-9AC1-9EE11578BEB3")

    internal const val GETNAMEDVALUE_9705_METHOD_ROW_ID: Int = 9_705

    internal const val SETNAMEDVALUE_METHOD_ROW_ID: Int = 9_706

    internal const val GETNAMEDOBJECT_9707_METHOD_ROW_ID: Int = 9_707

    internal const val GETNAMEDARRAY_9708_METHOD_ROW_ID: Int = 9_708

    internal const val GETNAMEDSTRING_9709_METHOD_ROW_ID: Int = 9_709

    internal const val GETNAMEDNUMBER_9710_METHOD_ROW_ID: Int = 9_710

    internal const val GETNAMEDBOOLEAN_9711_METHOD_ROW_ID: Int = 9_711

    internal const val STRINGIFY_METHOD_ROW_ID: Int = 9_713

    internal const val GETSTRING_METHOD_ROW_ID: Int = 9_714

    internal const val GETNUMBER_METHOD_ROW_ID: Int = 9_715

    internal const val GETBOOLEAN_METHOD_ROW_ID: Int = 9_716

    internal const val GETARRAY_METHOD_ROW_ID: Int = 9_717

    internal const val GETOBJECT_METHOD_ROW_ID: Int = 9_718

    internal const val LOOKUP_METHOD_ROW_ID: Int = 9_719

    internal const val HASKEY_METHOD_ROW_ID: Int = 9_721

    internal const val GETVIEW_METHOD_ROW_ID: Int = 9_722

    internal const val INSERT_METHOD_ROW_ID: Int = 9_723

    internal const val REMOVE_METHOD_ROW_ID: Int = 9_724

    internal const val CLEAR_METHOD_ROW_ID: Int = 9_725

    internal const val FIRST_METHOD_ROW_ID: Int = 9_726

    internal const val GETNAMEDVALUE_9727_METHOD_ROW_ID: Int = 9_727

    internal const val GETNAMEDOBJECT_9728_METHOD_ROW_ID: Int = 9_728

    internal const val GETNAMEDSTRING_9729_METHOD_ROW_ID: Int = 9_729

    internal const val GETNAMEDARRAY_9730_METHOD_ROW_ID: Int = 9_730

    internal const val GETNAMEDNUMBER_9731_METHOD_ROW_ID: Int = 9_731

    internal const val GETNAMEDBOOLEAN_9732_METHOD_ROW_ID: Int = 9_732

    internal const val TOSTRING_METHOD_ROW_ID: Int = 9_733

    internal const val PARSE_METHOD_ROW_ID: Int = 9_734

    internal const val TRYPARSE_METHOD_ROW_ID: Int = 9_735

    internal const val VALUETYPE_GETTER_METHOD_ROW_ID: Int = 9_712

    internal const val SIZE_GETTER_METHOD_ROW_ID: Int = 9_720

    internal const val GETNAMEDVALUE_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETNAMEDVALUE_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNAMEDVALUE_SLOT: Int = IJsonObject.Metadata.GETNAMEDVALUE_SLOT

    internal const val SETNAMEDVALUE_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val SETNAMEDVALUE_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val SETNAMEDVALUE_SLOT: Int = IJsonObject.Metadata.SETNAMEDVALUE_SLOT

    internal const val GETNAMEDOBJECT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETNAMEDOBJECT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNAMEDOBJECT_SLOT: Int = IJsonObject.Metadata.GETNAMEDOBJECT_SLOT

    internal const val GETNAMEDARRAY_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETNAMEDARRAY_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNAMEDARRAY_SLOT: Int = IJsonObject.Metadata.GETNAMEDARRAY_SLOT

    internal const val GETNAMEDSTRING_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETNAMEDSTRING_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNAMEDSTRING_SLOT: Int = IJsonObject.Metadata.GETNAMEDSTRING_SLOT

    internal const val GETNAMEDNUMBER_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETNAMEDNUMBER_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNAMEDNUMBER_SLOT: Int = IJsonObject.Metadata.GETNAMEDNUMBER_SLOT

    internal const val GETNAMEDBOOLEAN_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonObject"

    internal const val GETNAMEDBOOLEAN_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNAMEDBOOLEAN_SLOT: Int = IJsonObject.Metadata.GETNAMEDBOOLEAN_SLOT

    internal const val STRINGIFY_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val STRINGIFY_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val STRINGIFY_SLOT: Int = IJsonValue.Metadata.STRINGIFY_SLOT

    internal const val GETSTRING_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETSTRING_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETSTRING_SLOT: Int = IJsonValue.Metadata.GETSTRING_SLOT

    internal const val GETNUMBER_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETNUMBER_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETNUMBER_SLOT: Int = IJsonValue.Metadata.GETNUMBER_SLOT

    internal const val GETBOOLEAN_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETBOOLEAN_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETBOOLEAN_SLOT: Int = IJsonValue.Metadata.GETBOOLEAN_SLOT

    internal const val GETARRAY_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETARRAY_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETARRAY_SLOT: Int = IJsonValue.Metadata.GETARRAY_SLOT

    internal const val GETOBJECT_SLOT_OWNER_INTERFACE: String = "Windows.Data.Json.IJsonObject"

    internal const val GETOBJECT_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val GETOBJECT_SLOT: Int = IJsonValue.Metadata.GETOBJECT_SLOT

    internal const val TOSTRING_SLOT_OWNER_INTERFACE: String = "Windows.Foundation.IStringable"

    internal const val TOSTRING_SLOT_OWNER_CACHE: String = "_iStringable"

    internal val TOSTRING_SLOT: Int = IStringable.Metadata.TOSTRING_SLOT

    internal const val VALUETYPE_GETTER_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonObject"

    internal const val VALUETYPE_GETTER_SLOT_OWNER_CACHE: String = "_defaultInterface"

    internal val VALUETYPE_GETTER_SLOT: Int = IJsonValue.Metadata.VALUETYPE_GETTER_SLOT

    internal const val STATIC_PARSE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonObjectStatics"

    internal const val STATIC_PARSE_SLOT_OWNER_ACCESSOR: String = "iJsonObjectStatics"

    internal const val STATIC_PARSE_SLOT_OWNER_CACHE: String = "_iJsonObjectStatics"

    internal val STATIC_PARSE_SLOT: Int = IJsonObjectStatics.Metadata.PARSE_SLOT

    internal const val STATIC_TRYPARSE_SLOT_OWNER_INTERFACE: String =
        "Windows.Data.Json.IJsonObjectStatics"

    internal const val STATIC_TRYPARSE_SLOT_OWNER_ACCESSOR: String = "iJsonObjectStatics"

    internal const val STATIC_TRYPARSE_SLOT_OWNER_CACHE: String = "_iJsonObjectStatics"

    internal val STATIC_TRYPARSE_SLOT: Int = IJsonObjectStatics.Metadata.TRYPARSE_SLOT

    internal fun acquireInterface(instance: InspectableReference, iid: Guid): IUnknownReference =
        instance.queryInterface(iid).getOrThrow().use { IUnknownReference(it.getRefPointer(), iid) }

    public fun acquireDefaultInterface(instance: InspectableReference): IUnknownReference =
        acquireInterface(instance, DEFAULT_INTERFACE_IID)

    internal fun wrap(instance: InspectableReference): JsonObject = JsonObject(instance)

    public fun Parse(input: String): JsonObject {
      HString.create(input).use { __inputAbi ->
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocatePointerSlot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance =
              StaticInterfaces.iJsonObjectStatics().pointer, slot = STATIC_PARSE_SLOT, arg0 =
              __inputAbi.handle, arg1 = __resultOut)
          HResult(__hr).requireSuccess()
          val __resultRef =
              IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__resultOut)))
          return JsonObject.Metadata.wrap(__resultRef.asInspectable())
        }
      }

    }

    public fun TryParse(input: String, result: JsonObject): Boolean {
      HString.create(input).use { __inputAbi ->
        return PlatformAbi.confinedScope().use { __scope ->
          val __resultOut = PlatformAbi.allocateInt8Slot(__scope)
          val __hr = ComVtableInvoker.invokeArgs(instance =
              StaticInterfaces.iJsonObjectStatics().pointer, slot = STATIC_TRYPARSE_SLOT, arg0 =
              __inputAbi.handle, arg1 = PlatformAbi.fromRawComPtr((result as
              IWinRTObject).nativeObject.pointer), arg2 = __resultOut)
          HResult(__hr).requireSuccess()
          return PlatformAbi.readInt8(__resultOut).toInt() != 0
        }
      }

    }
  }

  public object ActivationFactory {
    public const val RUNTIME_CLASS: String = "Windows.Data.Json.JsonObject"

    public const val FACTORY_INTERFACE: String = "Any"

    public fun acquire(): IUnknownReference =
        io.github.kitectlab.winrt.runtime.ActivationFactory.get(RUNTIME_CLASS)

    public fun activate(): InspectableReference =
        io.github.kitectlab.winrt.runtime.ActivationFactory.activateInstance(RUNTIME_CLASS)
  }

  public object StaticInterfaces {
    public const val IJSONOBJECTSTATICS: String = "Windows.Data.Json.IJsonObjectStatics"

    public val IJSONOBJECTSTATICS_IID: Guid = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0")

    private val _iJsonObjectStatics: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
        io.github.kitectlab.winrt.runtime.ActivationFactory.get(Metadata.TYPE_NAME,
        IJSONOBJECTSTATICS_IID) }

    public fun iJsonObjectStatics(): IUnknownReference = _iJsonObjectStatics
  }
}
