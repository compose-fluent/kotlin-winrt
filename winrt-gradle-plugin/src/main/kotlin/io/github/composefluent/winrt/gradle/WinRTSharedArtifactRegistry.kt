package io.github.composefluent.winrt.gradle

import org.gradle.api.tasks.TaskProvider
import java.security.MessageDigest

/**
 * Project-local producer registry used to share compatible artifacts while keeping each
 * application variant's materialized layout independent.
 */
internal class WinRTSharedArtifactRegistry {
    val runtimeImageProducers: MutableMap<String, TaskProvider<PrepareWinRTJvmRuntimeImageTask>> = linkedMapOf()
    val authoringHostProducers: MutableMap<String, TaskProvider<BuildWinRTAuthoringHostTask>> = linkedMapOf()
}

/** Returns a deterministic, task-name-safe identity for a set of effective producer inputs. */
internal fun winRTSharedArtifactKey(prefix: String, parts: Iterable<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        digest.update(part.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    val hex = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "$prefix-${hex.take(20)}"
}

internal fun normalizedWinRTTaskIdentity(value: String): String =
    value.trim().replace('\\', '/').lowercase()
