package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.compiler.KotlinWinRtCommandLineProcessor
import io.github.composefluent.winrt.metadata.WinRtMetadataSource
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageResolver
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.runtime.Guid
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.JavaExec
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import java.io.File
import java.nio.file.Path
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

class KotlinWinRtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("winRt", WinRtExtension::class.java, project)
        configureWinRtRuntimeDependency(project)
        configureWinRtGeneration(project, extension)
        configureWinRtLibraryModel(project, extension)
        configureWinRtApplicationModel(project, extension)
    }
}

const val KOTLIN_WINRT_IDENTITY_CONFIGURATION: String = "kotlinWinRtIdentity"
const val KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION: String = "kotlinWinRtIdentityElements"
const val KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION: String = "kotlinWinRtCompilerPlugin"
const val KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION: String = "kotlinWinRtGeneratorWorker"
const val KOTLIN_WINRT_IDENTITY_USAGE: String = "kotlin-winrt-identity"
const val KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY: String = "kotlin-winrt-runtime-assets"
private const val KOTLIN_WINRT_COMPILER_PLUGIN_ID: String = "io.github.composefluent.winrt.compiler"
private const val KOTLIN_WINRT_LIBRARY_DEPENDENCY_IDENTITY_CONFIGURATION: String = "kotlinWinRtLibraryDependencyIdentity"

private fun configureWinRtRuntimeDependency(project: Project) {
    val configuredRuntimeConfigurations = mutableSetOf<String>()
    val configuredAuthoringConfigurations = mutableSetOf<String>()
    fun addRuntimeDependency(configurationName: String) {
        if (configuredRuntimeConfigurations.add(configurationName)) {
            project.dependencies.add(
                configurationName,
                kotlinWinRtRuntimeDependency(project),
            )
        }
    }
    fun addAuthoringDependency(configurationName: String) {
        if (configuredAuthoringConfigurations.add(configurationName)) {
            project.dependencies.add(
                configurationName,
                kotlinWinRtAuthoringDependency(project),
            )
        }
    }
    project.configurations
        .matching { configuration ->
            configuration.name == "implementation" ||
                configuration.name == "commonMainImplementation"
        }
        .configureEach { configuration ->
            addRuntimeDependency(configuration.name)
            if (configuration.name == "implementation") {
                addAuthoringDependency(configuration.name)
            }
        }
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
            kotlin.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
                val sourceSet = target.compilations.getByName("main").defaultSourceSet
                addAuthoringDependency(sourceSet.implementationConfigurationName)
            }
        }
    }
}

