package sample.consumer

import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtAsyncInterfaceIds
import io.github.composefluent.winrt.runtime.WinRtAsyncProjectionInterop
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import io.github.composefluent.winrt.runtime.join
import sample.NativeJsonValueThing
import windows.data.json.IJsonValue
import windows.data.json.JsonValueType
import windows.foundation.AsyncStatus
import windows.foundation.collections.IPropertySet
import windows.foundation.collections.MapChangedEventHandler
import windows.foundation.IStringable
import windows.storage.streams.IDataReader
import kotlin.time.Duration
import kotlin.time.Instant

fun main() {
    RuntimeScope.initializeMultithreaded().use {
        ActivationFactory.get("sample.NativeClosableThing").use { factory ->
            factory.activateInstance().use { instance ->
                instance.queryInterface(IID.IDisposable).getOrThrow().close()
            }
        }

        ActivationFactory.get("sample.NativeStringableThing").use { factory ->
            factory.activateInstance().use { instance ->
                instance.queryInterface(IStringable.Metadata.IID).getOrThrow().use { stringableReference ->
                    val projected = IStringable.Metadata.wrap(stringableReference as IUnknownReference)
                    check(projected.toString() == "NativeStringableThing") {
                        "Expected authored IStringable projection to dispatch through native activation."
                    }
                }
            }
        }

        ActivationFactory.get("sample.NativeJsonValueThing").use { factory ->
            factory.activateInstance().use { instance ->
                instance.queryInterface(IJsonValue.Metadata.IID).getOrThrow().use { jsonValueReference ->
                    val projected = IJsonValue.Metadata.wrap(jsonValueReference as IUnknownReference)
                    check(projected.valueType == JsonValueType.String) {
                        "Expected authored IJsonValue valueType to dispatch through native activation."
                    }
                    check(projected.stringify() == "\"NativeJsonValueThing\"") {
                        "Expected authored IJsonValue stringify to dispatch through native activation."
                    }
                    check(projected.getString() == "NativeJsonValueThing") {
                        "Expected authored IJsonValue getString to dispatch through native activation."
                    }
                    check(projected.getNumber() == 42.5) {
                        "Expected authored IJsonValue getNumber to dispatch through native activation."
                    }
                    check(projected.getBoolean()) {
                        "Expected authored IJsonValue getBoolean to dispatch through native activation."
                    }
                }
            }
        }

        val staticFactoryValue = NativeJsonValueThing.createStringValue("FromStaticFactory")
        check(staticFactoryValue.valueType == JsonValueType.String) {
            "Expected authored IJsonValueStatics.CreateStringValue result to preserve valueType."
        }
        check(staticFactoryValue.stringify() == "\"FromStaticFactory\"") {
            "Expected authored IJsonValueStatics.CreateStringValue result to dispatch stringify."
        }
        check(staticFactoryValue.getString() == "FromStaticFactory") {
            "Expected authored IJsonValueStatics.CreateStringValue result to dispatch getString."
        }

        ActivationFactory.get("sample.NativeDataReaderThing").use { factory ->
            factory.activateInstance().use { instance ->
                instance.queryInterface(IDataReader.Metadata.IID).getOrThrow().use { dataReaderReference ->
                    val projected = IDataReader.Metadata.wrap(dataReaderReference as IUnknownReference)
                    val buffer = Array(4) { 0u.toUByte() }
                    projected.readBytes(buffer)
                    check(buffer.contentEquals(arrayOf(0x57u.toUByte(), 0x69u.toUByte(), 0x6Eu.toUByte(), 0x52u.toUByte()))) {
                        "Expected authored IDataReader.ReadBytes FillArray dispatch to update caller-provided array."
                    }
                    val loadOperation = projected.loadAsync(10u)
                    check(loadOperation.status == AsyncStatus.Completed) {
                        "Expected authored IDataReader.LoadAsync result to be completed."
                    }
                    val asyncOperationInterfaceId = ParameterizedInterfaceId.createFromSignature(
                        WinRtTypeSignature.parameterizedInterface(
                            WinRtAsyncInterfaceIds.IAsyncOperationGeneric,
                            WinRtTypeSignature.uint32(),
                        ),
                    )
                    loadOperation.nativeObject.queryInterface(asyncOperationInterfaceId).getOrThrow().use { asyncReference ->
                        WinRtAsyncProjectionInterop.operation(
                            pointer = PlatformAbi.fromRawComPtr(asyncReference.getRefPointer()),
                            resultSignature = WinRtTypeSignature.uint32(),
                            resultOut = PlatformAbi::allocateInt32Slot,
                            resultReader = { resultOut -> PlatformAbi.readInt32(resultOut).toUInt() },
                        ).use { asyncOperation ->
                            check(asyncOperation.join() == 4u) {
                                "Expected authored IDataReader.LoadAsync to return the completed byte count."
                            }
                        }
                    }
                    check(projected.readDateTime() == Instant.fromEpochSeconds(1_700_000_000L, 123_456_700)) {
                        "Expected authored IDataReader.ReadDateTime to round-trip the authored Instant."
                    }
                    check(projected.readTimeSpan() == Duration.parse("PT1H2M3.4567S")) {
                        "Expected authored IDataReader.ReadTimeSpan to round-trip the authored Duration."
                    }
                }
            }
        }

        ActivationFactory.get("sample.NativePropertySetThing").use { factory ->
            factory.activateInstance().use { instance ->
                instance.queryInterface(IPropertySet.Metadata.IID).getOrThrow().use { propertySetReference ->
                    val projected = IPropertySet.Metadata.wrap(propertySetReference as IUnknownReference)
                    check(projected.containsKey("existing")) {
                        "Expected authored IPropertySet.HasKey to dispatch through native activation."
                    }
                    val handler = MapChangedEventHandler<String, Any?> { _, _ -> }
                    val token = projected.addMapChanged(handler)
                    projected.removeMapChanged(token)
                    projected["added"] = "value"
                    check(projected.containsKey("added")) {
                        "Expected authored IPropertySet.Insert to dispatch through native activation."
                    }
                    projected.remove("added")
                    check(!projected.containsKey("added")) {
                        "Expected authored IPropertySet.Remove to dispatch through native activation."
                    }
                }
            }
        }
    }
}
