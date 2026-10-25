package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.runtime.WinUIXamlVisualImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class WinUIXamlPreviewPlaceholderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val image = WinUIXamlVisualImage(2, 2, ByteArray(16) { it.toByte() })

    @Test fun restoresOnlyPixelsAndArtboardWithoutEditableSourceMetadata() {
        val cache = WinUIXamlPreviewPlaceholder(temporary.root.toPath(), "Page.xaml")
        cache.write("Dark", 800, 600, image)
        val snapshot = requireNotNull(cache.read("Dark", 800, 600))
        assertArrayEquals(image.pixels, snapshot.image!!.pixels)
        assertEquals(800.0, snapshot.nodes.single().bounds.width, 0.0)
        assertEquals("Previous render", snapshot.nodes.single().typeName)
        assertTrue(snapshot.properties.isEmpty())
        assertNull(snapshot.clock)
    }

    @Test fun themeViewportAndSourceCannotBorrowAnotherPlaceholder() {
        val cache = WinUIXamlPreviewPlaceholder(temporary.root.toPath(), "Page.xaml")
        cache.write("Dark", 800, 600, image)
        assertNull(cache.read("Light", 800, 600))
        assertNull(cache.read("Dark", 600, 800))
        assertNull(WinUIXamlPreviewPlaceholder(temporary.root.toPath(), "Other.xaml").read("Dark", 800, 600))
    }

    @Test fun corruptOrTruncatedCacheIsIgnored() {
        val directory = temporary.root.toPath()
        val cache = WinUIXamlPreviewPlaceholder(directory, "Page.xaml")
        cache.write("Default", 800, 600, image)
        val file = Files.list(directory).use { it.findFirst().get() }
        val bytes = Files.readAllBytes(file)
        Files.write(file, bytes.copyOf(bytes.size / 2))
        assertNull(cache.read("Default", 800, 600))
        Files.write(file, ByteArray(32) { 0x7f })
        assertNull(cache.read("Default", 800, 600))
    }

    @Test fun rejectsInvalidPixelDimensionsAndBufferLengths() {
        val cache = WinUIXamlPreviewPlaceholder(temporary.root.toPath(), "Page.xaml")
        assertThrows(IllegalArgumentException::class.java) {
            cache.write("Default", 800, 600, WinUIXamlVisualImage(Int.MAX_VALUE, Int.MAX_VALUE, byteArrayOf()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            cache.write("Default", 800, 600, WinUIXamlVisualImage(2, 2, byteArrayOf()))
        }
    }

    @Test fun boundsDiskEntriesAndKeepsTheNewestDocument() {
        val directory = temporary.root.toPath()
        repeat(10) { WinUIXamlPreviewPlaceholder(directory, "Page$it.xaml").write("Default", 800, 600, image) }
        assertEquals(8L, Files.list(directory).use { it.count() })
        assertNotNull(WinUIXamlPreviewPlaceholder(directory, "Page9.xaml").read("Default", 800, 600))
    }
}
