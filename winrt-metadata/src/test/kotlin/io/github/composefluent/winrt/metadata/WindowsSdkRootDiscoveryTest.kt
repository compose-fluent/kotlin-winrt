package io.github.composefluent.winrt.metadata

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsSdkRootDiscoveryTest {
    @Test
    fun registry_root_is_selected_before_environment_and_default_roots() {
        val registryRoot = Files.createTempDirectory("kotlin-winrt-registry-sdk-")
        val environmentRoot = Files.createTempDirectory("kotlin-winrt-environment-sdk-")
        val defaultParent = Files.createTempDirectory("kotlin-winrt-default-sdk-")
        val environment = mapOf(
            WindowsSdkRootDiscovery.environmentVariable to environmentRoot.toString(),
            "ProgramFiles(x86)" to defaultParent.toString(),
        )

        assertEquals(
            registryRoot.toAbsolutePath().normalize(),
            WindowsSdkRootDiscovery.discover(environment, listOf(registryRoot.toString())),
        )
        assertEquals(
            listOf(
                registryRoot.toAbsolutePath().normalize(),
                environmentRoot.toAbsolutePath().normalize(),
                defaultParent.resolve("Windows Kits").resolve("10").toAbsolutePath().normalize(),
            ),
            WindowsSdkRootDiscovery.candidateRoots(environment, listOf(registryRoot.toString())),
        )
    }

    @Test
    fun environment_root_is_used_when_registry_root_is_unavailable() {
        val environmentRoot = Files.createTempDirectory("kotlin-winrt-environment-sdk-")
        val missingRegistryRoot = environmentRoot.resolve("missing-registry-root")

        assertEquals(
            environmentRoot.toAbsolutePath().normalize(),
            WindowsSdkRootDiscovery.discover(
                environment = mapOf(WindowsSdkRootDiscovery.environmentVariable to environmentRoot.toString()),
                registryRoots = listOf(missingRegistryRoot.toString()),
            ),
        )
    }

    @Test
    fun registry_value_parser_preserves_paths_with_spaces() {
        assertEquals(
            "D:\\Windows Kits\\10\\",
            WindowsSdkRootDiscovery.parseRegistryValueLine(
                "    KitsRoot10    REG_SZ    D:\\Windows Kits\\10\\",
            ),
        )
        assertEquals(
            "D:\\Windows Kits\\10\\",
            WindowsSdkRootDiscovery.parseRegistryValueLine(
                "KitsRoot10 REG_EXPAND_SZ \"D:\\Windows Kits\\10\\\"",
            ),
        )
        assertNull(WindowsSdkRootDiscovery.parseRegistryValueLine("OtherValue REG_SZ D:\\Windows Kits\\10\\"))
        assertTrue(WindowsSdkRootDiscovery.parseRegistryValueLine(" KitsRoot10 REG_SZ ") == null)
    }

    @Test
    fun expandable_registry_root_is_resolved_with_environment_values() {
        val registryRoot = Files.createTempDirectory("kotlin-winrt-expandable-sdk-")
        val environment = mapOf(
            "SDK_ROOT" to registryRoot.toString(),
            "ProgramFiles(x86)" to Files.createTempDirectory("kotlin-winrt-default-sdk-").toString(),
        )

        assertEquals(
            registryRoot.toAbsolutePath().normalize(),
            WindowsSdkRootDiscovery.discover(
                environment = environment,
                registryRoots = listOf("%SDK_ROOT%"),
            ),
        )
    }

    @Test
    fun environment_lookup_is_case_insensitive_for_fallback_candidates() {
        val environmentRoot = Files.createTempDirectory("kotlin-winrt-environment-sdk-")

        assertEquals(
            environmentRoot.toAbsolutePath().normalize(),
            WindowsSdkRootDiscovery.discover(
                environment = mapOf("kotlin_winrt_windows_sdk_root" to environmentRoot.toString()),
                registryRoots = emptyList(),
            ),
        )
    }

    @Test
    fun default_root_accepts_a_quoted_program_files_value() {
        val programFiles = Files.createTempDirectory("kotlin-winrt-program-files-")
        val defaultRoot = programFiles.resolve("Windows Kits").resolve("10")
        Files.createDirectories(defaultRoot)

        assertEquals(
            defaultRoot.toAbsolutePath().normalize(),
            WindowsSdkRootDiscovery.discover(
                environment = mapOf("ProgramFiles(x86)" to "\"${programFiles}\""),
                registryRoots = emptyList(),
            ),
        )
    }

    @Test
    fun registry_output_parser_handles_utf16_and_bom() {
        val output = "\uFEFFHKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows Kits\\Installed Roots\r\n" +
            "    KitsRoot10    REG_SZ    D:\\Windows Kits\\10\\\r\n"

        assertEquals(
            "D:\\Windows Kits\\10\\",
            WindowsSdkRootDiscovery.parseRegistryOutput(output.toByteArray(Charsets.UTF_16LE)),
        )
    }

    @Test
    fun registry_output_parser_preserves_non_ascii_utf16_paths() {
        val output = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows Kits\\Installed Roots\r\n" +
            "    KitsRoot10    REG_SZ    D:\\Windows Kits\u4E2D\u6587\\10\\\r\n"

        assertEquals(
            "D:\\Windows Kits\u4E2D\u6587\\10\\",
            WindowsSdkRootDiscovery.parseRegistryOutput(output.toByteArray(Charsets.UTF_16LE)),
        )
    }
}
