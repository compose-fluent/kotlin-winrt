package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class WinRtDelegateHandle internal constructor(
    val descriptor: WinRtDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
    private val comObject: WinRtDelegateComObject,
    private val releaseAction: () -> Unit = {},
) : AutoCloseable {
    private val closed = AtomicInt(0)

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
            WinRtDelegateAbiMarshaller.decodeArguments(
                parameterKinds = descriptor.parameterKinds,
                abiArguments = arguments,
            ),
        )
    }

    fun createReference(): WinRtDelegateReference {
        check(closed.load() == 0) { "Delegate handle is already closed." }
        return comObject.createReference()
    }

    override fun close() {
        if (closed.compareAndSet(0, 1)) {
            releaseAction()
        }
    }
}
