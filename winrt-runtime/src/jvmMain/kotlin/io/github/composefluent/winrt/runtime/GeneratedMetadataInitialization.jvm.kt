package io.github.composefluent.winrt.runtime

import kotlin.jvm.java
import kotlin.reflect.KClass

private val generatedMetadataInitializedTypes = ConcurrentCacheSet<KClass<*>>()

internal actual fun platformTryInitializeGeneratedWinRtMetadata(type: KClass<*>) {
    if (!generatedMetadataInitializedTypes.add(type)) {
        return
    }
    runCatching {
        val javaClass = type.java
        // Generated projections use a named companion object `Metadata` whose initializer calls register().
        // This only initializes metadata for the KClass already supplied by the caller; it does not scan for types.
        Class.forName("${javaClass.name}\$Metadata", false, javaClass.classLoader)
        javaClass.getDeclaredField("Metadata").apply {
            isAccessible = true
        }.get(null)
    }
}
