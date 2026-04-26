package io.github.kitectlab.winrt.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer

class KotlinWinRtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kotlinWinRt", KotlinWinRtExtension::class.java)
        val generatedSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")
        val generateTask = project.tasks.register(
            "generateWinRtProjections",
            GenerateWinRtProjectionsTask::class.java,
            Action<GenerateWinRtProjectionsTask> { task ->
                task.group = "kotlin-winrt"
                task.description = "Generates Kotlin WinRT projections from Windows SDK and NuGet WinMD metadata."
                task.outputDirectory.set(generatedSources)
                task.metadataInputs.set(extension.metadataInputs)
                task.includeNamespaces.set(extension.includeNamespaces)
                task.includeTypes.set(extension.includeTypes)
                task.windowsSdkVersion.set(extension.windowsSdkVersion)
                task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
                task.nugetExecutable.set(extension.nugetExecutable)
                task.nugetCliVersion.set(extension.nugetCliVersion)
                task.nugetCliCacheDirectory.set(
                    project.layout.dir(
                        project.provider {
                            project.gradle.gradleUserHomeDir.resolve("caches/kotlin-winrt/nuget-cli")
                        },
                    ),
                )
                task.restoreNuGetPackages.set(extension.restoreNuGetPackages)
                task.useNuGetCliGlobalPackages.set(extension.useNuGetCliGlobalPackages)
                task.nugetGlobalPackagesRoots.set(extension.nugetGlobalPackagesRoots)
                task.nugetPackages.set(
                    project.provider {
                        extension.nugetPackages.map { pkg ->
                            "${pkg.packageId}@${pkg.version.get()}"
                        }
                    },
                )
            },
        )

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            addGeneratedSourcesToKotlinMain(project, generatedSources)
            project.tasks.matching { task -> task.name == "compileKotlin" }.configureEach(Action<Task> { task ->
                task.dependsOn(generateTask)
            })
        }

        project.plugins.withId("java") {
            project.extensions.configure(SourceSetContainer::class.java, Action<SourceSetContainer> {
                it.getByName("main").java.srcDir(generatedSources)
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
            Action<GenerateWinRtIdentityTask> { task ->
                task.group = "kotlin-winrt"
                task.description = "Writes Kotlin WinRT projection identity metadata for downstream application packaging."
                task.outputFile.set(project.layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt.json"))
                task.metadataInputs.set(extension.metadataInputs)
                task.includeNamespaces.set(extension.includeNamespaces)
                task.includeTypes.set(extension.includeTypes)
                task.windowsSdkVersion.set(extension.windowsSdkVersion)
                task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
                task.nugetPackages.set(
                    project.provider {
                        extension.nugetPackages.map { pkg ->
                            "${pkg.packageId}@${pkg.version.get()}"
                        }
                    },
                )
            },
        )

        val identityElements = project.configurations.create(
            KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION,
            Action { configuration ->
                configuration.isCanBeConsumed = true
                configuration.isCanBeResolved = false
                configuration.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
                )
                configuration.outgoing.artifact(identityTask.flatMap { it.outputFile }, Action { artifact ->
                    artifact.builtBy(identityTask)
                    artifact.type = "json"
                })
            },
        )
        project.extensions.extraProperties["kotlinWinRtIdentityElements"] = identityElements.name
    }
}

class KotlinWinRtApplicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.extraProperties["kotlinWinRtModel"] = "application"
        val extension = project.extensions.getByType(KotlinWinRtExtension::class.java)
        val identityDependencies = project.configurations.create(
            KOTLIN_WINRT_IDENTITY_CONFIGURATION,
            Action { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                configuration.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
                )
            },
        )
        project.configurations.matching { it.name == "implementation" }.configureEach(Action<Configuration> { configuration ->
            identityDependencies.extendsFrom(configuration)
        })
        val applicationIdentityTask = project.tasks.register(
            "generateWinRtApplicationIdentity",
            GenerateWinRtApplicationIdentityTask::class.java,
            Action<GenerateWinRtApplicationIdentityTask> { task ->
                task.group = "kotlin-winrt"
                task.description = "Aggregates Kotlin WinRT identity metadata from application dependencies."
                task.outputFile.set(project.layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt-application.json"))
                task.metadataInputs.set(extension.metadataInputs)
                task.includeNamespaces.set(extension.includeNamespaces)
                task.includeTypes.set(extension.includeTypes)
                task.windowsSdkVersion.set(extension.windowsSdkVersion)
                task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
                task.nugetPackages.set(
                    project.provider {
                        extension.nugetPackages.map { pkg ->
                            "${pkg.packageId}@${pkg.version.get()}"
                        }
                    },
                )
                task.dependencyIdentityFiles.from(identityDependencies)
            },
        )
        val runtimeAssetsDirectory = project.layout.buildDirectory.dir("kotlin-winrt/runtime-assets")
        val stageRuntimeAssetsTask = project.tasks.register(
            "stageWinRtRuntimeAssets",
            StageWinRtRuntimeAssetsTask::class.java,
            Action<StageWinRtRuntimeAssetsTask> { task ->
                task.group = "kotlin-winrt"
                task.description = "Stages WinRT NuGet runtime and resource assets for application execution."
                task.outputDirectory.set(runtimeAssetsDirectory)
                task.nugetPackages.set(
                    project.provider {
                        extension.nugetPackages.map { pkg ->
                            "${pkg.packageId}@${pkg.version.get()}"
                        }
                    },
                )
                task.nugetGlobalPackagesRoots.set(extension.nugetGlobalPackagesRoots)
                task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
                task.dependencyIdentityFiles.from(identityDependencies)
            },
        )
        project.plugins.withId("java") {
            project.tasks.matching { it.name == "processResources" }.configureEach(Action<Task> { task ->
                task.dependsOn(stageRuntimeAssetsTask)
                if (task is Copy) {
                    task.from(stageRuntimeAssetsTask.flatMap { it.outputDirectory }, Action<CopySpec> { spec ->
                        spec.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY)
                    })
                }
            })
        }
        project.plugins.withId("application") {
            project.extensions.configure(DistributionContainer::class.java, Action<DistributionContainer> { distributions ->
                distributions.named("main").configure { distribution ->
                    distribution.contents(Action<CopySpec> { contents ->
                        contents.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY, Action<CopySpec> { spec ->
                            spec.from(stageRuntimeAssetsTask.flatMap { it.outputDirectory })
                        })
                    })
                }
            })
        }
        project.extensions.extraProperties["kotlinWinRtIdentity"] = identityDependencies.name
        project.extensions.extraProperties["kotlinWinRtApplicationIdentityTask"] = applicationIdentityTask.name
        project.extensions.extraProperties["kotlinWinRtRuntimeAssetsTask"] = stageRuntimeAssetsTask.name
    }
}

const val KOTLIN_WINRT_IDENTITY_CONFIGURATION: String = "kotlinWinRtIdentity"
const val KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION: String = "kotlinWinRtIdentityElements"
const val KOTLIN_WINRT_IDENTITY_USAGE: String = "kotlin-winrt-identity"
const val KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY: String = "kotlin-winrt-runtime-assets"

private fun addGeneratedSourcesToKotlinMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByName("kotlin") ?: return
    val sourceSets = kotlinExtension.callNoArg("getSourceSets") as? org.gradle.api.NamedDomainObjectContainer<*> ?: return
    val mainSourceSet = sourceSets.getByName("main")
    val kotlinSourceDirectorySet = mainSourceSet.callNoArg("getKotlin") ?: return
    kotlinSourceDirectorySet.callOneArg("srcDir", generatedSources)
}

private fun Any.callNoArg(name: String): Any? =
    javaClass.methods.firstOrNull { method ->
        method.name == name && method.parameterCount == 0
    }?.invoke(this)

private fun Any.callOneArg(name: String, argument: Any): Any? =
    javaClass.methods.firstOrNull { method ->
        method.name == name && method.parameterCount == 1
    }?.invoke(this, argument)