private fun configureWinRtLibraryModel(
    project: Project,
    extension: WinRtExtension,
) {
    project.extensions.extraProperties["kotlinWinRtModel"] = project.provider {
        if (extension.applicationEnabled.get()) "application" else "library"
    }
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
            task.projectionRegistrarFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-support/projection-registrar.tsv",
                ),
            )
            task.excludeNamespaces.set(extension.excludeNamespaces)
            task.excludeTypes.set(extension.excludeTypes)
            task.additionExcludeNamespaces.set(extension.additionExcludeNamespaces)
            task.windowsSdkVersion.set(extension.windowsSdkVersion)
            task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
            task.nugetPackages.set(
                project.provider {
                    allNuGetPackageSpecs(extension)
                },
            )
            task.runtimeAssets.set(extension.runtimeAssets)
            task.authoringMetadataIndexFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/metadata-index.tsv",
                ),
            )
            task.compilerSupportManifestFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-support/compiler-support.tsv",
                ),
            )
            task.dependsOn("generateWinRtProjections")
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
    project.plugins.withId("maven-publish") {
        project.components.withType(AdhocComponentWithVariants::class.java).configureEach { component ->
            component.addVariantsFromConfiguration(identityElements) { details ->
                details.mapToOptional()
            }
        }
    }
    configureWinRtIdentityProjectDependencies(project, identityElements, includeExternalModules = false)
    val dependencyIdentities = project.configurations.create(
        KOTLIN_WINRT_LIBRARY_DEPENDENCY_IDENTITY_CONFIGURATION,
        Action { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
            )
        },
    )
    configureWinRtIdentityProjectDependencies(project, dependencyIdentities, includeExternalModules = true)
    val dependencyIdentityFiles = kotlinWinRtIdentityFiles(project, dependencyIdentities)
    project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.named("mergeWinRtCompilerSupport", MergeWinRtCompilerSupportTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.withType(GenerateWinRtCompilerAuthoredTypeDetailsTask::class.java).configureEach { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.extensions.extraProperties["kotlinWinRtIdentityElements"] = identityElements.name
    extension.whenApplicationConfigured {
        identityTask.configure { task ->
            task.enabled = false
        }
        identityElements.isCanBeConsumed = false
    }
    project.plugins.withId("java") {
        identityTask.configure { task ->
            task.authoredTargetArtifactFiles.from(
                project.tasks.named("jar", Jar::class.java).flatMap { it.archiveFile },
            )
        }
        project.tasks.matching { it.name == "processResources" }.configureEach(Action<Task> { task ->
            task.dependsOn("generateWinRtProjections")
        })
    }
}

private fun configureWinRtApplicationModel(
    project: Project,
    extension: WinRtExtension,
) {
    extension.whenApplicationConfigured {
        configureWinRtApplicationTasks(project, extension)
    }
}

private fun configureWinRtApplicationTasks(
    project: Project,
    extension: WinRtExtension,
) {
    if (project.configurations.findByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION) != null) {
        return
    }
    val unpackagedMode = extension.application.packageMode.map { it == WinRtApplicationPackageMode.Unpackaged }
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
    configureWinRtIdentityProjectDependencies(project, identityDependencies, includeExternalModules = true)
    val dependencyIdentityFiles = kotlinWinRtIdentityFiles(project, identityDependencies)
    project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.named("mergeWinRtCompilerSupport", MergeWinRtCompilerSupportTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.withType(GenerateWinRtCompilerAuthoredTypeDetailsTask::class.java).configureEach { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
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
            task.excludeNamespaces.set(extension.excludeNamespaces)
            task.excludeTypes.set(extension.excludeTypes)
            task.additionExcludeNamespaces.set(extension.additionExcludeNamespaces)
            task.windowsSdkVersion.set(extension.windowsSdkVersion)
            task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
            task.nugetPackages.set(
                project.provider {
                    allNuGetPackageSpecs(extension)
                },
            )
            task.runtimeAssets.set(extension.runtimeAssets)
            task.dependencyIdentityFiles.from(dependencyIdentityFiles)
        },
    )
    val runtimeAssetsDirectory = project.layout.buildDirectory.dir("kotlin-winrt/runtime-assets")
    val buildAuthoringHostTask = project.tasks.register(
        "buildWinRtAuthoringHost",
        BuildWinRtAuthoringHostTask::class.java,
        Action<BuildWinRtAuthoringHostTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Builds reference-aligned native JVM host DLLs for authored WinRT activation."
            task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/authoring-host/bin"))
            task.generatedSourceDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/authoring-host/src"))
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.javaHome.set(project.provider { System.getProperty("java.home") })
            task.commandWorkingDirectory.set(project.layout.projectDirectory)
            task.dependencyIdentityFiles.from(dependencyIdentityFiles)
        },
    )
    val resolveRuntimeNuGetPackagesTask = project.tasks.register(
        "resolveWinRtRuntimeNuGetPackages",
        ResolveWinRtRuntimeNuGetPackagesTask::class.java,
        Action<ResolveWinRtRuntimeNuGetPackagesTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Resolves WinRT NuGet runtime package roots for application asset staging."
            task.outputFile.set(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/runtime-nuget-packages/kotlin-winrt-runtime-nuget-packages.json",
                ),
            )
            task.nugetPackages.set(
                project.provider {
                    allNuGetPackageSpecs(extension)
                },
            )
            task.dependencyIdentityFiles.from(dependencyIdentityFiles)
            val dependencyNuGetPackages = dependencyIdentityFiles.elements.map { elements ->
                elements.map { it.asFile }.flatMap(::readNuGetPackages)
            }
            task.existingPackageContentFiles.from(
                task.nugetPackages.zip(extension.nugetGlobalPackagesRoots) { packageSpecs, explicitGlobalPackagesRoots ->
                    packageSpecs to explicitGlobalPackagesRoots
                }.zip(dependencyNuGetPackages) { packageInput, dependencyPackageSpecs ->
                    existingNuGetPackageContentRoots(
                        packageSpecs = packageInput.first + dependencyPackageSpecs,
                        explicitGlobalPackagesRoots = packageInput.second,
                    )
                },
            )
            task.nugetGlobalPackagesRoots.set(extension.nugetGlobalPackagesRoots)
            task.useNuGetCliGlobalPackages.set(extension.useNuGetCliGlobalPackages)
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
        },
    )
    val stageRuntimeAssetsTask = project.tasks.register(
        "stageWinRtRuntimeAssets",
        StageWinRtRuntimeAssetsTask::class.java,
        Action<StageWinRtRuntimeAssetsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Stages WinRT NuGet runtime and resource assets for application execution."
            task.outputDirectory.set(runtimeAssetsDirectory)
            task.nugetPackages.set(
                project.provider {
                    allNuGetPackageSpecs(extension)
                },
            )
            task.runtimeAssets.set(extension.runtimeAssets)
            task.runtimeAssetFiles.from(extension.runtimeAssets)
            task.dependencyRuntimeAssetFiles.from(
                dependencyIdentityFiles.elements.map { elements ->
                    elements.map { it.asFile }.flatMap(::readRuntimeAssets)
                },
            )
            val dependencyNuGetPackages = dependencyIdentityFiles.elements.map { elements ->
                elements.map { it.asFile }.flatMap(::readNuGetPackages)
            }
            task.nugetPackageContentFiles.from(
                task.nugetPackages.zip(task.nugetGlobalPackagesRoots) { packageSpecs, explicitGlobalPackagesRoots ->
                    packageSpecs to explicitGlobalPackagesRoots
                }.zip(dependencyNuGetPackages) { packageInput, dependencyPackageSpecs ->
                    existingNuGetPackageContentRoots(
                        packageSpecs = packageInput.first + dependencyPackageSpecs,
                        explicitGlobalPackagesRoots = packageInput.second,
                    )
                },
            )
            task.resolvedNuGetPackageManifestFiles.from(resolveRuntimeNuGetPackagesTask.flatMap { it.outputFile })
            task.nugetGlobalPackagesRoots.set(extension.nugetGlobalPackagesRoots)
            task.useNuGetCliGlobalPackages.set(extension.useNuGetCliGlobalPackages)
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
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.generateProjectPri.set(extension.application.generateProjectPri)
            task.projectPriIndexName.set(project.provider { extension.application.projectPriIndexName.orNull.orEmpty() })
            task.projectPriFallbackIndexName.set(project.name)
            task.projectPriInitialPath.set(extension.application.projectPriInitialPath)
            task.projectPriDefaultLanguage.set(extension.application.projectPriDefaultLanguage)
            task.projectPriDefaultQualifiers.set(extension.application.projectPriDefaultQualifiers)
            task.enableDefaultProjectPriResources.set(extension.application.enableDefaultProjectPriResources)
            task.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            task.defaultProjectPriResourceFiles.from(
                project.provider {
                    if (extension.application.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.resw")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriLayoutFiles.from(
                project.provider {
                    if (extension.application.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.xaml")
                            spec.include("**/*.xbf")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriContentFiles.from(
                project.provider {
                    if (extension.application.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.png")
                            spec.include("**/*.bmp")
                            spec.include("**/*.jpg")
                            spec.include("**/*.dds")
                            spec.include("**/*.tif")
                            spec.include("**/*.tga")
                            spec.include("**/*.gif")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.appxManifestFiles.from(extension.application.appxManifestFiles)
            task.projectPriResourceFiles.from(extension.application.projectPriResourceFiles)
            task.projectPriLayoutFiles.from(extension.application.projectPriLayoutFiles)
            task.projectPriContentFiles.from(extension.application.projectPriContentFiles)
            task.projectPriEmbedFiles.from(extension.application.projectPriEmbedFiles)
            task.projectPriTargetPaths.set(extension.application.projectPriTargetPaths)
            task.projectPriExcludedFromBuildPaths.set(extension.application.projectPriExcludedFromBuildPaths)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.dependencyIdentityFiles.from(dependencyIdentityFiles)
            task.authoredTargetArtifactFiles.from(
                dependencyIdentityFiles.elements.map { elements ->
                    elements.map { it.asFile }.flatMap(::readAuthoredTargetArtifacts)
                },
            )
            task.authoredHostDllFiles.from(project.fileTree(buildAuthoringHostTask.flatMap { it.outputDirectory }) { spec ->
                spec.include("*.dll")
            })
            task.dependsOn("generateWinRtProjections")
            task.dependsOn(buildAuthoringHostTask)
            task.dependsOn(resolveRuntimeNuGetPackagesTask)
        },
    )
    val stageApplicationPackageTask = project.tasks.register(
        "stageWinRtApplicationPackage",
        StageWinRtApplicationPackageTask::class.java,
        Action<StageWinRtApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Stages WinRT application package resources and generates the application PRI."
            task.runtimeAssetsDirectory.set(stageRuntimeAssetsTask.flatMap { it.outputDirectory })
            task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/application-package"))
            task.generateProjectPri.set(extension.application.generateProjectPri)
            task.projectPriIndexName.set(project.provider { extension.application.projectPriIndexName.orNull.orEmpty() })
            task.projectPriFallbackIndexName.set(project.name)
            task.projectPriInitialPath.set(extension.application.projectPriInitialPath)
            task.projectPriDefaultLanguage.set(extension.application.projectPriDefaultLanguage)
            task.projectPriDefaultQualifiers.set(extension.application.projectPriDefaultQualifiers)
            task.enableDefaultProjectPriResources.set(extension.application.enableDefaultProjectPriResources)
            task.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            task.defaultProjectPriResourceFiles.from(
                project.provider {
                    if (extension.application.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.resw")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriLayoutFiles.from(
                project.provider {
                    if (extension.application.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.xaml")
                            spec.include("**/*.xbf")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriContentFiles.from(
                project.provider {
                    if (extension.application.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.png")
                            spec.include("**/*.bmp")
                            spec.include("**/*.jpg")
                            spec.include("**/*.dds")
                            spec.include("**/*.tif")
                            spec.include("**/*.tga")
                            spec.include("**/*.gif")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.appxManifestFiles.from(extension.application.appxManifestFiles)
            task.projectPriResourceFiles.from(extension.application.projectPriResourceFiles)
            task.projectPriLayoutFiles.from(extension.application.projectPriLayoutFiles)
            task.projectPriContentFiles.from(extension.application.projectPriContentFiles)
            task.projectPriEmbedFiles.from(extension.application.projectPriEmbedFiles)
            task.packagePayloadFiles.from(extension.application.packagePayloadFiles)
            task.projectPriTargetPaths.set(extension.application.projectPriTargetPaths)
            task.projectPriExcludedFromBuildPaths.set(extension.application.projectPriExcludedFromBuildPaths)
            task.makePriExecutable.set(extension.application.makePriExecutable)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.dependsOn(stageRuntimeAssetsTask)
        },
    )
    val applicationHostTask = project.tasks.register(
        "buildWinRtApplicationHost",
        BuildWinRtApplicationHostTask::class.java,
        Action<BuildWinRtApplicationHostTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Builds the native Kotlin/WinRT JVM application host with Windows App SDK deployment initialization."
            task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/application-host/bin"))
            task.generatedSourceDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/application-host/src"))
            task.packageMode.set(project.provider { extension.application.packageMode.get().name })
            task.console.set(extension.application.console)
            task.executableBaseName.set(project.name)
            task.javaHome.set(project.provider { System.getProperty("java.home") })
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.commandWorkingDirectory.set(project.layout.projectDirectory)
            task.runtimeAssetsDirectory.from(stageApplicationPackageTask.flatMap { it.outputDirectory })
            task.dependsOn(stageApplicationPackageTask)
        },
    )
    val applicationHostExecutable = applicationHostTask.flatMap { task ->
        task.outputDirectory.file(task.executableBaseName.map { executableBaseName -> "$executableBaseName.exe" })
    }
    val runApplicationHostTask = project.tasks.register(
        "runWinRtApplicationHost",
        Exec::class.java,
        Action<Exec> { task ->
            task.group = "application"
            task.description = "Runs the native Kotlin/WinRT JVM application host."
            task.dependsOn(applicationHostTask)
            task.onlyIf {
                System.getProperty("os.name").contains("Windows", ignoreCase = true)
            }
            task.doFirst {
                val hostExecutable = applicationHostExecutable.get().asFile
                val hostDirectory = hostExecutable.parentFile
                task.executable = hostExecutable.absolutePath
                task.workingDir = hostDirectory
            }
        },
    )
    val packageApplicationTask = project.tasks.register(
        "packageWinRtApplication",
        PackageWinRtApplicationTask::class.java,
        Action<PackageWinRtApplicationTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Packages the staged WinRT application payload into an appx/msix package."
            task.packageDirectory.set(stageApplicationPackageTask.flatMap { it.outputDirectory })
            task.outputFile.set(
                extension.application.packageOutputFile.orElse(
                    project.layout.buildDirectory.file("kotlin-winrt/packages/${project.name}.msix"),
                ),
            )
            task.generatePackage.set(extension.application.generatePackage)
            task.makeAppxExecutable.set(extension.application.makeAppxExecutable)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.onlyIf { extension.application.packageMode.get() == WinRtApplicationPackageMode.Packaged }
            task.dependsOn(stageApplicationPackageTask)
        },
    )
    val verifyPackageTask = project.tasks.register(
        "verifyWinRtApplicationPackage",
        VerifyWinRtApplicationPackageTask::class.java,
        Action<VerifyWinRtApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Verifies the WinRT application appx/msix package layout with makeappx."
            task.packageFile.set(packageApplicationTask.flatMap { it.outputFile })
            task.markerFile.set(project.layout.buildDirectory.file("kotlin-winrt/packages/${project.name}.verify.marker"))
            task.unpackDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/package-verification/${project.name}"))
            task.verifyPackage.set(extension.application.verifyPackage)
            task.makeAppxExecutable.set(extension.application.makeAppxExecutable)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.onlyIf {
                extension.application.packageMode.get() == WinRtApplicationPackageMode.Packaged &&
                    extension.application.generatePackage.get() &&
                    extension.application.verifyPackage.get()
            }
            task.dependsOn(packageApplicationTask)
        },
    )
    val signPackageTask = project.tasks.register(
        "signWinRtApplicationPackage",
        SignWinRtApplicationPackageTask::class.java,
        Action<SignWinRtApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Signs the WinRT application appx/msix package with signtool."
            task.inputPackageFile.set(packageApplicationTask.flatMap { it.outputFile })
            task.outputFile.set(
                extension.application.signedPackageOutputFile.orElse(
                    project.layout.buildDirectory.file("kotlin-winrt/packages/${project.name}-signed.msix"),
                ),
            )
            task.signPackage.set(extension.application.signPackage)
            task.signToolExecutable.set(extension.application.signToolExecutable)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.signingCertificateThumbprint.set(extension.application.signingCertificateThumbprint)
            task.signingCertificateFile.set(extension.application.signingCertificateFile)
            task.signingCertificatePassword.set(extension.application.signingCertificatePassword)
            task.signingTimestampUrl.set(extension.application.signingTimestampUrl)
            task.signingHashAlgorithm.set(extension.application.signingHashAlgorithm)
            task.onlyIf {
                extension.application.packageMode.get() == WinRtApplicationPackageMode.Packaged &&
                    extension.application.signPackage.get()
            }
            task.dependsOn(packageApplicationTask)
            task.dependsOn(verifyPackageTask)
        },
    )
    val installPackageTask = project.tasks.register(
        "installWinRtApplicationPackage",
        InstallWinRtApplicationPackageTask::class.java,
        Action<InstallWinRtApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Installs the WinRT application appx/msix package for local test runs."
            val defaultInstallPackageFile = extension.application.signPackage.flatMap { signPackage ->
                if (signPackage) {
                    signPackageTask.flatMap { it.outputFile }
                } else {
                    packageApplicationTask.flatMap { it.outputFile }
                }
            }
            task.packageFile.set(
                extension.application.installPackageFile.orElse(defaultInstallPackageFile),
            )
            task.installPackage.set(extension.application.installPackage)
            task.powerShellExecutable.set(extension.application.installPowerShellExecutable)
            task.forceApplicationShutdown.set(extension.application.installForceApplicationShutdown)
            task.onlyIf {
                extension.application.packageMode.get() == WinRtApplicationPackageMode.Packaged &&
                    extension.application.installPackage.get()
            }
            task.dependsOn(packageApplicationTask)
            task.dependsOn(verifyPackageTask)
            task.dependsOn(signPackageTask)
        },
    )
    project.plugins.withId("java") {
        project.extensions.configure(SourceSetContainer::class.java, Action<SourceSetContainer> { sourceSets ->
            applicationHostTask.configure { task ->
                task.runtimeClasspath.from(sourceSets.getByName("main").runtimeClasspath)
            }
        })
        project.tasks.named("jar", Jar::class.java).let { jar ->
            applicationHostTask.configure { task ->
                task.runtimeClasspath.from(jar.flatMap { it.archiveFile })
                task.dependsOn(jar)
            }
        }
        project.tasks.matching { it.name == "processResources" }.configureEach(Action<Task> { task ->
            if (unpackagedMode.get()) {
                task.dependsOn(stageApplicationPackageTask)
            }
            if (task is Copy) {
                if (unpackagedMode.get()) {
                    task.from(stageApplicationPackageTask.flatMap { it.outputDirectory }, Action<CopySpec> { spec ->
                        spec.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY)
                    })
                }
            }
        })
        project.tasks.withType(JavaExec::class.java).configureEach(Action<JavaExec> { task ->
            if (unpackagedMode.get()) {
                task.dependsOn(stageApplicationPackageTask)
                task.jvmArgumentProviders.add(
                    RuntimeAssetsRootJvmArgumentProvider(
                        stageApplicationPackageTask.flatMap { it.outputDirectory }.map { it.asFile.absolutePath },
                    ),
                )
            }
        })
    }
    project.plugins.withId("application") {
        project.extensions.configure(JavaApplication::class.java, Action<JavaApplication> { application ->
            applicationHostTask.configure { task ->
                task.mainClass.set(extension.application.mainClass.orElse(application.mainClass))
            }
        })
        if (unpackagedMode.get()) {
            project.extensions.configure(DistributionContainer::class.java, Action<DistributionContainer> { distributions ->
                distributions.getByName("main").contents(Action<CopySpec> { contents ->
                    contents.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY, Action<CopySpec> { spec ->
                        spec.from(stageApplicationPackageTask.flatMap { it.outputDirectory })
                    })
                    contents.into("kotlin-winrt-application-host", Action<CopySpec> { spec ->
                        spec.from(applicationHostTask.flatMap { it.outputDirectory })
                    })
                })
            })
        }
    }
    applicationHostTask.configure { task ->
        task.mainClass.convention(extension.application.mainClass)
    }
    project.extensions.extraProperties["kotlinWinRtIdentity"] = identityDependencies.name
    project.extensions.extraProperties["kotlinWinRtApplicationIdentityTask"] = applicationIdentityTask.name
    project.extensions.extraProperties["kotlinWinRtRuntimeNuGetPackagesTask"] = resolveRuntimeNuGetPackagesTask.name
    project.extensions.extraProperties["kotlinWinRtRuntimeAssetsTask"] = stageRuntimeAssetsTask.name
    project.extensions.extraProperties["kotlinWinRtApplicationPackageTask"] = stageApplicationPackageTask.name
    project.extensions.extraProperties["kotlinWinRtApplicationHostTask"] = applicationHostTask.name
    project.extensions.extraProperties["kotlinWinRtRunApplicationHostTask"] = runApplicationHostTask.name
    project.extensions.extraProperties["kotlinWinRtPackageTask"] = packageApplicationTask.name
    project.extensions.extraProperties["kotlinWinRtVerifyPackageTask"] = verifyPackageTask.name
    project.extensions.extraProperties["kotlinWinRtSignPackageTask"] = signPackageTask.name
    project.extensions.extraProperties["kotlinWinRtInstallPackageTask"] = installPackageTask.name
}

private class RuntimeAssetsRootJvmArgumentProvider(
    @get:Input
    private val runtimeAssetsRoot: Provider<String>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Dkotlin.winrt.runtimeAssetsRoot=${runtimeAssetsRoot.get()}")
}

private fun configureWinRtGeneration(
    project: Project,
    extension: BaseWinRtExtension,
) {
    val generatedSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")
    val generatedAuthoringSources = project.layout.buildDirectory.dir("generated/kotlin-winrt-authoring/src/main/kotlin")
    val compilerPluginClasspath = kotlinWinRtCompilerPluginClasspath(project)
    val generatorWorkerClasspath = kotlinWinRtGeneratorWorkerClasspath(project)
    val authoringTargetArtifactName = kotlinWinRtAuthoringTargetArtifactName(project)
    val generateTask = project.tasks.register(
        "generateWinRtProjections",
        GenerateWinRtProjectionsTask::class.java,
        Action<GenerateWinRtProjectionsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Generates Kotlin WinRT projections from Windows SDK and NuGet WinMD metadata."
            task.outputDirectory.set(generatedSources)
            task.authoringTypeDetailsOutputDirectory.set(generatedAuthoringSources)
            task.metadataInputs.set(extension.metadataInputs)
            task.metadataInputFiles.from(
                project.provider {
                    explicitMetadataInputFiles(extension.metadataInputs.get())
                },
            )
            task.includeNamespaces.set(extension.includeNamespaces)
            task.includeTypes.set(extension.includeTypes)
            task.excludeNamespaces.set(extension.excludeNamespaces)
            task.excludeTypes.set(extension.excludeTypes)
            task.additionExcludeNamespaces.set(extension.additionExcludeNamespaces)
            task.windowsSdkVersion.set(extension.windowsSdkVersion)
            task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
            task.generateWindowsSdkProjection.set(extension.generateWindowsSdkProjection)
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
                    projectionNuGetPackageSpecs(extension)
                },
            )
            task.nugetPackageContentFiles.from(
                task.nugetPackages.zip(extension.nugetGlobalPackagesRoots) { packageSpecs, explicitGlobalPackagesRoots ->
                    existingNuGetPackageContentRoots(
                        packageSpecs = packageSpecs,
                        explicitGlobalPackagesRoots = explicitGlobalPackagesRoots,
                    )
                },
            )
            task.projectModel.set(
                project.provider {
                    if ((extension as? WinRtExtension)?.applicationEnabled?.get() == true) "application" else "library"
                },
            )
            task.authoringAssemblyName.set(project.name)
            task.authoringTargetArtifactName.set(authoringTargetArtifactName)
            task.authoringScannerJvmArgs.convention(
                listOf(
                    "-Xmx128m",
                    "-Xss512k",
                    "-XX:+UseSerialGC",
                    "-XX:ReservedCodeCacheSize=32m",
                ),
            )
            task.generatorWorkerJvmArgs.convention(
                listOf(
                    "-Xmx1024m",
                    "-XX:+UseSerialGC",
                    "-Dfile.encoding=UTF-8",
                ),
            )
            task.generatorWorkerClasspath.from(generatorWorkerClasspath)
            task.authoringScannerClasspath.from(compilerPluginClasspath)
            task.authoringScannerClasspath.from(kotlinWinRtAuthoringScannerRuntimeClasspath(project))
            task.sourceRoots.from(
                project.provider {
                    val generatedSourcesPath = generatedSources.get().asFile.toPath().toAbsolutePath().normalize()
                    val generatedAuthoringSourcesPath =
                        generatedAuthoringSources.get().asFile.toPath().toAbsolutePath().normalize()
                    kotlinMainSourceDirs(project).filterNot { sourceDir ->
                        val normalizedSourceDir = sourceDir.toPath().toAbsolutePath().normalize()
                        normalizedSourceDir.startsWith(generatedSourcesPath) ||
                            normalizedSourceDir.startsWith(generatedAuthoringSourcesPath)
                    }
                },
            )
        },
    )
    val mergeCompilerSupportTask = project.tasks.register(
        "mergeWinRtCompilerSupport",
        MergeWinRtCompilerSupportTask::class.java,
        Action<MergeWinRtCompilerSupportTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Merges Kotlin WinRT compiler support tables from this project and WinRT dependencies."
            task.localCompilerSupportManifest.set(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-support/compiler-support.tsv")
                },
            )
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/kotlin-winrt/compiler-support/merged"))
            task.emitXamlComponentResourceSources.set(
                (extension as? WinRtExtension)?.applicationEnabled ?: project.provider { false },
            )
            task.dependsOn(generateTask)
        },
    )

    project.plugins.withId("org.jetbrains.kotlin.jvm") {
        configureKotlinWinRtCompilerPluginClasspath(project)
        addGeneratedSourcesToKotlinMain(project, generatedSources)
        addGeneratedSourcesToKotlinMain(project, mergeCompilerSupportTask.flatMap { it.outputDirectory })
        addGeneratedSourcesToKotlinMain(project, generatedAuthoringSources)
        configureKotlinWinRtCompilerPluginOptions(
            project = project,
            metadataIndex = generatedSources.map { directory ->
                directory.file("kotlin-winrt-authoring/metadata-index.tsv")
            },
            authoringAssemblyName = project.provider { project.name },
            authoringTargetArtifactName = authoringTargetArtifactName,
            compilerSupportManifest = mergeCompilerSupportTask.flatMap { it.outputDirectory.file("compiler-support.tsv") },
        )
        project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(mergeCompilerSupportTask)
        })
        project.tasks.withType(KotlinNativeCompile::class.java).configureEach(Action<KotlinNativeCompile> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(mergeCompilerSupportTask)
        })
        configureWinRtAuthoredCandidateValidation(project, extension, generatedSources, generatedAuthoringSources)
    }

    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        configureKotlinWinRtCompilerPluginClasspath(project)
        generateTask.configure { task ->
            task.authoringTypeDetailsOutputDirectory.set(generatedAuthoringSources)
        }
        addGeneratedSourcesToKotlinMultiplatformCommonMain(project, generatedSources)
        addGeneratedSourcesToKotlinMultiplatformCommonMain(project, mergeCompilerSupportTask.flatMap { it.outputDirectory })
        addGeneratedAuthoringSourcesToKotlinMultiplatformSourceRoots(project, generatedAuthoringSources, generateTask)
        configureKotlinWinRtCompilerPluginOptions(
            project = project,
            metadataIndex = generatedSources.map { directory ->
                directory.file("kotlin-winrt-authoring/metadata-index.tsv")
            },
            authoringAssemblyName = project.provider { project.name },
            authoringTargetArtifactName = authoringTargetArtifactName,
            compilerSupportManifest = mergeCompilerSupportTask.flatMap { it.outputDirectory.file("compiler-support.tsv") },
        )
        project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(mergeCompilerSupportTask)
        })
        configureWinRtAuthoredCandidateValidation(project, extension, generatedSources, generatedAuthoringSources)
    }

    project.plugins.withId("java") {
        project.extensions.configure(SourceSetContainer::class.java, Action<SourceSetContainer> {
            it.getByName("main").java.srcDir(generatedSources)
        })
        project.tasks.matching { task -> task.name == "compileJava" }.configureEach(Action<Task> { task ->
            task.dependsOn(generateTask)
        })
    }
}

