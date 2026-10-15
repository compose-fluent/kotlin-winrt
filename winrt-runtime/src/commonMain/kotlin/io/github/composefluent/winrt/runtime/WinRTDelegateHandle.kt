package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class WinRTDelegateHandle internal constructor(
    val descriptor: WinRTDelegateDescriptor,
    callback: ((List<Any?>) -> Any?)?,
    managedTarget: Any,
    private val comObject: WinRTDelegateComObject,
) : AutoCloseable {
    // CsWinRT 3 object references pack disposal and ownership state into one
    // atomic word. Keep these independent claims without allocating two atomics.
    private val lifecycle = AtomicInt(0)
    private var ownedCallback: ((List<Any?>) -> Any?)? = callback
    private var ownedTarget: Any? = managedTarget
    private fun callback(): (List<Any?>) -> Any? = ownedCallback ?: comObject.callback()

    fun invokeForTesting(arguments: List<Any?>): Any? {
        check(lifecycle.load() and closedFlag == 0) { "Delegate handle is already closed." }
        require(arguments.size == descriptor.parameterKinds.size) {
            "Argument count ${arguments.size} must match delegate parameter count ${descriptor.parameterKinds.size}."
        }
        return callback()(arguments)
    }

    fun invokeAbiForTesting(arguments: List<Any?>): Any? {
        check(lifecycle.load() and closedFlag == 0) { "Delegate handle is already closed." }
        return callback()(
            WinRTDelegateAbiMarshaller.decodeArguments(
                descriptor = descriptor,
                abiArguments = arguments,
            ),
        )
    }

    fun createReference(): WinRTDelegateReference {
        return tryCreateReference()
            ?: throw WinRTObjectDisposedException("Delegate handle is already closed.")
    }

    internal fun tryCreateReference(): WinRTDelegateReference? =
        if (lifecycle.load() and closedFlag == 0) comObject.tryCreateReference(ownedTarget) else null

    internal fun tryAcquireMarshalingReference(): RawAddress? =
        if (lifecycle.load() and closedFlag == 0) comObject.tryAcquireMarshalingReference(ownedTarget) else null

    internal fun releaseManagedReferenceForNativeOwnership() {
        if (claim(managedReferenceReleasedFlag)) {
            ownedCallback = null
            ownedTarget = null
            comObject.releaseManagedReference()
        }
    }

    internal fun addCleanupAction(action: () -> Unit) {
        comObject.addCleanupAction(action)
    }

    internal fun markClosedAfterNativeCleanup() {
        claim(closedFlag)
    }

    private fun claim(flag: Int): Boolean {
        while (true) {
            val current = lifecycle.load()
            if (current and flag != 0) return false
            if (lifecycle.compareAndSet(current, current or flag)) return true
        }
    }

    private companion object {
        const val closedFlag = 1
        const val managedReferenceReleasedFlag = 2
    }

    override fun close() {
        if (claim(closedFlag)) {
            releaseManagedReferenceForNativeOwnership()
        }
    }
}
