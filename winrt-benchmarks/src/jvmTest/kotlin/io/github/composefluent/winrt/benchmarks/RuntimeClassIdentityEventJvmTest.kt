package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.ClassWithMarshalingRoutines
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic
import kotlin.test.Test
import kotlin.test.assertSame
import windows.foundation.EventHandler

class RuntimeClassIdentityEventJvmTest {
    @Test
    fun native_event_sender_reuses_the_activated_runtime_class_wrapper() {
        RuntimeScope.initializeMultithreaded().use {
            WinRTProjectionSupportIntrinsic.ensureInitialized()
            val instance = ClassWithMarshalingRoutines()
            var sender: Any? = null
            val handler: EventHandler<Int> = { value, _ -> sender = value }

            instance.intPropertyChanged += handler
            try {
                instance.raiseIntChanged()
            } finally {
                instance.intPropertyChanged -= handler
            }

            assertSame(instance, sender)
        }
    }
}
