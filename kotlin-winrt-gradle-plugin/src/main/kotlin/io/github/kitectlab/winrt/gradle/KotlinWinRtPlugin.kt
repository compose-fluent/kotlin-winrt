package io.github.kitectlab.winrt.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinWinRtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kotlinWinRt", KotlinWinRtExtension::class.java)
        val generatedSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")
        val generateTask = project.tasks.register(
            "generateWinRtProjections",
            GenerateWinRtProjectionsTask::class.java,
            Action<GenerateWinRtProjectionsTask> {
                group = "kotlin-winrt"
                description = "Generates Kotlin WinRT projections from Windows SDK and NuGet WinMD metadata."
                outputDirectory.set(generatedSources)
                metadataInputs.set(extension.metadataInputs)
                includeNamespaces.set(extension.includeNamespaces)
                includeTypes.set(extension.includeTypes)
                windowsSdkVersion.set(extension.windowsSdkVersion)
                includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
                nugetExecutable.set(extension.nugetExecutable)
                restoreNuGetPackages.set(extension.restoreNuGetPackages)
                useNuGetCliGlobalPackages.set(extension.useNuGetCliGlobalPackages)
                nugetGlobalPackagesRoots.set(extension.nugetGlobalPackagesRoots)
                nugetPackages.set(
                    project.provider {
                        extension.nugetPackages.map { pkg ->
                            "${pkg.packageId}@${pkg.version.get()}"
                        }
                    },
                )
            },
        )

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.extensions.configure(KotlinJvmProjectExtension::class.java, Action<KotlinJvmProjectExtension> {
                sourceSets.getByName("main").kotlin.srcDir(generatedSources)
            })
            project.tasks.matching { task -> task.name == "compileKotlin" }.configureEach(Action<Task> {
                dependsOn(generateTask)
            })
        }

        project.plugins.withId("java") {
            project.extensions.configure(SourceSetContainer::class.java, Action<SourceSetContainer> {
                getByName("main").java.srcDir(generatedSources)
            })
        }
    }
}

class KotlinWinRtLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.extraProperties["kotlinWinRtModel"] = "library"
    }
}

class KotlinWinRtApplicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.extraProperties["kotlinWinRtModel"] = "application"
    }
}