private fun configureWinRtAuthoredCandidateValidation(
    project: Project,
    extension: BaseWinRtExtension,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    generatedAuthoringSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    project.tasks.withType(KotlinJvmCompile::class.java).all { compileTask ->
        if (!compileTask.name.startsWith("compileKotlin")) {
            return@all
        }
        registerWinRtAuthoredCandidateValidation(project, extension, generatedSources, generatedAuthoringSources, compileTask)
    }
}

private fun registerWinRtAuthoredCandidateValidation(
    project: Project,
    extension: BaseWinRtExtension,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    generatedAuthoringSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    compileTask: KotlinJvmCompile,
) {
    val projectName = project.name
    val validationTaskName = "validate${compileTask.name.replaceFirstChar(Char::uppercaseChar)}WinRtAuthoredCandidates"
    if (project.tasks.names.contains(validationTaskName)) {
        return
    }
    val validationTask = project.tasks.register(
        validationTaskName,
        ValidateWinRtAuthoredCandidatesTask::class.java,
        Action<ValidateWinRtAuthoredCandidatesTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Validates source-scanned authored candidates against compiler IR authored candidates."
            task.scannerCandidates.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/authored-candidates.tsv")
                },
            )
            task.compilerCandidates.from(
                compileTask.destinationDirectory.map { directory ->
                    directory.file("kotlin-winrt/authored-candidates.tsv")
                },
            )
            task.scannerAuthoredMetadata.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/authored-metadata.tsv")
                },
            )
            task.compilerAuthoredMetadata.from(
                compileTask.destinationDirectory.map { directory ->
                    directory.file("kotlin-winrt-authoring/authored-metadata.tsv")
                },
            )
            task.scannerAuthoredWinmd.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/${projectName}.winmd")
                },
            )
            task.compilerAuthoredWinmd.from(
                compileTask.destinationDirectory.map { directory ->
                    directory.file("kotlin-winrt-authoring/${projectName}.winmd")
                },
            )
            task.scannerAuthoredHostManifest.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/${projectName}.host.json")
                },
            )
            task.compilerAuthoredHostManifest.from(
                compileTask.destinationDirectory.map { directory ->
                    directory.file("kotlin-winrt-authoring/${projectName}.host.json")
                },
            )
            task.scannerAuthoringTypeDetails.from(generatedAuthoringSources)
            task.compilerAuthoringTypeDetails.from(
                project.layout.buildDirectory.dir("generated/kotlin-winrt-compiler-authoring/${compileTask.name}/src/main/kotlin"),
            )
            task.outputFile.set(
                project.layout.buildDirectory.file("kotlin-winrt/validation/${compileTask.name}/authored-candidates.txt"),
            )
            task.dependsOn(compileTask)
        },
    )
    val compilerTypeDetailsTask = project.tasks.register(
        "generate${compileTask.name.replaceFirstChar(Char::uppercaseChar)}WinRtCompilerAuthoredTypeDetails",
        GenerateWinRtCompilerAuthoredTypeDetailsTask::class.java,
        Action<GenerateWinRtCompilerAuthoredTypeDetailsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Regenerates authored TypeDetails from compiler IR authored candidates for validation."
            task.outputDirectory.set(
                project.layout.buildDirectory.dir("generated/kotlin-winrt-compiler-authoring/${compileTask.name}/src/main/kotlin"),
            )
            task.compilerCandidates.from(
                compileTask.destinationDirectory.map { directory ->
                    directory.file("kotlin-winrt/authored-candidates.tsv")
                },
            )
            task.metadataInputs.set(extension.metadataInputs)
            task.metadataInputFiles.from(
                project.provider {
                    explicitMetadataInputFiles(extension.metadataInputs.get())
                },
            )
            task.includeNamespaces.set(extension.includeNamespaces)
            task.includeTypes.set(extension.includeTypes)
            task.excludeNamespaces.set(extension.excludeNamespaces)
            task.excludeTypes.set(extension.excludeTypes)
            task.windowsSdkVersion.set(extension.windowsSdkVersion)
            task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
            task.generateWindowsSdkProjection.set(extension.generateWindowsSdkProjection)
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
                    projectionNuGetPackageSpecs(extension)
                },
            )
            task.authoringAssemblyName.set(projectName)
            task.dependsOn(compileTask)
        },
    )
    validationTask.configure { task ->
        task.dependsOn(compilerTypeDetailsTask)
    }
    val compilerAuthoredMetadata = compileTask.destinationDirectory.map { directory ->
        directory.file("kotlin-winrt-authoring/${projectName}.winmd")
    }
    val compilerAuthoredHostManifest = compileTask.destinationDirectory.map { directory ->
        directory.file("kotlin-winrt-authoring/${projectName}.host.json")
    }
    val compilerAuthoredHostManifestFiles = localAuthoredHostManifestFiles(project, compilerAuthoredHostManifest)
    project.tasks.withType(GenerateWinRtIdentityTask::class.java).matching { task ->
        task.name == "generateWinRtIdentity"
    }.configureEach { task ->
        task.authoredMetadataFiles.from(compilerAuthoredMetadata)
        task.authoredHostManifestFiles.from(compilerAuthoredHostManifestFiles)
    }
    project.tasks.matching { task -> task.name == "processResources" }.configureEach(Action<Task> { task ->
        if (task is Copy) {
            task.from(
                if ((extension as? WinRtExtension)?.applicationEnabled?.get() == true) {
                    project.files()
                } else {
                    project.files(compilerAuthoredMetadata, compilerAuthoredHostManifest)
                },
                Action<CopySpec> { spec ->
                    spec.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY)
                },
            )
        }
    })
    project.tasks.withType(StageWinRtRuntimeAssetsTask::class.java).configureEach { task ->
        task.authoredMetadataFiles.from(compilerAuthoredMetadata)
        task.authoredHostManifestFiles.from(compilerAuthoredHostManifestFiles)
        task.dependsOn(validationTask)
    }
    project.tasks.withType(BuildWinRtAuthoringHostTask::class.java).configureEach { task ->
        task.authoredHostManifestFiles.from(compilerAuthoredHostManifestFiles)
        task.dependsOn(validationTask)
    }
    project.tasks.matching { task -> task.name == "check" }.configureEach(Action<Task> { task ->
        task.dependsOn(validationTask)
    })
    project.tasks.matching { task ->
        task.name == "generateWinRtIdentity" ||
            task.name == "classes" ||
            task.name == "jar" ||
            task.name == "assemble" ||
            task.name == "processResources" ||
            task.name == "stageWinRtRuntimeAssets" ||
            task.name == "stageWinRtApplicationPackage"
    }.configureEach(Action<Task> { task ->
        task.dependsOn(validationTask)
    })
}

