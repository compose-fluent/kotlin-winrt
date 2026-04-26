package io.github.kitectlab.winrt.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Usage
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
                nugetCliVersion.set(extension.nugetCliVersion)
                nugetCliCacheDirectory.set(
                    project.layout.dir(
                        project.provider {
                            project.gradle.gradleUserHomeDir.resolve("caches/kotlin-winrt/nuget-cli")
                        },
                    ),
                )
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
        val extension = project.extensions.getByType(KotlinWinRtExtension::class.java)
        val identityTask = project.tasks.register(
            "generateWinRtIdentity",
            GenerateWinRtIdentityTask::class.java,
            Action<GenerateWinRtIdentityTask> {
                group = "kotlin-winrt"
                description = "Writes Kotlin WinRT projection identity metadata for downstream application packaging."
                outputFile.set(project.layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt.json"))
                metadataInputs.set(extension.metadataInputs)
                includeNamespaces.set(extension.includeNamespaces)
                includeTypes.set(extension.includeTypes)
                windowsSdkVersion.set(extension.windowsSdkVersion)
                includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
                nugetPackages.set(
                    project.provider {
                        extension.nugetPackages.map { pkg ->
                            "${pkg.packageId}@${pkg.version.get()}"
                        }
                    },
                )
            },
        )

        val identityElements = project.configurations.create(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION) {
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
            )
            outgoing.artifact(identityTask.flatMap { it.outputFile }) {
                builtBy(identityTask)
                type = "json"
            }
        }
        project.extensions.extraProperties["kotlinWinRtIdentityElements"] = identityElements.name
    }
}

class KotlinWinRtApplicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.extraProperties["kotlinWinRtModel"] = "application"
        val extension = project.extensions.getByType(KotlinWinRtExtension::class.java)
        val identityDependencies = project.configurations.create(KOTLIN_WINRT_IDENTITY_CONFIGURATION) {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
            )
        }
        project.configurations.matching { it.name == "implementation" }.configureEach {
            identityDependencies.extendsFrom(this)
        }
        val applicationIdentityTask = project.tasks.register(
            "generateWinRtApplicationIdentity",
            GenerateWinRtApplicationIdentityTask::class.java,
            Action<GenerateWinRtApplicationIdentityTask> {
                group = "kotlin-winrt"
                description = "Aggregates Kotlin WinRT identity metadata from application dependencies."
                outputFile.set(project.layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt-application.json"))
                metadataInputs.set(extension.metadataInputs)
                includeNamespaces.set(extension.includeNamespaces)
                includeTypes.set(extension.includeTypes)
                windowsSdkVersion.set(extension.windowsSdkVersion)
                includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
                nugetPackages.set(
                    project.provider {
                        extension.nugetPackages.map { pkg ->
                            "${pkg.packageId}@${pkg.version.get()}"
                        }
                    },
                )
                dependencyIdentityFiles.from(identityDependencies)
            },
        )
        project.extensions.extraProperties["kotlinWinRtIdentity"] = identityDependencies.name
        project.extensions.extraProperties["kotlinWinRtApplicationIdentityTask"] = applicationIdentityTask.name
    }
}

const val KOTLIN_WINRT_IDENTITY_CONFIGURATION: String = "kotlinWinRtIdentity"
const val KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION: String = "kotlinWinRtIdentityElements"
const val KOTLIN_WINRT_IDENTITY_USAGE: String = "kotlin-winrt-identity"
