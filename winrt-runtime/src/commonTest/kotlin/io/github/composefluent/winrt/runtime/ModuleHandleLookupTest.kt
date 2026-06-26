package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse

class ModuleHandleLookupTest {
    @Test
    fun module_handle_lookup_resolves_module_from_export_address() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        val loadedModule = WinRTPlatformApi.tryLoadLibraryExWRaw("kernel32.dll", flags = 0)
        assertFalse(PlatformAbi.isNull(loadedModule))
        try {
            val procedure = WinRTPlatformApi.tryGetProcAddressRaw(loadedModule, "GetLastError")
            assertFalse(PlatformAbi.isNull(procedure))

            val addressModule = WinRTPlatformApi.tryGetModuleHandleExFromAddressRaw(procedure)
            assertFalse(PlatformAbi.isNull(addressModule))
            WinRTPlatformApi.freeLibraryRaw(addressModule)
        } finally {
            WinRTPlatformApi.freeLibraryRaw(loadedModule)
        }
    }
}