private fun configureKotlinWinRtCompilerPluginClasspath(project: Project) {
    val configuredConfigurations = mutableSetOf<String>()
    project.configurations
        .matching { configuration ->
            configuration.name.contains("compilerPluginClasspath", ignoreCase = true)
        }
        .configureEach { configuration ->
            if (configuredConfigurations.add(configuration.name)) {
                project.dependencies.add(configuration.name, kotlinWinRtCompilerPluginDependency(project))
                kotlinWinRtCompilerPluginRuntimeDependencies(project).forEach { dependency ->
                    project.dependencies.add(configuration.name, dependency)
                }
            }
        }
}

private fun kotlinWinRtCompilerPluginClasspath(project: Project) =
    project.configurations.findByName(KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION)
        ?: project.configurations.create(KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION).also { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            project.dependencies.add(configuration.name, kotlinWinRtCompilerPluginDependency(project))
            kotlinWinRtCompilerPluginRuntimeDependencies(project).forEach { dependency ->
                project.dependencies.add(configuration.name, dependency)
            }
        }

private fun kotlinWinRtGeneratorWorkerClasspath(project: Project) =
    project.files(
        kotlinWinRtPluginClasspathLocation(project),
        project.configurations.findByName(KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION)
            ?: project.configurations.create(KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION).also { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                val version = kotlinWinRtPluginVersion()
                if (kotlinWinRtHasLocalGeneratorWorkerProjects(project)) {
                    project.dependencies.add(
                        configuration.name,
                        kotlinWinRtProjectOrModuleDependency(project, ":winrt-runtime", "winrt-runtime", version),
                    )
                    project.dependencies.add(
                        configuration.name,
                        kotlinWinRtProjectOrModuleDependency(project, ":winrt-metadata", "winrt-metadata", version),
                    )
                    project.dependencies.add(
                        configuration.name,
                        kotlinWinRtProjectOrModuleDependency(project, ":winrt-generator", "winrt-generator", version),
                    )
                } else {
                    kotlinWinRtLocalGeneratorWorkerClasspath(project).takeIf { it.isNotEmpty() }?.let { files ->
                        project.dependencies.add(configuration.name, project.files(files))
                    } ?: run {
                        project.dependencies.add(
                            configuration.name,
                            kotlinWinRtProjectOrModuleDependency(project, ":winrt-runtime", "winrt-runtime", version),
                        )
                        project.dependencies.add(
                            configuration.name,
                            kotlinWinRtProjectOrModuleDependency(project, ":winrt-metadata", "winrt-metadata", version),
                        )
                        project.dependencies.add(
                            configuration.name,
                            kotlinWinRtProjectOrModuleDependency(project, ":winrt-generator", "winrt-generator", version),
                        )
                    }
                }
                project.dependencies.add(
                    configuration.name,
                    project.dependencies.create("com.squareup:kotlinpoet-jvm:1.18.1").also { dependency ->
                        (dependency as? ExternalModuleDependency)?.isTransitive = false
                    },
                )
            },
    )

