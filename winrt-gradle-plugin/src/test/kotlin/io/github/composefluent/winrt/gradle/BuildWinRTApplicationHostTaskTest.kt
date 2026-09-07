package io.github.composefluent.winrt.gradle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildWinRTApplicationHostTaskTest {
    @Test
    fun generated_host_uses_c_integer_condition_for_bundled_runtime() {
        val source = applicationHostSource(
            mainClass = "sample.MainKt",
            packageMode = WinRTApplicationPackageMode.Packaged.name,
            runtimeMode = WinRTJvmRuntimeMode.Bundled.name,
            externalJvmHome = "",
        )

        assertTrue(source.contains("if (1) {"))
        assertFalse(source.contains("if (true) {"))
        assertFalse(source.contains("if (false) {"))
    }

    @Test
    fun generated_host_uses_c_integer_condition_for_external_runtime() {
        val source = applicationHostSource(
            mainClass = "sample.MainKt",
            packageMode = WinRTApplicationPackageMode.Unpackaged.name,
            runtimeMode = WinRTJvmRuntimeMode.External.name,
            externalJvmHome = "C:\\Program Files\\Java\\jdk",
        )

        assertTrue(source.contains("if (0) {"))
        assertFalse(source.contains("if (true) {"))
        assertFalse(source.contains("if (false) {"))
    }
}
