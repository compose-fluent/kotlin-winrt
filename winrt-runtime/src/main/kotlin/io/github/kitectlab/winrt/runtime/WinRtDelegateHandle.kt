package io.github.kitectlab.winrt.runtime

import java.util.concurrent.atomic.AtomicBoolean

class WinRtDelegateHandle internal constructor(
    val descriptor: WinRtDelegateDescriptor,
    private val callback: (List<Any?>) -> Unit,
    private val comObject: WinRtDelegateComObject,
    private val releaseAction: () -> Unit = {},
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    fun invokeForTesting(arguments: List<Any?>) {
        check(!closed.get()) { "Delegate handle is already closed." }
        require(arguments.size == descriptor.parameterKinds.size) {
            "Argument count ${arguments.size} must match delegate parameter count ${descriptor.parameterKinds.size}."
        }
        callback(arguments)
    }

    fun invokeAbiForTesting(arguments: List<Any?>) {
        check(!closed.get()) { "Delegate handle is already closed." }
        callback(
            WinRtDelegateAbiMarshaller.decodeArguments(
                parameterKinds = descriptor.parameterKinds,
                abiArguments = arguments,
            ),
        )
    }

    fun createReference(): WinRtDelegateReference {
        check(!closed.get()) { "Delegate handle is already closed." }
        return comObject.createReference()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            releaseAction()
        }
    }
}
