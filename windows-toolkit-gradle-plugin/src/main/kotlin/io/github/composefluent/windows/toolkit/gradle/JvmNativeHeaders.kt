package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

internal data class JvmNativeHeaderDirectories(
    val includeDirectory: Path,
    val platformIncludeDirectory: Path,
)

/**
 * Selects a JDK suitable for compiling the Windows JNI hosts.
 *
 * Some IDE runtimes advertise themselves as Java toolchains and contain javac, but omit the
 * development headers required by native JNI callers. Keep the configured toolchain when it is
 * complete; otherwise accept only a same-major fallback JDK.
 */
internal fun resolveJvmDevelopmentKitHome(
    selectedHome: Path,
    expectedJavaMajor: Int,
    fallbackHomes: Iterable<Path> = jvmDevelopmentKitFallbackHomes(),
): Path {
    val normalizedSelectedHome = selectedHome.toAbsolutePath().normalize()
    if (findJvmNativeHeaderDirectories(normalizedSelectedHome) != null) {
        return normalizedSelectedHome
    }
    return fallbackHomes.asSequence()
        .map { it.toAbsolutePath().normalize() }
        .filter { it != normalizedSelectedHome }
        .distinct()
        .firstOrNull { candidate ->
            findJvmNativeHeaderDirectories(candidate) != null && javaReleaseMajor(candidate) == expectedJavaMajor
        }
        ?: throw GradleException(
            "Kotlin/WinRT JVM native hosts require JNI headers, but the configured Java toolchain at " +
                "$normalizedSelectedHome does not contain include/jni.h and include/win32/jni_md.h. " +
                "Set java.home in .gradle/config.properties or JAVA_HOME to a complete JDK $expectedJavaMajor.",
        )
}

internal fun resolveJvmNativeHeaderDirectories(javaHome: String): JvmNativeHeaderDirectories {
    val normalizedJavaHome = javaHome.trim().takeIf(String::isNotBlank)?.let(Path::of)
        ?.toAbsolutePath()
        ?.normalize()
        ?: throw GradleException("Kotlin/WinRT JVM native hosts require a resolved Java toolchain home for JNI headers.")
    return findJvmNativeHeaderDirectories(normalizedJavaHome)
        ?: throw GradleException(
            "Kotlin/WinRT JVM native hosts require include/jni.h and include/win32/jni_md.h under the configured " +
                "JDK home: $normalizedJavaHome",
        )
}

internal fun jvmDevelopmentKitFallbackHomes(
    environment: Map<String, String>? = null,
    currentJavaHome: String = System.getProperty("java.home"),
    localProperties: String = "",
): List<Path> = listOfNotNull(
    Properties().apply { localProperties.reader().use(::load) }.getProperty("java.home"),
    environment.valueIgnoringCase("JAVA_HOME"),
    environment.valueIgnoringCase("JDK_HOME"),
    currentJavaHome,
).mapNotNull { value ->
    value.trim().takeIf(String::isNotBlank)?.let { path -> runCatching { Path.of(path) }.getOrNull() }
}

private fun findJvmNativeHeaderDirectories(javaHome: Path): JvmNativeHeaderDirectories? {
    val includeDirectory = javaHome.resolve("include")
    val platformIncludeDirectory = includeDirectory.resolve("win32")
    return if (
        Files.isRegularFile(includeDirectory.resolve("jni.h")) &&
        Files.isRegularFile(platformIncludeDirectory.resolve("jni_md.h"))
    ) {
        JvmNativeHeaderDirectories(includeDirectory, platformIncludeDirectory)
    } else {
        null
    }
}

private fun javaReleaseMajor(javaHome: Path): Int? = runCatching {
    val value = Files.readAllLines(javaHome.resolve("release"))
        .firstOrNull { it.startsWith("JAVA_VERSION=") }
        ?.substringAfter('=')
        ?.trim()
        ?.trim('"')
        ?.substringBefore('-')
        ?: return null
    val parts = value.split('.')
    if (parts.firstOrNull() == "1") parts.getOrNull(1)?.toIntOrNull() else parts.firstOrNull()?.toIntOrNull()
}.getOrNull()

private fun Map<String, String>?.valueIgnoringCase(name: String): String? =
    // Windows getenv(name) already ignores case. Enumerating the ambient map would make
    // unrelated session variables configuration-cache inputs during toolchain resolution.
    if (this == null) System.getenv(name)
    else get(name) ?: entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value
