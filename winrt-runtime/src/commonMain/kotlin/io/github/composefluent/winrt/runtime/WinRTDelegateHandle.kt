package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class WinRTDelegateHandle internal constructor(
    val descriptor: WinRTDelegateDescriptor,
    callback: (List<Any?>) -> Any?,
    managedTarget: Any,
    private val comObject: WinRTDelegateComObject,
    private val releaseAction: () -> Unit = {},
) : AutoCloseable {
    private val closed = AtomicInt(0)
    private val managedReferenceReleased = AtomicInt(0)
    private var ownedCallback: ((List<Any?>) -> Any?)? = callback
    private var ownedTarget: Any? = managedTarget
    private val callbackReference = PlatformManagedWeakReference(callback)

    private fun callback(): (List<Any?>) -> Any? = ownedCallback ?: callbackReference.get()
        ?: throw WinRTObjectDisposedException("Delegate callback was collected.")

    fun invokeForTesting(arguments: List<Any?>): Any? {
        check(closed.load() == 0) { "Delegate handle is already closed." }
        require(arguments.size == descriptor.parameterKinds.size) {
            "Argument count ${arguments.size} must match delegate parameter count ${descriptor.parameterKinds.size}."
        }
        return callback()(arguments)
    }

    fun invokeAbiForTesting(arguments: List<Any?>): Any? {
        check(closed.load() == 0) { "Delegate handle is already closed." }
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
        if (closed.load() == 0) comObject.tryCreateReference(ownedTarget) else null

    internal fun tryAcquireMarshalingReference(): RawAddress? =
        if (closed.load() == 0) comObject.tryAcquireMarshalingReference(ownedTarget) else null

    internal fun releaseManagedReferenceForNativeOwnership() {
        if (managedReferenceReleased.compareAndSet(0, 1)) {
            ownedCallback = null
            ownedTarget = null
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
