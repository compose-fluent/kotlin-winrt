package sample.consumer

import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.RuntimeScope
import windows.data.json.IJsonValue
import windows.data.json.JsonValueType
import windows.foundation.IStringable

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
    }
}
