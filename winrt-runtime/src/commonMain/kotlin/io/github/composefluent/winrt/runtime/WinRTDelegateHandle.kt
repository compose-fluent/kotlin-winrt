package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class WinRTDelegateHandle internal constructor(
    val descriptor: WinRTDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
    private val comObject: WinRTDelegateComObject,
    private val releaseAction: () -> Unit = {},
) : AutoCloseable {
    private val closed = AtomicInt(0)
    private val managedReferenceReleased = AtomicInt(0)

    fun invokeForTesting(arguments: List<Any?>): Any? {
        check(closed.load() == 0) { "Delegate handle is already closed." }
        require(arguments.size == descriptor.parameterKinds.size) {
            "Argument count ${arguments.size} must match delegate parameter count ${descriptor.parameterKinds.size}."
        }
        return callback(arguments)
    }

    fun invokeAbiForTesting(arguments: List<Any?>): Any? {
        check(closed.load() == 0) { "Delegate handle is already closed." }
        return callback(
            WinRTDelegateAbiMarshaller.decodeArguments(
                parameterKinds = descriptor.parameterKinds,
                abiArguments = arguments,
            ),
        )
    }

    fun createReference(): WinRTDelegateReference {
        return tryCreateReference()
            ?: throw WinRTObjectDisposedException("Delegate handle is already closed.")
    }

    internal fun tryCreateReference(): WinRTDelegateReference? =
        if (closed.load() == 0) comObject.tryCreateReference() else null

    internal fun releaseManagedReferenceForNativeOwnership() {
        if (managedReferenceReleased.compareAndSet(0, 1)) {
            releaseAction()
        }
    }

    internal fun addCleanupAction(action: () -> Unit) {
        comObject.addCleanupAction(action)
    }

    internal fun markClosedAfterNativeCleanup() {
        closed.compareAndSet(0, 1)
    }

    override fun close() {
        if (closed.compareAndSet(0, 1)) {
            releaseManagedReferenceForNativeOwnership()
        }
    }
}
