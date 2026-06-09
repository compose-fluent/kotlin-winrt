package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InteropRuntimeTest {
    @Test
    fun agile_reference_resolves_same_identity_for_winrt_object() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeMultithreaded().use {
            WinRtRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow().use { instance ->
                AgileReference(instance).use { agileReference ->
                    val resolved = agileReference.get()
                    assertNotNull(resolved)
                    resolved.use {
                        assertTrue(it.sameIdentity(instance))
                    }
                }
            }
        }
    }

    @Test
    fun context_owner_can_reenter_current_context_without_throwing() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeMultithreaded().use {
            val contextToken = Context.getContextToken()
            val contextCallback = Context.getContextCallback()
            assertNotNull(contextCallback)
            contextCallback.use {
                var invoked = false
                Context.callInContext(
                    contextCallback = it,
                    contextToken = contextToken,
                    callback = { _: Unit -> invoked = true },
                    state = Unit,
                )
                assertTrue(invoked)
            }
        }
    }
}
