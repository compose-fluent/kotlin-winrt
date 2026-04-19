package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationFactoryTest {
    @Test
    fun manifest_free_candidate_names_walk_namespace_prefixes() {
        assertEquals(
            listOf(
                "Microsoft.UI.Xaml.Controls.dll",
                "Microsoft.UI.Xaml.dll",
                "Microsoft.UI.dll",
                "Microsoft.dll",
            ),
            ManifestFreeActivation.candidateDllNames("Microsoft.UI.Xaml.Controls.Button"),
        )
    }

    @Test
    fun non_windows_activation_returns_class_not_registered() {
        if (PlatformRuntime.isWindows) {
            return
        }

        val result = ActivationFactory.tryGet("Windows.Data.Json.JsonObject")
        assertEquals(KnownHResults.REGDB_E_CLASSNOTREG, result.hResult)
        assertFalse(result.isSuccess)
    }

    @Test
    fun activation_factory_iid_is_stable() {
        assertTrue(ActivationFactory.iActivationFactoryIid.toString().isNotBlank())
    }
}
