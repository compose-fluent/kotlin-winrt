package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildWinAppHostTaskTest {
    @Test fun preview_entrypoint_is_separate_from_the_users_main_and_consumes_its_host_argument() {
        val source = applicationHostSource("sample.MainKt", WindowsPackageType.Packaged.name, WinAppJvmRuntimeMode.Bundled.name, "")
        assertTrue(source.contains("--kotlin-winrt-xaml-preview"))
        assertTrue(source.contains("io/github/composefluent/winrt/generated/xaml/KotlinWinRTXamlPreviewHost"))
        assertTrue(source.contains("kotlin_winrt_preview_requested ?"))
    }
    @Test
    fun generated_host_uses_c_integer_condition_for_bundled_runtime() {
        val source = applicationHostSource(
            mainClass = "sample.MainKt",
            packageType = WindowsPackageType.Packaged.name,
            runtimeMode = WinAppJvmRuntimeMode.Bundled.name,
            externalJvmHome = "",
        )

        assertTrue(source.contains("if (1) {"))
        assertFalse(source.contains("if (true) {"))
        assertFalse(source.contains("if (false) {"))
        assertTrue(source.contains("kotlin_winrt_handle_pending_exception"))
        assertTrue(source.contains("if (application_host == NULL)"))
        assertTrue(source.contains("goto cleanup;"))
        assertTrue(source.contains("kotlin_winrt_close_application_host(env, application_host)"))
    }

    @Test
    fun generated_host_uses_c_integer_condition_for_external_runtime() {
        val source = applicationHostSource(
            mainClass = "sample.MainKt",
            packageType = WindowsPackageType.None.name,
            runtimeMode = WinAppJvmRuntimeMode.External.name,
            externalJvmHome = "C:\\Program Files\\Java\\jdk",
        )

        assertTrue(source.contains("if (0) {"))
        assertFalse(source.contains("if (true) {"))
        assertFalse(source.contains("if (false) {"))
    }

    // The runtime's zip.dll imports java.dll by name. The host is not next to them, so without
    // this the loader goes on to PATH and takes the java.dll of whichever JDK is there.
    @Test
    fun generated_host_searches_the_runtime_bin_directory_before_it_loads_the_jvm() {
        val source = applicationHostSource(
            mainClass = "sample.MainKt",
            packageType = WindowsPackageType.None.name,
            runtimeMode = WinAppJvmRuntimeMode.Bundled.name,
            externalJvmHome = "",
        )
        val load = source.substringAfter("static HMODULE kotlin_winrt_load_jvm_at(")
            .substringBefore("static HMODULE kotlin_winrt_load_jvm_module(")
        val useRuntimeDirectory = load.indexOf("kotlin_winrt_use_runtime_library_directory(path);")

        assertTrue(source.contains("SetDllDirectoryW(directory);"))
        assertTrue(useRuntimeDirectory >= 0)
        assertTrue(useRuntimeDirectory < load.indexOf("return LoadLibraryW(path);"))
    }

    // A JetBrains redirect artifact is a JAR without classes that is named like the androidx JAR
    // it points to. Both are on the runtime class path, and the host keeps its JARs in one directory.
    @Test
    fun runtime_jars_of_different_modules_with_one_file_name_are_all_staged() {
        val root = Files.createTempDirectory("kotlin-winrt-host-runtime-jars-")
        fun jar(module: String, name: String, content: String) =
            Files.createDirectories(root.resolve(module)).resolve(name).also { Files.writeString(it, content) }.toFile()
        val library = jar("androidx.lifecycle", "lifecycle-common-jvm-2.11.0.jar", "classes")
        val redirect = jar("org.jetbrains.androidx.lifecycle", "lifecycle-common-jvm-2.11.0.jar", "redirect")
        val libraryCopy = jar("copy", "lifecycle-common-jvm-2.11.0.jar", "classes")
        val other = jar("org.jetbrains.kotlin", "kotlin-stdlib-2.4.0.jar", "stdlib")

        val names = stagedRuntimeJarNames(listOf(redirect, other, library, libraryCopy, library))

        assertEquals("kotlin-stdlib-2.4.0.jar", names.getValue(other))
        assertTrue(names.getValue(library), Regex("lifecycle-common-jvm-2\\.11\\.0-[0-9a-f]{8}\\.jar").matches(names.getValue(library)))
        assertNotEquals(names.getValue(library), names.getValue(redirect))
        assertEquals(names.getValue(library), names.getValue(libraryCopy))
        assertEquals(names, stagedRuntimeJarNames(listOf(libraryCopy, library, other, redirect)))
    }
}
