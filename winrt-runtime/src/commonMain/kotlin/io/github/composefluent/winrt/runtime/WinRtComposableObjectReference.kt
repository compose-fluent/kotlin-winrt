package io.github.composefluent.winrt.runtime

class WinRtComposableObjectReference internal constructor(
    val instance: IInspectableReference,
    val inner: IInspectableReference?,
    private val composed: IInspectableReference?,
    val outer: ComObjectReference,
    val isAggregatedReferenceTrackerObject: Boolean,
    private val cleanup: () -> Unit,
) : AutoCloseable {
    override fun close() {
        try {
            instance.close()
        } finally {
            try {
                if (!isAggregatedReferenceTrackerObject && inner !== instance) {
                    inner?.close()
                }
            } finally {
                try {
                    composed?.close()
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
}