private fun kotlinWinRtHasLocalGeneratorWorkerProjects(project: Project): Boolean =
    listOf(":winrt-runtime", ":winrt-metadata", ":winrt-generator").all { projectPath ->
        project.rootProject.findProject(projectPath) != null
    }

private fun kotlinWinRtProjectOrModuleDependency(
    project: Project,
    projectPath: String,
    moduleName: String,
    version: String,
): Any =
    if (project.rootProject.findProject(projectPath) != null) {
        project.dependencies.project(mapOf("path" to projectPath))
    } else {
        "io.github.compose-fluent:$moduleName:$version"
    }

private fun kotlinWinRtPluginClasspathLocation(project: Project): Any =
    runCatching {
        val location = GenerateWinRtProjectionsTask::class.java.protectionDomain?.codeSource?.location
        requireNotNull(location) { "kotlin-winrt Gradle plugin code source is unavailable." }
        project.files(File(location.toURI()))
    }.getOrElse {
        project.files()
    }

private fun kotlinWinRtCompilerPluginDependency(project: Project): Any {
    val localCompilerPlugin = project.rootProject.findProject(":winrt-compiler-plugin")
    return if (localCompilerPlugin != null) {
        project.dependencies.project(mapOf("path" to localCompilerPlugin.path))
    } else {
        kotlinWinRtCompilerPluginClasspathJar(project)
            ?: "io.github.compose-fluent:winrt-compiler-plugin:${kotlinWinRtPluginVersion()}"
    }
}

