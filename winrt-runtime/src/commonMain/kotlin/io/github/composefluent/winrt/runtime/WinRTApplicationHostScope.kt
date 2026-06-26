package io.github.composefluent.winrt.runtime

internal object WinRTApplicationHostScope {
    fun initialize(unpackaged: Boolean): AutoCloseable {
        val deployment = if (unpackaged) {
            WinRTWindowsAppSdkDeployment.initializeForUnpackagedApp()
        } else {
            null
        }
        return try {
            Scope(
                runtime = RuntimeScope.initializeSingleThreaded(),
                deployment = deployment,
            )
        } catch (failure: Throwable) {
            deployment?.close()
            throw failure
        }
    }

    private class Scope(
        private val runtime: RuntimeScope,
        private val deployment: AutoCloseable?,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            var failure: Throwable? = null
            runCatching { runtime.close() }
                .onFailure { failure = it }
            runCatching { deployment?.close() }
                .onFailure { error ->
                    failure?.addSuppressed(error) ?: run { failure = error }
                }
            failure?.let { throw it }
        }
    }
}
