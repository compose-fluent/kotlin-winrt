package sample.consumer

import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.RuntimeScope
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
    }
}