private fun kotlinWinRtRuntimeDependency(project: Project): Any {
    val localRuntimeProject = project.rootProject.findProject(":winrt-runtime")
    if (localRuntimeProject != null) {
        return project.dependencies.project(mapOf("path" to localRuntimeProject.path))
    }
    val runtimeClasspath = kotlinWinRtCodeSourceFile(Guid::class.java)
    return if (runtimeClasspath != null) {
        project.files(runtimeClasspath)
    } else {
        "io.github.compose-fluent:winrt-runtime:${kotlinWinRtPluginVersion()}"
    }
}

private fun kotlinWinRtAuthoringDependency(project: Project): Any {
    val localAuthoringProject = project.rootProject.findProject(":winrt-authoring")
    if (localAuthoringProject != null) {
        return project.dependencies.project(mapOf("path" to localAuthoringProject.path))
    }
    val authoringClasspath = kotlinWinRtCodeSourceFile(io.github.composefluent.winrt.authoring.KotlinWinRtAuthoredTypeCandidate::class.java)
    return if (authoringClasspath != null) {
        project.files(authoringClasspath)
    } else {
        "io.github.compose-fluent:winrt-authoring:${kotlinWinRtPluginVersion()}"
    }
}

private fun kotlinWinRtCompilerPluginRuntimeDependencies(project: Project): List<Any> {
    val runtimeDependencies = mutableListOf<Any>()
    runtimeDependencies += kotlinWinRtAuthoringDependency(project)
    val localMetadataProject = project.rootProject.findProject(":winrt-metadata")
    if (localMetadataProject != null) {
        runtimeDependencies += project.dependencies.project(mapOf("path" to localMetadataProject.path))
        return runtimeDependencies
    }
    val metadataClasspath = kotlinWinRtCodeSourceFile(WinRtMetadataSource::class.java)
    runtimeDependencies += if (metadataClasspath != null) {
        project.files(metadataClasspath)
    } else {
        "io.github.compose-fluent:winrt-metadata:${kotlinWinRtPluginVersion()}"
    }
    return runtimeDependencies
}

