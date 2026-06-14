package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse

class ModuleHandleLookupTest {
    @Test
    fun module_handle_lookup_resolves_module_from_export_address() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        val loadedModule = WinRtPlatformApi.tryLoadLibraryExWRaw("kernel32.dll", flags = 0)
        assertFalse(PlatformAbi.isNull(loadedModule))
        try {
            val procedure = WinRtPlatformApi.tryGetProcAddressRaw(loadedModule, "GetLastError")
            assertFalse(PlatformAbi.isNull(procedure))

            val addressModule = WinRtPlatformApi.tryGetModuleHandleExFromAddressRaw(procedure)
            assertFalse(PlatformAbi.isNull(addressModule))
            WinRtPlatformApi.freeLibraryRaw(addressModule)
        } finally {
            WinRtPlatformApi.freeLibraryRaw(loadedModule)
        }
    }
}
