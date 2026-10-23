package io.github.composefluent.winrt.ide.preview

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WinUIXamlDesignDocumentCacheTest {
    @Test fun sourceAndMetadataChangesCannotReuseTransformedMarkup() {
        val root = Files.createTempDirectory("design-document")
        try {
            val cache = WinUIXamlDesignDocumentCache(root, "Page.xaml")
            val key = WinUIXamlDesignDocumentCache.key(listOf("source", "dictionary", "winmd"))
            val document = WinUIXamlDesignDocument("<Grid />", listOf("Event handler excluded"))
            cache.write(key, document)
            assertEquals(document, WinUIXamlDesignDocumentCache(root, "Page.xaml").read(key))
            listOf(0, 1, 2).forEach { changed ->
                val inputs = mutableListOf("source", "dictionary", "winmd")
                inputs[changed] += " changed"
                assertNull(cache.read(WinUIXamlDesignDocumentCache.key(inputs)))
            }
            assertNull(WinUIXamlDesignDocumentCache(root, "Other.xaml").read(key))
            Files.list(root).use { paths -> paths.forEach { Files.writeString(it, "broken") } }
            assertNull(cache.read(key))
        } finally { root.toFile().deleteRecursively() }
    }
}