private fun kotlinWinRtLocalGeneratorWorkerClasspath(project: Project): List<File> =
    listOfNotNull(
        kotlinWinRtCodeSourceFile(Guid::class.java),
        kotlinWinRtCodeSourceFile(WinRtMetadataSource::class.java),
        kotlinWinRtCodeSourceFile(KotlinProjectionGenerator::class.java),
    )
        .distinctBy { file -> file.toPath().toAbsolutePath().normalize() }
        .filter { file -> file.exists() }

private fun kotlinWinRtAuthoringScannerRuntimeClasspath(project: Project): Any =
    project.files(
        listOf(
            "kotlin.Unit",
            "kotlinx.coroutines.CoroutineScope",
            "org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment",
            "org.jetbrains.kotlin.psi.KtFile",
        )
            .mapNotNull(::kotlinWinRtCodeSourceFile)
            .distinctBy { file -> file.toPath().toAbsolutePath().normalize() },
    )

private fun kotlinWinRtCompilerPluginClasspathJar(project: Project): Any? {
    val compilerPluginClass = KotlinWinRtCommandLineProcessor::class.java
    return kotlinWinRtCodeSourceFile(compilerPluginClass)?.let { file -> project.files(file) }
}

private fun kotlinWinRtCodeSourceFile(type: Class<*>): File? {
    val location = type.protectionDomain?.codeSource?.location ?: return null
    return runCatching { File(location.toURI()) }.getOrNull()
}

private fun kotlinWinRtCodeSourceFile(typeName: String): File? =
    runCatching { Class.forName(typeName, false, KotlinWinRtPlugin::class.java.classLoader) }
        .getOrNull()
        ?.let(::kotlinWinRtCodeSourceFile)

private fun kotlinWinRtPluginVersion(): String =
    KotlinWinRtPlugin::class.java.`package`.implementationVersion
        ?: "0.1.0-SNAPSHOT"

private fun explicitMetadataInputFiles(inputs: List<String>): List<File> =
    inputs.mapNotNull { input ->
        when (val source = runCatching { WinRtMetadataSource.parse(input) }.getOrNull()) {
            is WinRtMetadataSource.PathSource -> source.path.toFile()
            is WinRtMetadataSource.NuGetPackage -> source.packagePath.toFile()
            else -> null
        }
    }.filter { it.exists() }

private fun allNuGetPackageSpecs(extension: BaseWinRtExtension): List<String> =
    extension.nugetPackages.map { pkg ->
        "${pkg.packageId}@${pkg.version.get()}"
    }

private fun projectionNuGetPackageSpecs(extension: BaseWinRtExtension): List<String> =
    extension.nugetPackages
        .filter { pkg -> pkg.generateProjection }
        .map { pkg -> "${pkg.packageId}@${pkg.version.get()}" }

private fun addGeneratedSourcesToKotlinMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java) ?: return
    kotlinExtension.sourceSets.named("main").configure { sourceSet ->
        sourceSet.kotlin.srcDir(generatedSources)
    }
}

