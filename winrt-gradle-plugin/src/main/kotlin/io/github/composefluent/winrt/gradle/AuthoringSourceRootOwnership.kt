package io.github.composefluent.winrt.gradle

import java.nio.file.Path

private val KOTLIN_WINRT_PLUGIN_GENERATED_SOURCE_OWNERS = setOf(
    "kotlin-winrt",
    "kotlin-winrt-authoring",
    "kotlin-winrt-application-entry",
    "kotlin-winrt-native-authoring-host",
)

/** Identifies plugin-owned generated source families independent of their per-variant suffix. */
internal fun isKotlinWinRTPluginOwnedAuthoringSourceRoot(path: Path): Boolean {
    val components = path.toAbsolutePath().normalize().iterator().asSequence().toList()
    return components.zipWithNext().any { (parent, child) ->
        parent.toString().equals("generated", ignoreCase = true) &&
            KOTLIN_WINRT_PLUGIN_GENERATED_SOURCE_OWNERS.any { owner ->
                child.toString().equals(owner, ignoreCase = true)
            }
    }
}
