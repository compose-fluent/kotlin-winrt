package io.github.kitectlab.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonRuntimeModelsTest {
    @Test
    fun hResult_tracks_success_failure_and_hex_format() {
        assertTrue(KnownHResults.S_OK.isSuccess)
        assertFalse(KnownHResults.S_OK.isFailure)
        assertTrue(KnownHResults.REGDB_E_CLASSNOTREG.isFailure)
        assertEquals("0x80040154", KnownHResults.REGDB_E_CLASSNOTREG.toString())
    }

    @Test
    fun manifest_free_candidate_names_walk_namespace_prefixes() {
        // Mirrors the runtime-class probing order in .cswinrt/src/WinRT.Runtime/ActivationFactory.cs.
        assertEquals(
            listOf(
                "Microsoft.UI.Xaml.Controls.dll",
                "Microsoft.UI.Xaml.dll",
                "Microsoft.UI.dll",
                "Microsoft.dll",
            ),
            manifestFreeActivationCandidateDllNames("Microsoft.UI.Xaml.Controls.Button"),
        )
    }
}
