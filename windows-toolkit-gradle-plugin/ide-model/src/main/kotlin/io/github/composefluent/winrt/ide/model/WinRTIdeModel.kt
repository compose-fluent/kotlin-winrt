package io.github.composefluent.winrt.ide.model

import java.io.Serializable

/** Gradle Tooling API contract. Configuration facts, never resolved outputs or secrets. */
interface WinRTIdeModel : Serializable {
    val schemaVersion: Int
    val isEnabled: Boolean
    val projectPath: String
    val projectDirectory: String
    val buildDirectory: String
    val kotlinVersion: String
    val windowsSdkVersion: String
    val sourceSets: List<SourceSet>
    val targets: List<Target>
    val nuGetPackages: List<NuGetPackage>
    val manifestFiles: List<String>
    val xamlCompilations: List<XamlCompilation>

    /** Paths to declaration-pass tasks; their outputs need not exist at sync time. */
    interface XamlCompilation : Serializable {
        val taskName: String
        val sourceRoots: List<String>
        val declarationsFile: String
        val inputFile: String
        val compilerDirectory: String
        val metadataIndexFile: String
    }

    interface SourceSet : Serializable {
        val name: String
        val kotlinRoots: List<String>
        val dependsOn: List<String>
        /** Least to most specific, exactly as used by AppX resource staging. */
        val appxResourceRoots: List<String>
    }

    interface Target : Serializable {
        val name: String
        val platform: String
        val sourceSets: List<String>
    }

    interface NuGetPackage : Serializable {
        val id: String
        val version: String
        val isGenerateProjection: Boolean
    }

    companion object {
        const val SCHEMA_VERSION: Int = 2
    }
}
