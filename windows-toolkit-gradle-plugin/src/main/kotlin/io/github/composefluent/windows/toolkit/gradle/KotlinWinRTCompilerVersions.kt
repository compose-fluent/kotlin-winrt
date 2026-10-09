package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.GradleException
import java.util.Properties

/** Exact compiler API targets, shared with the compiler builds and their publications. */
internal object KotlinWinRTCompilerVersions {
    private val versions = Properties().apply {
        requireNotNull(KotlinWinRTCompilerVersions::class.java.getResourceAsStream("/kotlin-winrt/compiler-versions.properties")) {
            "kotlin-winrt compiler version matrix is missing"
        }.use(::load)
    }
    val default: String = versions.getProperty("default")
    val supported: List<String> = versions.getProperty("supported").split(',')

    fun artifact(module: String, kotlinVersion: String): String {
        validate(kotlinVersion)
        return module + if (kotlinVersion == default) "" else "-kotlin-$kotlinVersion"
    }

    fun projectPath(base: String, kotlinVersion: String): String {
        validate(kotlinVersion)
        val module = if (base.endsWith(":callsite-lowering")) "lowering" else "compiler"
        return base + if (kotlinVersion == default) "" else ":$module-kotlin-${kotlinVersion.replace('.', '-')}"
    }

    fun validate(kotlinVersion: String) {
        if (kotlinVersion !in supported) {
            throw GradleException(
                "kotlin-winrt does not support Kotlin $kotlinVersion. " +
                    "Supported Kotlin compiler versions: ${supported.joinToString()}. " +
                    "Use one of these exact versions or update the Windows toolkit plugin to a release that supports Kotlin $kotlinVersion.",
            )
        }
    }
}
