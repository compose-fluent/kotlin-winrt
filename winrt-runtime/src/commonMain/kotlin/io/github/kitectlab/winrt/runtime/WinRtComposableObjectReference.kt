package io.github.kitectlab.winrt.runtime

class WinRtComposableObjectReference internal constructor(
    val instance: IInspectableReference,
    val inner: IInspectableReference?,
    val outer: ComObjectReference,
    val isAggregatedReferenceTrackerObject: Boolean,
    private val cleanup: () -> Unit,
) : AutoCloseable {
    override fun close() {
        try {
            instance.close()
        } finally {
            try {
                if (!isAggregatedReferenceTrackerObject) {
                    inner?.close()
                }
            } finally {
                try {
                    outer.close()
                } finally {
                    cleanup()
                }
            }
        }
    }
}