private fun configureWinRtIdentityProjectDependencies(
    project: Project,
    identityDependencies: org.gradle.api.artifacts.Configuration,
    includeExternalModules: Boolean,
) {
    val registeredProjectPaths = linkedSetOf<String>()
    val registeredExternalModules = linkedSetOf<String>()
    fun canRegisterIdentityDependency(): Boolean =
        identityDependencies.state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED

    fun registerDependency(dependency: ProjectDependency) {
        if (!canRegisterIdentityDependency()) {
            return
        }
        val dependencyProject = project.findProject(dependency.path)
        if (dependencyProject?.hasKotlinWinRtIdentityMetadata() == true &&
            registeredProjectPaths.add(dependency.path)
        ) {
            identityDependencies.dependencies.add(dependency.copy())
        }
    }
    fun registerDependency(dependency: ExternalModuleDependency) {
        if (!canRegisterIdentityDependency()) {
            return
        }
        val key = listOf(dependency.group, dependency.name, dependency.version).joinToString(":")
        if (registeredExternalModules.add(key)) {
            identityDependencies.dependencies.add(dependency.copy())
        }
    }
    fun scanConfiguration(configurationName: String) {
        project.configurations.findByName(configurationName)?.allDependencies?.forEach { dependency ->
            when (dependency) {
                is ProjectDependency -> registerDependency(dependency)
                is ExternalModuleDependency -> if (includeExternalModules) registerDependency(dependency)
            }
        }
    }
    fun observeConfiguration(configuration: org.gradle.api.artifacts.Configuration) {
        if (!configuration.name.isWinRtIdentityDependencySourceConfiguration()) {
            return
        }
        configuration.allDependencies.all { dependency ->
            if (dependency is ProjectDependency) {
                registerDependency(dependency)
            }
        }
    }
    fun kotlinSourceSetConfigurationNames(sourceSet: KotlinSourceSet): List<String> =
        buildList {
            add(sourceSet.apiConfigurationName)
            add(sourceSet.implementationConfigurationName)
            add("${sourceSet.apiConfigurationName}Metadata")
            add("${sourceSet.implementationConfigurationName}Metadata")
        }

    project.configurations.configureEach(::observeConfiguration)
    project.gradle.projectsEvaluated {
        scanConfiguration("api")
        scanConfiguration("implementation")
        project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.sourceSets?.forEach { sourceSet ->
            kotlinSourceSetConfigurationNames(sourceSet).forEach(::scanConfiguration)
        }
    }
}

private fun String.isWinRtIdentityDependencySourceConfiguration(): Boolean =
    this == "api" ||
        this == "implementation" ||
        ("Main" in this && (
            endsWith("Api") ||
                endsWith("Implementation") ||
                endsWith("ApiMetadata") ||
                endsWith("ImplementationMetadata")
            ))

private fun kotlinWinRtIdentityFiles(
    project: Project,
    identityDependencies: org.gradle.api.artifacts.Configuration,
): org.gradle.api.file.FileCollection =
    identityDependencies.incoming.artifactView { view ->
        view.isLenient = true
        view.attributes.attribute(
            Usage.USAGE_ATTRIBUTE,
            project.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
        )
    }.files

private fun addGeneratedSourcesToKotlinMultiplatformCommonMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlinExtension.sourceSets.named("commonMain").configure { sourceSet ->
        sourceSet.kotlin.srcDir(generatedSources)
    }
}

private fun addGeneratedAuthoringSourcesToKotlinMultiplatformSourceRoots(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    generateTask: org.gradle.api.tasks.TaskProvider<GenerateWinRtProjectionsTask>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    project.gradle.projectsEvaluated {
        if (generateTask.get().sourceRoots.files.isEmpty()) {
            return@projectsEvaluated
        }
        kotlinExtension.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
            val sourceSet = target.compilations.getByName("main").defaultSourceSet
            sourceSet.kotlin.srcDir(generatedSources)
        }
    }
}

private fun kotlinWinRtAuthoringTargetArtifactName(project: Project): Provider<String> =
    runCatching {
        project.tasks.named("jar", Jar::class.java).flatMap { task -> task.archiveFileName }
    }.getOrNull() ?: project.provider { "${project.name}.jar" }

private fun configureKotlinWinRtCompilerPluginOptions(
    project: Project,
    metadataIndex: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
    authoringAssemblyName: org.gradle.api.provider.Provider<String>,
    authoringTargetArtifactName: org.gradle.api.provider.Provider<String>,
    compilerSupportManifest: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
) {
    project.tasks.withType(KotlinNativeCompile::class.java).configureEach(Action<KotlinNativeCompile> { task ->
        val freeCompilerArgs = task.compilerOptions.freeCompilerArgs
        val metadataIndexPath = metadataIndex.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:metadataIndex=$metadataIndexPath",
        )
        val compilerSupportManifestPath = compilerSupportManifest.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:compilerSupportManifest=$compilerSupportManifestPath",
        )
    })
    project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
        task.compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
        task.compilerOptions.freeCompilerArgs.add("-Xjdk-release=25")
        val freeCompilerArgs = task.compilerOptions.freeCompilerArgs
        val metadataIndexPath = metadataIndex.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:metadataIndex=$metadataIndexPath",
        )
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(project.provider {
            val outputDirectory = task.destinationDirectory.get()
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:typeIndexOutput=${outputDirectory.file("kotlin-winrt/type-index.tsv").asFile.absolutePath}"
        })
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(project.provider {
            val outputDirectory = task.destinationDirectory.get()
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:authoredCandidatesOutput=${outputDirectory.file("kotlin-winrt/authored-candidates.tsv").asFile.absolutePath}"
        })
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(project.provider {
            val outputDirectory = task.destinationDirectory.get()
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:authoredMetadataOutput=${outputDirectory.file("kotlin-winrt-authoring/authored-metadata.tsv").asFile.absolutePath}"
        })
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(project.provider {
            val outputDirectory = task.destinationDirectory.get()
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:authoredWinmdOutput=${outputDirectory.file("kotlin-winrt-authoring/${authoringAssemblyName.get()}.winmd").asFile.absolutePath}"
        })
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(project.provider {
            val outputDirectory = task.destinationDirectory.get()
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:authoredHostManifestOutput=${outputDirectory.file("kotlin-winrt-authoring/${authoringAssemblyName.get()}.host.json").asFile.absolutePath}"
        })
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(authoringAssemblyName.map { assemblyName ->
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:authoringAssemblyName=$assemblyName"
        })
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(authoringTargetArtifactName.map { targetArtifactName ->
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:authoringTargetArtifactName=$targetArtifactName"
        })
        val compilerSupportManifestPath = compilerSupportManifest.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:compilerSupportManifest=$compilerSupportManifestPath",
        )
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(project.provider {
            val outputDirectory = task.destinationDirectory.get()
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:compilerSupportClassOutputDirectory=${outputDirectory.asFile.absolutePath}"
        })
    })
}

private fun kotlinMainSourceDirs(project: Project): List<File> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.let { kotlinExtension ->
        return kotlinExtension.sourceSets.flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
    }
    val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java) ?: return emptyList()
    return kotlinExtension.sourceSets.findByName("main")?.kotlin?.srcDirs.orEmpty().toList()
}

private fun localAuthoredHostManifestFiles(project: Project, manifestFile: Provider<RegularFile>) =
    project.files(manifestFile)
        .filter(::authoredHostManifestDeclaresActivatableClasses)

private fun existingNuGetPackageContentRoots(
    packageSpecs: List<String>,
    explicitGlobalPackagesRoots: List<String>,
): List<File> {
    val roots = WinRtNuGetPackageResolver.globalPackagesRoots(
        explicitRoots = explicitGlobalPackagesRoots.map(Path::of),
    )
    return packageSpecs
        .map(::parseNuGetPackageIdentity)
        .flatMap { identity ->
            runCatching {
                WinRtNuGetPackageResolver.resolveClosure(identity, roots)
            }.getOrElse {
                emptyList()
            }
        }
        .map { resolved -> resolved.packageRoot.toFile() }
        .distinctBy { it.toPath().toAbsolutePath().normalize().toString().lowercase() }
}

private fun Project.hasKotlinWinRtIdentityMetadata(): Boolean =
    configurations.findByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)?.isCanBeConsumed == true
