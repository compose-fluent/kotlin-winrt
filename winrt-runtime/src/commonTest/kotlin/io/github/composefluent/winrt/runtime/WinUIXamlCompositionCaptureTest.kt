@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class WinUIXamlCompositionCaptureTest {
    @Test fun composition_crop_excludes_asymmetric_nonclient_pixels_without_resampling() {
        // WGC uses DWM frame bounds; GetClientRect + ClientToScreen supplies the
        // actual client offset (not a guessed symmetric border/title-bar size).
        val pixels = ByteArray(6 * 5 * 4) { (it % 251).toByte() }
        val client = WinUIXamlVisualBounds(1.0, 2.0, 3.0, 2.0)
        val viewport = WinUIXamlVisualBounds(200.0, 100.0, 1.5, 1.0)
        val image = cropWinUIXamlCompositionFrame(6, 5, pixels, client, viewport)
        assertEquals(3, image.width); assertEquals(2, image.height)
        assertEquals(viewport, image.bounds); assertEquals("Composition", image.captureBackend)
        assertContentEquals(pixels.copyOfRange(52, 64) + pixels.copyOfRange(76, 88), image.pixels)
        assertFailsWith<IllegalArgumentException> { cropWinUIXamlCompositionFrame(6, 5, pixels, client.copy(x = -1.0)) }
        assertFailsWith<IllegalArgumentException> { cropWinUIXamlCompositionFrame(6, 5, pixels, client.copy(y = 4.0)) }
        assertFailsWith<IllegalArgumentException> { cropWinUIXamlCompositionFrame(6, 5, pixels, client.copy(x = 0.5)) }
        assertFailsWith<IllegalArgumentException> { cropWinUIXamlCompositionFrame(6, 5, pixels.copyOf(119), client) }
    }

    @Test fun direct3d_device_is_a_real_winrt_device_with_scoped_com_ownership() {
        if (!PlatformRuntime.isWindows) return
        WinRTModule.ensureInitialized()
        // SDK D3D11CreateDevice -> IDXGIDevice -> CreateDirect3D11DeviceFromDXGIDevice;
        // CsWinRT Runtime2 ObjectReference owns each acquired interface independently.
        repeat(2) {
            val device = createWinUIXamlCaptureDevice()
            device.use {
                assertFalse(it.isDisposed)
                it.queryInterface(Guid("A37624AB-8D5F-4650-9D3E-9EAE3D9BC670")).getOrThrow().use { projected ->
                    assertFalse(PlatformAbi.isNull(projected.pointer))
                }
            }
            assertTrue(device.isDisposed)
        }
    }
}
