package io.github.composefluent.winrt.gradle

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class JvmNativeHeadersTest {
    @Test
    fun falls_back_to_a_matching_jdk_when_the_selected_toolchain_lacks_jni_headers() {
        val root = Files.createTempDirectory("jvm-native-headers-")
        val selectedToolchain = root.resolve("ide-runtime")
        val developmentKit = root.resolve("jdk")
        writeRelease(selectedToolchain, "25.0.3")
        writeRelease(developmentKit, "25.0.4")
        Files.createDirectories(developmentKit.resolve("include/win32"))
        Files.writeString(developmentKit.resolve("include/jni.h"), "")
        Files.writeString(developmentKit.resolve("include/win32/jni_md.h"), "")

        val resolved = resolveJvmDevelopmentKitHome(
            selectedHome = selectedToolchain,
            expectedJavaMajor = 25,
            fallbackHomes = listOf(developmentKit),
        )

        assertEquals(developmentKit.toAbsolutePath().normalize(), resolved)
    }

    private fun writeRelease(home: java.nio.file.Path, version: String) {
        Files.createDirectories(home)
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"$version\"")
    }
}
