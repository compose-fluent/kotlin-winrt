package io.github.composefluent.windows.toolkit.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.gradle.api.GradleException
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

    @Test
    fun uses_the_local_build_jdk_when_the_ide_overrides_process_java_home() {
        val root = Files.createTempDirectory("local-jvm-native-headers-")
        val ideRuntime = root.resolve("IDE runtime")
        val localJdk = root.resolve("Local JDK")
        writeRelease(ideRuntime, "25.0.3")
        writeRelease(localJdk, "25.0.4")
        Files.createDirectories(localJdk.resolve("include/win32"))
        Files.writeString(localJdk.resolve("include/jni.h"), "")
        Files.writeString(localJdk.resolve("include/win32/jni_md.h"), "")

        val fallbacks = jvmDevelopmentKitFallbackHomes(
            environment = mapOf("java_home" to ideRuntime.toString()),
            currentJavaHome = ideRuntime.toString(),
            localProperties = "java.home=${localJdk.toString().replace("\\", "\\\\")}",
        )

        assertEquals(localJdk.toAbsolutePath().normalize(), resolveJvmDevelopmentKitHome(ideRuntime, 25, fallbacks))
        assertThrows(GradleException::class.java) { resolveJvmDevelopmentKitHome(ideRuntime, 21, fallbacks) }
    }
}
