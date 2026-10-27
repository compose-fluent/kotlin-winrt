@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

@file:JvmName("WinUIXamlDevelopmentBuildPolicy")

package io.github.composefluent.winrt.runtime

import java.nio.file.Path
import java.util.jar.JarFile

/** Read only the archive defining this class, not classpath resources or system properties.
 * Published runtime JARs and unpacked classes fail closed. Development run tasks
 * stamp only their isolated runtime copy; distribution staging removes the stamp. */
@InternalWinRTApi
actual fun isWinUIXamlDevelopmentBuild(): Boolean = WinUIXamlDevelopmentBuild.enabled

private object WinUIXamlDevelopmentBuild {
    val enabled: Boolean = runCatching {
        val source = WinUIXamlDevelopmentBuild::class.java.protectionDomain.codeSource?.location
            ?: return@runCatching false
        if (source.protocol != "file") return@runCatching false
        JarFile(Path.of(source.toURI()).toFile()).use {
            it.manifest?.mainAttributes?.getValue("Kotlin-WinRT-Development") == "true"
        }
    }.getOrDefault(false)
}
