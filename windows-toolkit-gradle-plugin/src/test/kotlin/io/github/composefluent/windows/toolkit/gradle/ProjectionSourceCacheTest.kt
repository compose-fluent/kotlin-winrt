package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ProjectionSourceCacheTest {
    @Test fun disabling_local_projection_clears_uncached_support_records() {
        val root = Files.createTempDirectory("uncached-projection-support")
        val support = Files.createDirectories(root.resolve("kotlin-winrt-support"))
        Files.writeString(support.resolve("compiler-support.tsv"), "stale projection registrar")
        Files.writeString(support.resolve("projection-registrar.tsv"), "stale owned types")
        Files.writeString(support.resolve("xaml-component-resources.tsv"), "stale component")
        Files.writeString(root.resolve("User.kt"), "class User")
        clearPreparedStaticSources(root)
        assertFalse(Files.exists(support.resolve("compiler-support.tsv")))
        assertFalse(Files.exists(support.resolve("projection-registrar.tsv")))
        assertFalse(Files.exists(support.resolve("xaml-component-resources.tsv")))
        assertEquals("class User", Files.readString(root.resolve("User.kt")))
    }

    @Test fun implementation_changes_invalidate_only_the_composed_source_cache_key() {
        val contract = "a".repeat(64)
        val original = preparedProjectionImplementationKey(contract, "b".repeat(64))
        assertNotEquals(original, preparedProjectionImplementationKey(contract, "c".repeat(64)))
        assertNotEquals(original, preparedProjectionImplementationKey("d".repeat(64), "b".repeat(64)))
    }

    @Test
    fun cache_repairs_damaged_sources_and_preserves_user_files() {
        // CsWinRTGenerateProjection's input/output boundary, adapted to a shared Gradle cache.
        val root = Files.createTempDirectory("projection-task-cache")
        val output = Files.createDirectories(root.resolve("output"))
        val store = root.resolve("cache")
        val model = WinRTMetadataModel(emptyList())
        var generations = 0
        fun prepare(destination: java.nio.file.Path = output) = prepareProjectionSources(store, "a".repeat(64), destination, model) { target ->
            generations++
            Files.writeString(target.resolve("sample.kt"), "class Sample")
        }
        val cached = prepare()
        Files.writeString(output.resolve("User.kt"), "class User")
        prepare()
        assertEquals(1, generations)
        Files.delete(output.resolve("sample.kt"))
        prepare()
        assertEquals(1, generations)
        assertEquals("class Sample", Files.readString(output.resolve("sample.kt")))
        Files.writeString(cached.resolve("sample.kt"), "corrupt")
        prepare()
        assertEquals(2, generations)
        assertEquals("class User", Files.readString(output.resolve("User.kt")))
        assertTrue(isPreparedStaticSourceValid(cached))
        val relocated = root.resolve("another-checkout/build/projections")
        prepare(relocated)
        assertEquals(2, generations)
        assertEquals("class Sample", Files.readString(relocated.resolve("sample.kt")))
        assertFalse(Files.exists(relocated.resolve("User.kt")))
    }

    @Test
    fun source_locations_do_not_change_key_but_projection_options_do() {
        val model = WinRTMetadataModel(emptyList())
        val root = Files.createTempDirectory("projection-key")
        fun key(path: String, excluded: Set<String> = emptySet(), owner: String = "sample.jar") = preparedProjectionKey(
            model, model, WinRTMetadataProjectionContext(
                sources = listOf(WinRTMetadataSource.path(root.resolve(path))), excludedTypes = excluded,
            ), emptySet(), emptySet(), owner, true,
        )
        assertEquals(key("first/metadata.winmd"), key("relocated/metadata.winmd"))
        assertNotEquals(key("first"), key("first", setOf("Sample.IProbe")))
        assertNotEquals(key("first"), key("first", owner = "other.jar"))
    }
}
