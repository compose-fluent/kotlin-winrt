package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTNuGetPackageResolver
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.metadata.projectionInventory
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.projections.generator.redirectedWinAppSdkProjectionSurfaceTypeReferences
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
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import java.io.File
import java.util.Properties
import java.nio.file.Path
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

class KotlinWinRTPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("winRT", WinRTExtension::class.java, project)
        configureWinRTRuntimeDependency(project)
        configureWinRTGeneration(project, extension)
        configureWinRTLibraryModel(project, extension)
        configureWinRTApplicationModel(project, extension)
    }
}

const val KOTLIN_WINRT_IDENTITY_CONFIGURATION: String = "kotlinWinRTIdentity"
const val KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION: String = "kotlinWinRTIdentityElements"
const val KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION: String = "kotlinWinRTCompilerPlugin"
const val KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION: String = "kotlinWinRTGeneratorWorker"
const val KOTLIN_WINRT_IDENTITY_USAGE: String = "kotlin-winrt-identity"
const val KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY: String = "kotlin-winrt-runtime-assets"
private const val KOTLIN_WINRT_COMPILER_PLUGIN_ID: String = "io.github.composefluent.winrt.compiler"
private const val KOTLIN_WINRT_LIBRARY_DEPENDENCY_IDENTITY_CONFIGURATION: String = "kotlinWinRTLibraryDependencyIdentity"

fun Project.registerWinRTApplicationHostRunTask(
    name: String,
): TaskProvider<RunWinRTApplicationHostTask> =
    registerWinRTApplicationHostRunTask(name, Action {})

fun Project.registerWinRTApplicationHostRunTask(
    name: String,
    configure: Action<in RunWinRTApplicationHostTask>,
): TaskProvider<RunWinRTApplicationHostTask> {
    val applicationHostTask = tasks.named("buildWinRTApplicationHost", BuildWinRTApplicationHostTask::class.java)
    return registerWinRTApplicationHostRunTask(name, applicationHostTask, configure)
}

private fun configureWinRTRuntimeDependency(
    project: Project,
    includeAuthoring: Boolean = true,
) {
    val configuredRuntimeConfigurations = mutableSetOf<String>()
    val configuredAuthoringConfigurations = mutableSetOf<String>()
    fun addRuntimeDependency(configurationName: String) {
        if (configuredRuntimeConfigurations.add(configurationName)) {
            project.dependencies.add(
                configurationName,
                kotlinWinRTRuntimeDependency(project),
            )
        }
    }
    fun addAuthoringDependency(configurationName: String) {
        if (configuredAuthoringConfigurations.add(configurationName)) {
            project.dependencies.add(
                configurationName,
                kotlinWinRTAuthoringDependency(project),
            )
        }
    }
    project.configurations
        .matching { configuration -> configuration.name == "implementation" }
        .configureEach { configuration ->
            addRuntimeDependency(configuration.name)
            if (includeAuthoring) {
                addAuthoringDependency(configuration.name)
            }
        }
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
            kotlin.sourceSets.matching { sourceSet -> sourceSet.name == "winuiMain" }.configureEach { sourceSet ->
                addRuntimeDependency(sourceSet.implementationConfigurationName)
                if (includeAuthoring) {
                    addAuthoringDependency(sourceSet.implementationConfigurationName)
                }
            }
            kotlin.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
                val sourceSet = target.compilations.getByName("main").defaultSourceSet
                if (includeAuthoring) {
                    addAuthoringDependency(sourceSet.implementationConfigurationName)
                }
            }
        }
    }
}

private fun configureWinRTLibraryModel(
    project: Project,
    extension: WinRTExtension,
) {
    project.extensions.extraProperties["kotlinWinRTModel"] = project.provider {
        if (extension.applicationEnabled.get()) "application" else "library"
    }
    val identityTask = project.tasks.register(
        "generateWinRTIdentity",
        GenerateWinRTIdentityTask::class.java,
        Action<GenerateWinRTIdentityTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Writes Kotlin WinRT projection identity metadata for downstream application packaging."
            task.outputFile.set(project.layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt.json"))
            task.metadataInputs.set(extension.metadataInputs)
            task.includeNamespaces.set(extension.includeNamespaces)
            task.includeTypes.set(extension.includeTypes)
            task.projectionRegistrarFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-support/projection-registrar.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-support/projection-registrar.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-support/projection-registrar.tsv",
                ),
            )
            task.typeShapeDescriptorFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-support/type-shape-descriptors.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-support/type-shape-descriptors.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-support/type-shape-descriptors.tsv",
                ),
            )
            task.sourceAdditionManifestFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-support/source-additions.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-support/source-additions.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-support/source-additions.tsv",
                ),
            )
            task.excludeNamespaces.set(extension.excludeNamespaces)
            task.excludeTypes.set(extension.excludeTypes)
            task.additionExcludeNamespaces.set(extension.additionExcludeNamespaces)
            task.windowsSdkDeclared.set(extension.windowsSdkDeclared)
            task.windowsSdkVersion.set(extension.windowsSdkVersion)
            task.includeWindowsSdkExtensions.set(extension.includeWindowsSdkExtensions)
            task.nugetPackages.set(
                project.provider {
                    allNuGetPackageSpecs(extension)
                },
            )
            task.runtimeAssets.set(extension.runtimeAssets)
            task.runtimeAssetFiles.from(extension.runtimeAssets)
            task.authoringMetadataIndexFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring/metadata-index.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-authoring/metadata-index.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-authoring/metadata-index.tsv",
                ),
            )
            task.authoredMetadataFiles.from(
                project.layout.buildDirectory.file("generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring/${project.name}.winmd"),
                project.layout.buildDirectory.file("generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-authoring/${project.name}.winmd"),
                project.layout.buildDirectory.file("generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-authoring/${project.name}.winmd"),
            )
            task.authoredHostManifestFiles.from(
                project.layout.buildDirectory.file("generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring/${project.name}.host.json"),
                project.layout.buildDirectory.file("generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-authoring/${project.name}.host.json"),
                project.layout.buildDirectory.file("generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-authoring/${project.name}.host.json"),
                project.fileTree(project.layout.buildDirectory.dir("generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring")) { spec ->
                    spec.include("*.host.json")
                },
                project.fileTree(project.layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-authoring")) { spec ->
                    spec.include("*.host.json")
                },
                project.fileTree(project.layout.buildDirectory.dir("generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-authoring")) { spec ->
                    spec.include("*.host.json")
                },
            )
            task.compilerSupportManifestFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-support/compiler-support.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-support/compiler-support.tsv",
                ),
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/commonMain/kotlin/kotlin-winrt-support/compiler-support.tsv",
                ),
            )
            task.dependsOn("generateWinRTProjections")
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
    configureWinRTIdentityProjectDependencies(project, identityElements, includeExternalModules = false)
    configureWinRTIdentityProjectDependencies(project, dependencyIdentities, includeExternalModules = true)
    val dependencyIdentityFiles = kotlinWinRTIdentityFiles(project, dependencyIdentities)
    val localGenerationRequired = kotlinWinRTLocalGenerationRequired(project, extension, dependencyIdentityFiles)
    project.extensions.extraProperties["kotlinWinRTLocalGenerationRequired"] = localGenerationRequired
    project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
        task.emitProjectionSources.set(localGenerationRequired)
    }
    project.tasks.named("mergeWinRTCompilerSupport", MergeWinRTCompilerSupportTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.withType(GenerateWinRTCompilerAuthoredTypeDetailsTask::class.java).configureEach { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.extensions.extraProperties["kotlinWinRTIdentityElements"] = identityElements.name
    extension.whenApplicationConfigured {
        identityTask.configure { task ->
            task.enabled = false
        }
        identityElements.isCanBeConsumed = false
    }
    project.plugins.withId("java") {
        project.tasks.matching { it.name == "processResources" }.configureEach(Action<Task> { task ->
            task.dependsOn("generateWinRTProjections")
        })
    }
}

private fun configureWinRTApplicationModel(
    project: Project,
    extension: WinRTExtension,
) {
    extension.whenApplicationConfigured {
        configureWinRTApplicationTasks(project, extension)
    }
}

private fun Project.registerWinRTApplicationHostRunTask(
    name: String,
    applicationHostTask: TaskProvider<BuildWinRTApplicationHostTask>,
    configure: Action<in RunWinRTApplicationHostTask>,
): TaskProvider<RunWinRTApplicationHostTask> {
    val applicationHostExecutable = applicationHostTask.flatMap { task ->
        task.outputDirectory.file(task.executableBaseName.map { executableBaseName -> "$executableBaseName.exe" })
    }
    return tasks.register(
        name,
        RunWinRTApplicationHostTask::class.java,
        Action<RunWinRTApplicationHostTask> { task ->
            task.group = "application"
            task.description = "Runs the native Kotlin/WinRT JVM application host."
            task.hostExecutable.set(applicationHostExecutable)
            task.workingDirectory.set(applicationHostTask.flatMap { it.outputDirectory })
            task.dependsOn(applicationHostTask)
            task.onlyIf {
                System.getProperty("os.name").contains("Windows", ignoreCase = true)
            }
            configure.execute(task)
        },
    )
}

private fun configureWinRTApplicationTasks(
    project: Project,
    extension: WinRTExtension,
) {
    if (project.configurations.findByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION) != null) {
        return
    }
    val unpackagedMode = extension.application.packageMode.map { it == WinRTApplicationPackageMode.Unpackaged }
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
    configureWinRTIdentityProjectDependencies(project, identityDependencies, includeExternalModules = true)
    val dependencyIdentityFiles = kotlinWinRTIdentityFiles(project, identityDependencies)
    project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.named("mergeWinRTCompilerSupport", MergeWinRTCompilerSupportTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.withType(GenerateWinRTCompilerAuthoredTypeDetailsTask::class.java).configureEach { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    val applicationIdentityTask = project.tasks.register(
        "generateWinRTApplicationIdentity",
        GenerateWinRTApplicationIdentityTask::class.java,
        Action<GenerateWinRTApplicationIdentityTask> { task ->
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
        "buildWinRTAuthoringHost",
        BuildWinRTAuthoringHostTask::class.java,
        Action<BuildWinRTAuthoringHostTask> { task ->
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
        "resolveWinRTRuntimeNuGetPackages",
        ResolveWinRTRuntimeNuGetPackagesTask::class.java,
        Action<ResolveWinRTRuntimeNuGetPackagesTask> { task ->
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
        "stageWinRTRuntimeAssets",
        StageWinRTRuntimeAssetsTask::class.java,
        Action<StageWinRTRuntimeAssetsTask> { task ->
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
            task.executableBaseName.set(project.name)
            task.dependencyIdentityFiles.from(dependencyIdentityFiles)
            task.authoredHostDllFiles.from(project.fileTree(buildAuthoringHostTask.flatMap { it.outputDirectory }) { spec ->
                spec.include("*.dll")
            })
            task.dependsOn("generateWinRTProjections")
            task.dependsOn(buildAuthoringHostTask)
            task.dependsOn(resolveRuntimeNuGetPackagesTask)
        },
    )
    val mingwApplicationEntryTask = project.tasks.register(
        "generateWinRTMingwApplicationEntry",
        GenerateWinRTMingwApplicationEntryTask::class.java,
        Action<GenerateWinRTMingwApplicationEntryTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Generates the Kotlin/Native mingw application entry wrapper with WinUI bootstrap."
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/kotlin-winrt-application-entry/src/mingwX64Main/kotlin"))
            task.legacyOutputDirectories.from(
                project.layout.buildDirectory.dir("generated/kotlin-winrt-application-entry/src/commonMain/kotlin"),
            )
            task.mainClass.set(extension.application.mainClass)
            task.packageMode.set(project.provider { extension.application.packageMode.get().name })
        },
    )
    addGeneratedSourcesToKotlinMultiplatformMingwX64Main(project, mingwApplicationEntryTask)
    project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
        task.dependsOn(mingwApplicationEntryTask)
    })
    project.tasks.withType(KotlinNativeCompile::class.java).configureEach(Action<KotlinNativeCompile> { task ->
        task.dependsOn(mingwApplicationEntryTask)
    })
    val stageApplicationPackageTask = project.tasks.register(
        "stageWinRTApplicationPackage",
        StageWinRTApplicationPackageTask::class.java,
        Action<StageWinRTApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Stages WinRT application package resources and generates the application PRI."
            task.runtimeAssetsDirectory.set(stageRuntimeAssetsTask.flatMap { it.outputDirectory })
            task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/application-layout/mingwX64/release"))
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
            task.executableBaseName.set(project.name)
            task.dependsOn(stageRuntimeAssetsTask)
        },
    )
    configureMingwApplicationEntry(
        project,
        mingwApplicationEntryTask,
        stageRuntimeAssetsTask,
        stageApplicationPackageTask,
        extension.application.console.get(),
    )
    val applicationHostTask = project.tasks.register(
        "buildWinRTApplicationHost",
        BuildWinRTApplicationHostTask::class.java,
        Action<BuildWinRTApplicationHostTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Builds the native Kotlin/WinRT JVM application host with Windows App SDK deployment initialization."
            task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/application-layout/jvm"))
            task.generatedSourceDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/application-host/src"))
            task.packageMode.set(project.provider { extension.application.packageMode.get().name })
            task.console.set(extension.application.console)
            task.executableBaseName.set(project.name)
            task.javaHome.set(project.provider { System.getProperty("java.home") })
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.commandWorkingDirectory.set(project.layout.projectDirectory)
            task.runtimeAssetsDirectory.from(stageApplicationPackageTask.flatMap { it.outputDirectory })
            task.dependsOn("generateWinRTProjections")
            task.dependsOn("mergeWinRTCompilerSupport")
            task.dependsOn(stageRuntimeAssetsTask)
            task.dependsOn(stageApplicationPackageTask)
            task.dependsOn(buildAuthoringHostTask)
        },
    )
    val runApplicationHostTask = project.registerWinRTApplicationHostRunTask(
        "runWinRTApplicationHost",
        applicationHostTask,
        Action {},
    )
    extension.application.runTaskRegistrations.forEach { registration ->
        project.registerWinRTApplicationHostRunTask(
            registration.name,
            applicationHostTask,
            registration.action,
        )
    }
    val packageApplicationTask = project.tasks.register(
        "packageWinRTApplication",
        PackageWinRTApplicationTask::class.java,
        Action<PackageWinRTApplicationTask> { task ->
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
            task.onlyIf { extension.application.packageMode.get() == WinRTApplicationPackageMode.Packaged }
            task.dependsOn(stageApplicationPackageTask)
        },
    )
    val verifyPackageTask = project.tasks.register(
        "verifyWinRTApplicationPackage",
        VerifyWinRTApplicationPackageTask::class.java,
        Action<VerifyWinRTApplicationPackageTask> { task ->
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
                extension.application.packageMode.get() == WinRTApplicationPackageMode.Packaged &&
                    extension.application.generatePackage.get() &&
                    extension.application.verifyPackage.get()
            }
            task.dependsOn(packageApplicationTask)
        },
    )
    val signPackageTask = project.tasks.register(
        "signWinRTApplicationPackage",
        SignWinRTApplicationPackageTask::class.java,
        Action<SignWinRTApplicationPackageTask> { task ->
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
                extension.application.packageMode.get() == WinRTApplicationPackageMode.Packaged &&
                    extension.application.signPackage.get()
            }
            task.dependsOn(packageApplicationTask)
            task.dependsOn(verifyPackageTask)
        },
    )
    val installPackageTask = project.tasks.register(
        "installWinRTApplicationPackage",
        InstallWinRTApplicationPackageTask::class.java,
        Action<InstallWinRTApplicationPackageTask> { task ->
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
                extension.application.packageMode.get() == WinRTApplicationPackageMode.Packaged &&
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
    configureKmpJvmApplicationHostClasspath(project, applicationHostTask)
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
    project.extensions.extraProperties["kotlinWinRTIdentity"] = identityDependencies.name
    project.extensions.extraProperties["kotlinWinRTApplicationIdentityTask"] = applicationIdentityTask.name
    project.extensions.extraProperties["kotlinWinRTRuntimeNuGetPackagesTask"] = resolveRuntimeNuGetPackagesTask.name
    project.extensions.extraProperties["kotlinWinRTRuntimeAssetsTask"] = stageRuntimeAssetsTask.name
    project.extensions.extraProperties["kotlinWinRTApplicationPackageTask"] = stageApplicationPackageTask.name
    project.extensions.extraProperties["kotlinWinRTApplicationHostTask"] = applicationHostTask.name
    project.extensions.extraProperties["kotlinWinRTRunApplicationHostTask"] = runApplicationHostTask.name
    project.extensions.extraProperties["kotlinWinRTPackageTask"] = packageApplicationTask.name
    project.extensions.extraProperties["kotlinWinRTVerifyPackageTask"] = verifyPackageTask.name
    project.extensions.extraProperties["kotlinWinRTSignPackageTask"] = signPackageTask.name
    project.extensions.extraProperties["kotlinWinRTInstallPackageTask"] = installPackageTask.name
}

private class RuntimeAssetsRootJvmArgumentProvider(
    @get:Input
    private val runtimeAssetsRoot: Provider<String>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Dkotlin.winrt.runtimeAssetsRoot=${runtimeAssetsRoot.get()}")
}

private fun configureKmpJvmApplicationHostClasspath(
    project: Project,
    applicationHostTask: TaskProvider<BuildWinRTApplicationHostTask>,
) {
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        var configuredTargetName: String? = null

        fun configureTarget(targetName: String) {
            if (configuredTargetName != null) {
                return
            }
            configuredTargetName = targetName
            val runtimeClasspath = project.configurations.named("${targetName}RuntimeClasspath")
            val jar = project.tasks.named("${targetName}Jar", Jar::class.java)
            applicationHostTask.configure { task ->
                task.runtimeClasspath.from(runtimeClasspath)
                task.runtimeClasspath.from(jar.flatMap { it.archiveFile })
                task.dependsOn(jar)
            }
        }

        kotlinExtension.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
            if (target.name == "winuiJvm") {
                configureTarget(target.name)
            }
        }
        project.afterEvaluate {
            if (configuredTargetName == null) {
                kotlinExtension.targets.withType(KotlinJvmTarget::class.java).singleOrNull()?.let { target ->
                    configureTarget(target.name)
                }
            }
        }
    }
}

private fun configureMingwApplicationEntry(
    project: Project,
    entryTask: TaskProvider<GenerateWinRTMingwApplicationEntryTask>,
    stageRuntimeAssetsTask: TaskProvider<StageWinRTRuntimeAssetsTask>,
    stageApplicationPackageTask: TaskProvider<StageWinRTApplicationPackageTask>,
    console: Boolean,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlinExtension.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
        if (!target.isMingwX64Target()) {
            return@configureEach
        }
        target.binaries.withType(Executable::class.java).configureEach { executable ->
            executable.entryPoint = KOTLIN_WINRT_MINGW_APPLICATION_ENTRY_POINT
            executable.linkerOpts(if (console) "-Wl,/SUBSYSTEM:CONSOLE" else "-Wl,/SUBSYSTEM:WINDOWS")
            executable.linkTaskProvider.configure { task ->
                task.dependsOn(entryTask)
            }
            executable.runTaskProvider?.configure { task ->
                task.dependsOn(stageRuntimeAssetsTask)
                task.workingDir(project.projectDir)
                task.environment(
                    "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT",
                    stageRuntimeAssetsTask.flatMap { it.outputDirectory }.get().asFile.absolutePath,
                )
            }
            if (executable.buildType == NativeBuildType.RELEASE) {
                stageApplicationPackageTask.configure { task ->
                    task.dependsOn(executable.linkTaskProvider)
                    task.rootPackagePayloadFiles.from(project.provider { executable.outputFile })
                }
            }
        }
    }
}

private fun configureWinRTGeneration(
    project: Project,
    extension: BaseWinRTExtension,
) {
    val generatedJvmSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/jvmMain/kotlin")
    val generatedKmpWinuiSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
    val generatedKmpCommonSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/commonMain/kotlin")
    val generatedLegacyMainSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")
    val generatedJvmAuthoringSources = project.layout.buildDirectory.dir("generated/kotlin-winrt-authoring/src/jvmMain/kotlin")
    val generatedKmpWinuiAuthoringSources =
        project.layout.buildDirectory.dir("generated/kotlin-winrt-authoring/src/winuiMain/kotlin")
    val generatedKmpCommonAuthoringSources =
        project.layout.buildDirectory.dir("generated/kotlin-winrt-authoring/src/commonMain/kotlin")
    val generatedLegacyMainAuthoringSources =
        project.layout.buildDirectory.dir("generated/kotlin-winrt-authoring/src/main/kotlin")
    val generatedMingwApplicationEntrySources =
        project.layout.buildDirectory.dir("generated/kotlin-winrt-application-entry/src/mingwX64Main/kotlin")
    val compilerPluginClasspath = kotlinWinRTCompilerPluginClasspath(project)
    val generatorWorkerClasspath = kotlinWinRTGeneratorWorkerClasspath(project)
    val authoringTargetArtifactName = kotlinWinRTAuthoringTargetArtifactName(project)
    val mergedCompilerSupportManifest = project.layout.buildDirectory.file(
        "generated/kotlin-winrt/compiler-support/merged/compiler-support.tsv",
    )
    val generateTask = project.tasks.register(
        "generateWinRTProjections",
        GenerateWinRTProjectionsTask::class.java,
        Action<GenerateWinRTProjectionsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Generates Kotlin WinRT projections from Windows SDK and NuGet WinMD metadata."
            task.outputDirectory.set(generatedJvmSources)
            task.authoringTypeDetailsOutputDirectory.set(generatedJvmAuthoringSources)
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
            task.windowsSdkDeclared.set(extension.windowsSdkDeclared)
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
            task.workerEnvironment.set(
                project.providers.environmentVariablesPrefixedBy(""),
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
                    if ((extension as? WinRTExtension)?.applicationEnabled?.get() == true) "application" else "library"
                },
            )
            task.authoringAssemblyName.set(project.name)
            task.authoringTargetArtifactName.set(authoringTargetArtifactName)
            task.emitJvmAuthoringHostExports.convention(true)
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
            task.authoringScannerClasspath.from(kotlinWinRTAuthoringScannerRuntimeClasspath(project))
            task.sourceRoots.from(
                project.provider {
                    val generatedSourcesPath = task.outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
                    val generatedAuthoringSourcesPath =
                        task.authoringTypeDetailsOutputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
                    val generatedMingwApplicationEntrySourcesPath =
                        generatedMingwApplicationEntrySources.get().asFile.toPath().toAbsolutePath().normalize()
                    kotlinWinRTAuthoringSourceDirs(project).filterNot { sourceDir ->
                        val normalizedSourceDir = sourceDir.toPath().toAbsolutePath().normalize()
                        normalizedSourceDir.startsWith(generatedSourcesPath) ||
                            normalizedSourceDir.startsWith(generatedAuthoringSourcesPath) ||
                            normalizedSourceDir.startsWith(generatedMingwApplicationEntrySourcesPath)
                    }
                },
            )
        },
    )
    val mergeCompilerSupportTask = project.tasks.register(
        "mergeWinRTCompilerSupport",
        MergeWinRTCompilerSupportTask::class.java,
        Action<MergeWinRTCompilerSupportTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Merges Kotlin WinRT compiler support tables from this project and WinRT dependencies."
            task.localCompilerSupportManifest.set(
                generateTask.flatMap { generator ->
                    generator.outputDirectory.file("kotlin-winrt-support/compiler-support.tsv")
                },
            )
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/kotlin-winrt/compiler-support/merged"))
            task.emitXamlComponentResourceSources.set(
                (extension as? WinRTExtension)?.applicationEnabled ?: project.provider { false },
            )
            task.dependsOn(generateTask)
        },
    )
    project.tasks.withType(GenerateWinRTCompilerAuthoredTypeDetailsTask::class.java).configureEach { task ->
        task.projectionRegistrarFiles.from(
            project.provider {
                if (kotlinWinRTLocalGenerationRequired(project).get()) {
                    listOf(
                        project.layout.buildDirectory
                            .file("generated/kotlin-winrt/compiler-support/merged/projection-registrar.tsv")
                            .get()
                            .asFile,
                    )
                } else {
                    emptyList()
                }
            },
        )
    }

    project.plugins.withId("org.jetbrains.kotlin.jvm") {
        val generatedSources = generatedJvmSources
        val generatedAuthoringSources = generatedJvmAuthoringSources
        generateTask.configure { task ->
            task.outputDirectory.set(generatedSources)
            task.authoringTypeDetailsOutputDirectory.set(generatedAuthoringSources)
            task.emitJvmAuthoringHostExports.set(true)
        }
        addGeneratedProjectionSourcesToKotlinMain(project, generatedSources)
        addGeneratedProjectionSourcesToKotlinMain(
            project,
            project.layout.buildDirectory.dir("generated/kotlin-winrt/compiler-support/merged"),
        )
        addGeneratedSourcesToKotlinMain(project, generatedAuthoringSources)
        configureKotlinWinRTCompilerPluginClasspath(project)
        configureKotlinWinRTCompilerPluginOptions(
            project = project,
            metadataIndex = generatedSources.map { directory ->
                directory.file("kotlin-winrt-authoring/metadata-index.tsv")
            },
            authoringAssemblyName = project.provider { project.name },
            authoringTargetArtifactName = authoringTargetArtifactName,
            nativeAuthoringTargetArtifactName = kotlinWinRTNativeAuthoringTargetArtifactName(project),
            compilerSupportManifest = mergedCompilerSupportManifest,
        )
        project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(kotlinWinRTLocalCompilerSupportDependencies(project, mergeCompilerSupportTask))
        })
        project.tasks.withType(KotlinNativeCompile::class.java).configureEach(Action<KotlinNativeCompile> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(kotlinWinRTLocalCompilerSupportDependencies(project, mergeCompilerSupportTask))
        })
        configureWinRTAuthoredCandidateValidation(project, extension, generatedSources, generatedAuthoringSources)
    }

    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val generatedSources = generatedKmpWinuiSources
        val generatedAuthoringSources = generatedKmpWinuiAuthoringSources
        configureKotlinWinRTMultiplatformWinuiSourceSet(project)
        generateTask.configure { task ->
            task.outputDirectory.set(generatedSources)
            task.authoringTypeDetailsOutputDirectory.set(generatedAuthoringSources)
            task.legacyOutputDirectories.from(
                generatedJvmSources,
                generatedKmpCommonSources,
                generatedLegacyMainSources,
                generatedJvmAuthoringSources,
                generatedKmpCommonAuthoringSources,
                generatedLegacyMainAuthoringSources,
                project.layout.buildDirectory.dir("generated/kotlin-winrt-native-authoring-host"),
            )
            task.emitJvmAuthoringHostExports.set(false)
            task.authoringTargetArtifactName.set(kotlinWinRTNativeAuthoringTargetArtifactName(project))
            task.additionalAuthoringTargetArtifactNames.set(authoringTargetArtifactName.map(::listOf))
        }
        addGeneratedProjectionSourcesToKotlinMultiplatformWinuiMain(project, generatedSources)
        addGeneratedProjectionSourcesToKotlinMultiplatformWinuiMain(
            project,
            project.layout.buildDirectory.dir("generated/kotlin-winrt/compiler-support/merged"),
        )
        addGeneratedSourcesToKotlinMultiplatformWinuiMain(project, generatedAuthoringSources)
        configureKotlinWinRTCompilerPluginClasspath(project)
        configureKotlinWinRTCompilerPluginOptions(
            project = project,
            metadataIndex = generatedSources.map { directory ->
                directory.file("kotlin-winrt-authoring/metadata-index.tsv")
            },
            authoringAssemblyName = project.provider { project.name },
            authoringTargetArtifactName = authoringTargetArtifactName,
            nativeAuthoringTargetArtifactName = kotlinWinRTNativeAuthoringTargetArtifactName(project),
            compilerSupportManifest = mergedCompilerSupportManifest,
        )
        project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(kotlinWinRTLocalCompilerSupportDependencies(project, mergeCompilerSupportTask))
        })
        project.tasks.withType(KotlinNativeCompile::class.java).configureEach(Action<KotlinNativeCompile> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(kotlinWinRTLocalCompilerSupportDependencies(project, mergeCompilerSupportTask))
        })
        project.tasks.matching { task -> task.name == "compileWinuiMainKotlinMetadata" }.configureEach(Action<Task> { task ->
            task.dependsOn(generateTask)
            task.dependsOn(kotlinWinRTLocalCompilerSupportDependencies(project, mergeCompilerSupportTask))
        })
        project.tasks.withType(Jar::class.java)
            .matching { task -> task.name.endsWith("SourcesJar") || task.name == "sourcesJar" }
            .configureEach(Action<Jar> { task ->
                task.dependsOn(generateTask)
                task.dependsOn(kotlinWinRTLocalCompilerSupportDependencies(project, mergeCompilerSupportTask))
            })
        configureWinRTAuthoredCandidateValidation(project, extension, generatedSources, generatedAuthoringSources)
    }

    project.plugins.withId("java") {
        project.extensions.configure(SourceSetContainer::class.java, Action<SourceSetContainer> {
            it.getByName("main").java.srcDir(
                conditionalGeneratedProjectionSourceDirectory(project, generatedJvmSources),
            )
        })
        project.tasks.matching { task -> task.name == "compileJava" }.configureEach(Action<Task> { task ->
            task.dependsOn(generateTask)
        })
    }
}

private fun configureWinRTAuthoredCandidateValidation(
    project: Project,
    extension: BaseWinRTExtension,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    generatedAuthoringSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val isMultiplatformProject = project.extensions.findByType(KotlinMultiplatformExtension::class.java) != null
    project.tasks.withType(KotlinJvmCompile::class.java).all { compileTask ->
        if (!compileTask.name.startsWith("compileKotlin")) {
            return@all
        }
        val outputs = compilerAuthoringOutputs(
            outputDirectory = compileTask.destinationDirectory,
            projectName = project.name,
        )
        registerWinRTAuthoredCandidateValidation(
            project = project,
            extension = extension,
            generatedSources = generatedSources,
            generatedAuthoringSources = generatedAuthoringSources,
            compileTaskName = compileTask.name,
            compileTaskProvider = project.tasks.named(compileTask.name),
            outputs = outputs,
            artifactPublication = WinRTAuthoredArtifactPublication.Jvm,
            allowTargetSpecificHostManifest = isMultiplatformProject,
        )
    }
    project.tasks.withType(KotlinNativeCompile::class.java).all { compileTask ->
        if (!compileTask.name.startsWith("compileKotlin")) {
            return@all
        }
        if (!compileTask.isMingwX64CompileTask()) {
            return@all
        }
        val outputDirectory = nativeAuthoringOutputDirectory(project, compileTask.name)
        compileTask.outputs.dir(outputDirectory)
        val outputs = compilerAuthoringOutputs(
            outputDirectory = outputDirectory,
            projectName = project.name,
        )
        registerWinRTAuthoredCandidateValidation(
            project = project,
            extension = extension,
            generatedSources = generatedSources,
            generatedAuthoringSources = generatedAuthoringSources,
            compileTaskName = compileTask.name,
            compileTaskProvider = project.tasks.named(compileTask.name),
            outputs = outputs,
            artifactPublication = WinRTAuthoredArtifactPublication.Native,
            allowTargetSpecificHostManifest = true,
        )
        registerWinRTNativeAuthoringExportValidation(
            project = project,
            compileTaskName = compileTask.name,
            outputs = outputs,
        )
    }
}

private enum class WinRTAuthoredArtifactPublication {
    Jvm,
    Native,
}

private fun registerWinRTAuthoredCandidateValidation(
    project: Project,
    extension: BaseWinRTExtension,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    generatedAuthoringSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    compileTaskName: String,
    compileTaskProvider: TaskProvider<out Task>,
    outputs: CompilerAuthoringOutputs,
    artifactPublication: WinRTAuthoredArtifactPublication,
    allowTargetSpecificHostManifest: Boolean,
) {
    val projectName = project.name
    val validationTaskName = "validate${compileTaskName.replaceFirstChar(Char::uppercaseChar)}WinRTAuthoredCandidates"
    if (project.tasks.names.contains(validationTaskName)) {
        return
    }
    val validationTask = project.tasks.register(
        validationTaskName,
        ValidateWinRTAuthoredCandidatesTask::class.java,
        Action<ValidateWinRTAuthoredCandidatesTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Validates source-scanned authored candidates against compiler IR authored candidates."
            task.scannerCandidates.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/authored-candidates.tsv")
                },
            )
            task.compilerCandidates.from(
                outputs.authoredCandidates,
            )
            task.scannerAuthoredMetadata.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/authored-metadata.tsv")
                },
            )
            task.compilerAuthoredMetadata.from(
                outputs.authoredMetadata,
            )
            task.scannerAuthoredWinmd.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/${projectName}.winmd")
                },
            )
            task.compilerAuthoredWinmd.from(
                outputs.authoredWinmd,
            )
            task.scannerAuthoredHostManifest.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-authoring/${projectName}.host.json")
                },
            )
            task.compilerAuthoredHostManifest.from(
                outputs.authoredHostManifest,
            )
            task.allowTargetSpecificHostManifest.set(allowTargetSpecificHostManifest)
            task.scannerAuthoringTypeDetails.from(generatedAuthoringSources)
            task.compilerAuthoringTypeDetails.from(
                compilerAuthoringTypeDetailsOutputDirectory(project, compileTaskName),
            )
            task.outputFile.set(
                project.layout.buildDirectory.file("kotlin-winrt/validation/${compileTaskName}/authored-candidates.txt"),
            )
            task.dependsOn("generateWinRTProjections")
            task.dependsOn(compileTaskProvider)
        },
    )
    val compilerTypeDetailsTask = project.tasks.register(
        "generate${compileTaskName.replaceFirstChar(Char::uppercaseChar)}WinRTCompilerAuthoredTypeDetails",
        GenerateWinRTCompilerAuthoredTypeDetailsTask::class.java,
        Action<GenerateWinRTCompilerAuthoredTypeDetailsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Regenerates authored TypeDetails from compiler IR authored candidates for validation."
            task.outputDirectory.set(
                compilerAuthoringTypeDetailsOutputDirectory(project, compileTaskName),
            )
            task.compilerCandidates.from(
                outputs.authoredCandidates,
            )
            task.metadataInputs.set(extension.metadataInputs)
            task.metadataInputFiles.from(
                project.provider {
                    explicitMetadataInputFiles(extension.metadataInputs.get())
                },
            )
            task.projectionRegistrarFiles.from(
                generatedSources.map { directory ->
                    directory.file("kotlin-winrt-support/projection-registrar.tsv")
                },
            )
            task.includeNamespaces.set(extension.includeNamespaces)
            task.includeTypes.set(extension.includeTypes)
            task.excludeNamespaces.set(extension.excludeNamespaces)
            task.excludeTypes.set(extension.excludeTypes)
            task.windowsSdkDeclared.set(extension.windowsSdkDeclared)
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
            task.dependsOn(compileTaskProvider)
        },
    )
    validationTask.configure { task ->
        task.dependsOn(compilerTypeDetailsTask)
    }
    val compilerAuthoredHostManifestFiles = localAuthoredHostManifestFiles(project, outputs.authoredHostManifest)
    if (artifactPublication == WinRTAuthoredArtifactPublication.Jvm) {
        project.tasks.withType(GenerateWinRTIdentityTask::class.java).matching { task ->
            task.name == "generateWinRTIdentity"
        }.configureEach { task ->
            task.authoredTargetArtifactFiles.from(
                kotlinWinRTJvmTargetJarArchiveFilePathCollection(project, compileTaskName),
            )
            kotlinWinRTJvmTargetJarTask(project, compileTaskName)?.let { jarTask ->
                task.mustRunAfter(jarTask)
            }
        }
    }
    if (artifactPublication == WinRTAuthoredArtifactPublication.Jvm) {
        project.tasks.matching { task -> task.name == "processResources" }.configureEach(Action<Task> { task ->
            if (task is Copy) {
                task.from(
                    project.provider {
                        if ((extension as? WinRTExtension)?.applicationEnabled?.get() == true) {
                            project.files()
                        } else {
                            project.files(outputs.authoredWinmd, outputs.authoredHostManifest)
                        }
                    },
                    Action<CopySpec> { spec ->
                        spec.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY)
                    },
                )
            }
        })
    }
    project.tasks.withType(StageWinRTRuntimeAssetsTask::class.java).configureEach { task ->
        if (artifactPublication == WinRTAuthoredArtifactPublication.Jvm) {
            task.authoredMetadataFiles.from(outputs.authoredWinmd)
            task.authoredHostManifestFiles.from(compilerAuthoredHostManifestFiles)
        }
        task.dependsOn(validationTask)
    }
    if (artifactPublication == WinRTAuthoredArtifactPublication.Jvm) {
        project.tasks.withType(BuildWinRTAuthoringHostTask::class.java).configureEach { task ->
            task.authoredHostManifestFiles.from(compilerAuthoredHostManifestFiles)
            task.dependsOn(validationTask)
        }
    }
    project.tasks.matching { task -> task.name == "check" }.configureEach(Action<Task> { task ->
        task.dependsOn(validationTask)
    })
    project.tasks.matching { task ->
        task.name == "classes" ||
            task.name == "jar" ||
            task.name == "assemble" ||
            task.name == "processResources" ||
            task.name == "stageWinRTRuntimeAssets" ||
            task.name == "stageWinRTApplicationPackage"
    }.configureEach(Action<Task> { task ->
        task.dependsOn(validationTask)
    })
}

private fun registerWinRTNativeAuthoringExportValidation(
    project: Project,
    compileTaskName: String,
    outputs: CompilerAuthoringOutputs,
) {
    val targetName = compileTaskName
        .removePrefix("compileKotlin")
        .replaceFirstChar(Char::lowercaseChar)
    if (!targetName.contains("mingw", ignoreCase = true)) {
        return
    }
    val targetDirectoryName = targetName
    val linkTaskName = "linkReleaseShared${targetDirectoryName.replaceFirstChar(Char::uppercaseChar)}"
    val nativeSharedLibrary = project.layout.buildDirectory.file(
        "bin/$targetDirectoryName/releaseShared/${kotlinNativeSharedLibraryFileStem(project.name)}.dll",
    )
    val exportValidationTaskName =
        "validate${compileTaskName.replaceFirstChar(Char::uppercaseChar)}WinRTNativeAuthoringExports"
    if (exportValidationTaskName in project.tasks.names) {
        return
    }
    val exportValidationTask = project.tasks.register(
        exportValidationTaskName,
        ValidateWinRTNativeAuthoringExportsTask::class.java,
        Action<ValidateWinRTNativeAuthoringExportsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Validates that the linked mingwX64 authored DLL exports WinRT activation entry points."
            task.authoredHostManifestFiles.from(outputs.authoredHostManifest)
            task.nativeSharedLibraryFiles.from(nativeSharedLibrary)
        },
    )
    project.afterEvaluate {
        if (linkTaskName !in project.tasks.names) {
            return@afterEvaluate
        }
        exportValidationTask.configure { task ->
            task.dependsOn(project.tasks.named(linkTaskName))
        }
        project.tasks.withType(GenerateWinRTIdentityTask::class.java).matching { task ->
            task.name == "generateWinRTIdentity"
        }.configureEach { task ->
            task.authoredTargetArtifactFiles.from(nativeSharedLibrary)
            task.dependsOn(project.tasks.named(linkTaskName))
        }
        project.tasks.matching { task ->
            task.name == "stageWinRTRuntimeAssets" ||
                task.name == "stageWinRTApplicationPackage"
        }.configureEach(Action<Task> { task ->
            task.dependsOn(exportValidationTask)
        })
    }
}

private data class CompilerAuthoringOutputs(
    val outputDirectory: Provider<Directory>,
    val typeIndex: Provider<RegularFile>,
    val authoredCandidates: Provider<RegularFile>,
    val authoredMetadata: Provider<RegularFile>,
    val authoredWinmd: Provider<RegularFile>,
    val authoredHostManifest: Provider<RegularFile>,
)

private fun nativeAuthoringOutputDirectory(
    project: Project,
    compileTaskName: String,
): Provider<Directory> =
    project.layout.buildDirectory.dir("kotlin-winrt/native-authoring/$compileTaskName")

private fun compilerAuthoringTypeDetailsOutputDirectory(
    project: Project,
    compileTaskName: String,
): Provider<Directory> =
    project.layout.buildDirectory.dir("generated/kotlin-winrt-compiler-authoring/$compileTaskName/src/commonMain/kotlin")

private fun compilerAuthoringOutputs(
    outputDirectory: Provider<Directory>,
    projectName: String,
): CompilerAuthoringOutputs =
    CompilerAuthoringOutputs(
        outputDirectory = outputDirectory,
        typeIndex = outputDirectory.map { directory ->
            directory.file("kotlin-winrt/type-index.tsv")
        },
        authoredCandidates = outputDirectory.map { directory ->
            directory.file("kotlin-winrt/authored-candidates.tsv")
        },
        authoredMetadata = outputDirectory.map { directory ->
            directory.file("kotlin-winrt-authoring/authored-metadata.tsv")
        },
        authoredWinmd = outputDirectory.map { directory ->
            directory.file("kotlin-winrt-authoring/${projectName}.winmd")
        },
        authoredHostManifest = outputDirectory.map { directory ->
            directory.file("kotlin-winrt-authoring/${projectName}.host.json")
        },
    )

private fun configureKotlinWinRTCompilerPluginClasspath(project: Project) {
    val configuredConfigurations = mutableSetOf<String>()
    project.configurations
        .matching { configuration ->
            configuration.name.contains("compilerPluginClasspath", ignoreCase = true)
        }
        .configureEach { configuration ->
            if (configuredConfigurations.add(configuration.name)) {
                project.dependencies.add(configuration.name, kotlinWinRTCompilerPluginDependency(project))
                kotlinWinRTCompilerPluginRuntimeDependencies(project).forEach { dependency ->
                    project.dependencies.add(configuration.name, dependency)
                }
            }
        }
}

private fun kotlinWinRTCompilerPluginClasspath(project: Project) =
    project.configurations.findByName(KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION)
        ?: project.configurations.create(KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION).also { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            project.dependencies.add(configuration.name, kotlinWinRTCompilerPluginDependency(project))
            kotlinWinRTCompilerPluginRuntimeDependencies(project).forEach { dependency ->
                project.dependencies.add(configuration.name, dependency)
            }
        }

private fun kotlinWinRTGeneratorWorkerClasspath(project: Project) =
    project.files(
        kotlinWinRTPluginClasspathLocation(project),
        project.configurations.findByName(KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION)
            ?: project.configurations.create(KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION).also { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                val version = kotlinWinRTPluginVersion()
                if (kotlinWinRTHasLocalGeneratorWorkerProjects(project)) {
                    project.dependencies.add(
                        configuration.name,
                        kotlinWinRTProjectOrModuleDependency(project, ":winrt-runtime", "winrt-runtime", version),
                    )
                    project.dependencies.add(
                        configuration.name,
                        kotlinWinRTProjectOrModuleDependency(project, ":winrt-metadata", "winrt-metadata", version),
                    )
                    project.dependencies.add(
                        configuration.name,
                        kotlinWinRTProjectOrModuleDependency(project, ":winrt-generator", "winrt-generator", version),
                    )
                } else {
                    (kotlinWinRTLocalGeneratorWorkerClasspath(project) ?: kotlinWinRTPluginMetadataGeneratorWorkerClasspath())
                        ?.let { files ->
                        project.dependencies.add(configuration.name, project.files(files))
                    } ?: run {
                        project.dependencies.add(
                            configuration.name,
                            kotlinWinRTProjectOrModuleDependency(project, ":winrt-runtime", "winrt-runtime", version),
                        )
                        project.dependencies.add(
                            configuration.name,
                            kotlinWinRTProjectOrModuleDependency(project, ":winrt-metadata", "winrt-metadata", version),
                        )
                        project.dependencies.add(
                            configuration.name,
                            kotlinWinRTProjectOrModuleDependency(project, ":winrt-generator", "winrt-generator", version),
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

private fun kotlinWinRTHasLocalGeneratorWorkerProjects(project: Project): Boolean =
    listOf(":winrt-runtime", ":winrt-metadata", ":winrt-generator").all { projectPath ->
        project.rootProject.findProject(projectPath) != null
    }

private fun kotlinWinRTProjectOrModuleDependency(
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

private fun kotlinWinRTPluginClasspathLocation(project: Project): Any =
    runCatching {
        val location = GenerateWinRTProjectionsTask::class.java.protectionDomain?.codeSource?.location
        requireNotNull(location) { "kotlin-winrt Gradle plugin code source is unavailable." }
        project.files(File(location.toURI()))
    }.getOrElse {
        project.files()
    }

private fun kotlinWinRTCompilerPluginDependency(project: Project): Any {
    val localCompilerPlugin = project.rootProject.findProject(":winrt-compiler-plugin")
    return if (localCompilerPlugin != null) {
        project.dependencies.project(mapOf("path" to localCompilerPlugin.path))
    } else {
        kotlinWinRTCompilerPluginClasspathJar(project)
            ?: "io.github.compose-fluent:winrt-compiler-plugin:${kotlinWinRTPluginVersion()}"
    }
}

private fun kotlinWinRTRuntimeDependency(project: Project): Any {
    return kotlinWinRTLocalOrPluginUnderTestDependency(
        project = project,
        projectPath = ":winrt-runtime",
        moduleName = "winrt-runtime",
    ) ?: "io.github.compose-fluent:winrt-runtime:${kotlinWinRTPluginVersion()}"
}

private fun kotlinWinRTRuntimeClasspathDependency(project: Project): Any {
    return kotlinWinRTLocalOrPluginUnderTestDependency(
        project = project,
        projectPath = ":winrt-runtime",
        moduleName = "winrt-runtime",
    )
        ?: kotlinWinRTCodeSourceFile("io.github.composefluent.winrt.runtime.Guid")?.let(project::files)
        ?: "io.github.compose-fluent:winrt-runtime:${kotlinWinRTPluginVersion()}"
}

private fun kotlinWinRTAuthoringDependency(project: Project): Any {
    return kotlinWinRTLocalOrPluginUnderTestDependency(
        project = project,
        projectPath = ":winrt-authoring",
        moduleName = "winrt-authoring",
    ) ?: "io.github.compose-fluent:winrt-authoring:${kotlinWinRTPluginVersion()}"
}

private fun kotlinWinRTAuthoringRuntimeClasspathDependency(project: Project): Any {
    return kotlinWinRTLocalOrPluginUnderTestDependency(
        project = project,
        projectPath = ":winrt-authoring",
        moduleName = "winrt-authoring",
    )
        ?: kotlinWinRTCodeSourceFile("io.github.composefluent.winrt.authoring.WinRTAuthoringHostExports")?.let(project::files)
        ?: "io.github.compose-fluent:winrt-authoring:${kotlinWinRTPluginVersion()}"
}

private fun kotlinWinRTLocalOrPluginUnderTestDependency(
    project: Project,
    projectPath: String,
    moduleName: String,
): Any? {
    val localProject = project.rootProject.findProject(projectPath)
    if (localProject != null) {
        return project.dependencies.project(mapOf("path" to localProject.path))
    }
    return kotlinWinRTPluginMetadataArtifact(project, moduleName)
}

private fun kotlinWinRTCompilerPluginRuntimeDependencies(project: Project): List<Any> {
    val runtimeDependencies = mutableListOf<Any>()
    runtimeDependencies += kotlinWinRTRuntimeClasspathDependency(project)
    runtimeDependencies += kotlinWinRTAuthoringRuntimeClasspathDependency(project)
    val localMetadataProject = project.rootProject.findProject(":winrt-metadata")
    if (localMetadataProject != null) {
        runtimeDependencies += project.dependencies.project(mapOf("path" to localMetadataProject.path))
        return runtimeDependencies
    }
    runtimeDependencies += kotlinWinRTPluginMetadataArtifact(project, "winrt-metadata")
        ?: kotlinWinRTCodeSourceFile(WinRTMetadataSource::class.java)
            ?.let(project::files)
        ?: "io.github.compose-fluent:winrt-metadata:${kotlinWinRTPluginVersion()}"
    return runtimeDependencies
}

private fun kotlinWinRTLocalGeneratorWorkerClasspath(project: Project): List<File>? {
    val files = listOf(
        kotlinWinRTCodeSourceFile("io.github.composefluent.winrt.runtime.Guid"),
        kotlinWinRTCodeSourceFile(WinRTMetadataSource::class.java),
        kotlinWinRTCodeSourceFile(KotlinProjectionGenerator::class.java),
    )
    if (files.any { file -> file == null || !file.exists() }) {
        return null
    }
    return files
        .filterNotNull()
        .distinctBy { file -> file.toPath().toAbsolutePath().normalize() }
}

private fun kotlinWinRTPluginMetadataGeneratorWorkerClasspath(): List<File>? {
    val metadataFile = kotlinWinRTPluginUnderTestMetadataFile() ?: return null
    val classpath = Properties().run {
        metadataFile.inputStream().use(::load)
        getProperty("implementation-classpath").orEmpty()
    }
    val files = classpath
        .split(File.pathSeparatorChar)
        .mapNotNull { path -> path.takeIf(String::isNotBlank)?.let(::File) }
        .filter { file ->
            val name = file.name
            (name.startsWith("winrt-runtime-jvm") ||
                name.startsWith("winrt-metadata") ||
                name.startsWith("winrt-generator")) &&
                name.endsWith(".jar") &&
                file.isFile
        }
        .distinctBy { file -> file.toPath().toAbsolutePath().normalize() }
    return files.takeIf { found ->
        found.any { it.name.startsWith("winrt-runtime-jvm") } &&
            found.any { it.name.startsWith("winrt-metadata") } &&
            found.any { it.name.startsWith("winrt-generator") }
    }
}

private fun kotlinWinRTPluginUnderTestMetadataFile(): File? {
    val codeSource = kotlinWinRTCodeSourceFile(KotlinWinRTPlugin::class.java) ?: return null
    var current = codeSource.canonicalFile
    if (current.isFile) {
        current = current.parentFile ?: return null
    }
    while (current != current.parentFile) {
        val metadataFile = current.resolve("pluginUnderTestMetadata/plugin-under-test-metadata.properties")
        if (metadataFile.isFile) {
            return metadataFile
        }
        if (current.name == "build") {
            break
        }
        current = current.parentFile ?: break
    }
    return current.resolve("pluginUnderTestMetadata/plugin-under-test-metadata.properties")
        .takeIf(File::isFile)
}

private fun kotlinWinRTPluginMetadataArtifact(project: Project, moduleName: String): Any? {
    val metadataFile = kotlinWinRTPluginUnderTestMetadataFile() ?: return null
    val classpath = Properties().run {
        metadataFile.inputStream().use(::load)
        getProperty("implementation-classpath").orEmpty()
    }
    return classpath
        .split(File.pathSeparatorChar)
        .mapNotNull { path -> path.takeIf(String::isNotBlank)?.let(::File) }
        .firstOrNull { file ->
            val name = file.name
            name.startsWith(moduleName) && name.endsWith(".jar") && file.isFile
        }
        ?.let(project::files)
}

private fun kotlinWinRTAuthoringScannerRuntimeClasspath(project: Project): Any =
    project.files(
        listOf(
            "kotlin.Unit",
            "kotlinx.coroutines.CoroutineScope",
            "org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment",
            "org.jetbrains.kotlin.psi.KtFile",
        )
            .mapNotNull(::kotlinWinRTCodeSourceFile)
            .distinctBy { file -> file.toPath().toAbsolutePath().normalize() },
    )

private fun kotlinWinRTCompilerPluginClasspathJar(project: Project): Any? {
    return kotlinWinRTPluginMetadataArtifact(project, "winrt-compiler-plugin")
}

private fun kotlinWinRTCodeSourceFile(type: Class<*>): File? {
    val location = type.protectionDomain?.codeSource?.location ?: return null
    return runCatching { File(location.toURI()) }.getOrNull()
}

private fun kotlinWinRTCodeSourceFile(typeName: String): File? =
    runCatching { Class.forName(typeName, false, KotlinWinRTPlugin::class.java.classLoader) }
        .getOrNull()
        ?.let(::kotlinWinRTCodeSourceFile)

private fun kotlinWinRTPluginVersion(): String =
    KotlinWinRTPlugin::class.java.`package`.implementationVersion
        ?: "0.1.0-SNAPSHOT"

private fun explicitMetadataInputFiles(inputs: List<String>): List<File> =
    inputs.mapNotNull { input ->
        when (val source = runCatching { WinRTMetadataSource.parse(input) }.getOrNull()) {
            is WinRTMetadataSource.PathSource -> source.path.toFile()
            is WinRTMetadataSource.NuGetPackage -> source.packagePath.toFile()
            else -> null
        }
    }.filter { it.exists() }

private fun allNuGetPackageSpecs(extension: BaseWinRTExtension): List<String> =
    extension.nugetPackages.map { pkg ->
        "${pkg.packageId}@${pkg.version.get()}"
    }

private fun projectionNuGetPackageSpecs(extension: BaseWinRTExtension): List<String> =
    extension.nugetPackages
        .filter { pkg -> pkg.generateProjection }
        .map { pkg -> "${pkg.packageId}@${pkg.version.get()}" }

private fun kotlinWinRTLocalGenerationRequired(
    project: Project,
    extension: BaseWinRTExtension,
    dependencyIdentityFiles: org.gradle.api.file.FileCollection,
): Provider<Boolean> =
    memoizedBooleanProvider(project) {
        try {
            if (!kotlinWinRTLocalGenerationMetadataPlan(extension, emptySet()).hasLocalProjectionSelection) {
                false
            } else {
                kotlinWinRTCombinedProjectionHasLocalOutput(extension, dependencyIdentityFiles.files)
            }
        } catch (_: Exception) {
            true
        }
    }

@Suppress("UNCHECKED_CAST")
private fun kotlinWinRTLocalGenerationRequired(project: Project): Provider<Boolean> =
    project.extensions.extraProperties.properties["kotlinWinRTLocalGenerationRequired"] as? Provider<Boolean>
        ?: project.provider { false }

internal fun memoizedBooleanProvider(
    project: Project,
    compute: () -> Boolean,
): Provider<Boolean> =
    project.objects.property(Boolean::class.javaObjectType).apply {
        set(project.provider(compute))
        finalizeValueOnRead()
    }

private fun kotlinWinRTCombinedProjectionHasLocalOutput(
    extension: BaseWinRTExtension,
    dependencyIdentityFiles: Set<File>,
): Boolean {
    if (kotlinWinRTApplicationPackagingOnly(extension)) {
        return false
    }
    val sources = kotlinWinRTLocalGenerationMetadataPlan(extension, dependencyIdentityFiles).sources
    if (sources.isEmpty()) {
        return dependencyOwnedExactTypeHasUnownedSourceAddition(extension, dependencyIdentityFiles)
    }
    val unfilteredModel = WinRTMetadataLoader.loadSources(sources)
    val effectiveIncludeTypes = extension.includeTypes.get() +
        automaticXamlComponentResourceDictionaryTypes(unfilteredModel, extension.includeTypes.get().toSet())
    val dependencyProjectionSurfaceTypes = dependencyProjectionSurfaceTypeNames(dependencyIdentityFiles)
    val model = unfilteredModel.filterProjectionSurface(
        namespaces = extension.includeNamespaces.get().toSet(),
        types = (effectiveIncludeTypes + dependencyProjectionSurfaceTypes).toSet(),
        excludedNamespaces = extension.excludeNamespaces.get().toSet(),
        excludedTypes = extension.excludeTypes.get().toSet(),
        additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
    )
    val projectionContext = WinRTMetadataProjectionContext(
        sources = sources,
        include = extension.includeNamespaces.get().toSet() + effectiveIncludeTypes.toSet() + dependencyProjectionSurfaceTypes.toSet(),
        exclude = extension.excludeNamespaces.get().toSet() + extension.excludeTypes.get().toSet(),
        excludedTypes = extension.excludeTypes.get().toSet(),
        additionExclude = extension.additionExcludeNamespaces.get().toSet(),
    )
    val inventory = model.projectionInventory(projectionContext)
    val dependencyProjectionTypeNames = dependencyProjectedTypeNames(model, dependencyIdentityFiles)
    val hasLocalProjectedTypes = inventory.namespaces
        .asSequence()
        .flatMap { namespace -> namespace.projectedTypes.asSequence() }
        .map { projected -> projected.type.qualifiedName }
        .any { typeName -> typeName !in dependencyProjectionTypeNames }
    if (hasLocalProjectedTypes) {
        return true
    }
    val dependencySourceAdditions = dependencySourceAdditionTypeNames(dependencyIdentityFiles)
    return inventory.namespaceAdditions
        .asSequence()
        .flatMap { addition -> addition.generatedTypeNames.asSequence() }
        .any { typeName -> typeName !in dependencySourceAdditions }
}

private fun dependencyOwnedExactTypeHasUnownedSourceAddition(
    extension: BaseWinRTExtension,
    dependencyIdentityFiles: Set<File>,
): Boolean {
    val requestedTypes = extension.includeTypes.get().toSet()
    if (requestedTypes.isEmpty()) {
        return false
    }
    val dependencyProjectedTypes = dependencyIdentityFiles
        .filter(File::isFile)
        .flatMap { identityFile -> readProjectionSurfaceIdentity(identityFile).currentShapeProjectedTypes() }
        .toSet()
    if (!dependencyProjectedTypes.containsAll(requestedTypes)) {
        return false
    }
    val context = WinRTMetadataProjectionContext(
        sources = emptyList(),
        include = extension.includeNamespaces.get().toSet() + requestedTypes,
        exclude = extension.excludeNamespaces.get().toSet() + extension.excludeTypes.get().toSet(),
        excludedTypes = extension.excludeTypes.get().toSet(),
        additionExclude = extension.additionExcludeNamespaces.get().toSet(),
    )
    val additions = WinRTMetadataModel(namespaces = emptyList()).projectionInventory(context).namespaceAdditions
    val dependencySourceAdditions = dependencySourceAdditionTypeNames(dependencyIdentityFiles)
    return additions
        .asSequence()
        .flatMap { addition -> addition.generatedTypeNames.asSequence() }
        .any { typeName -> typeName !in dependencySourceAdditions }
}

private fun kotlinWinRTApplicationPackagingOnly(extension: BaseWinRTExtension): Boolean =
    extension is WinRTExtension &&
        extension.applicationEnabled.get() &&
        extension.metadataInputs.get().isEmpty() &&
        extension.includeNamespaces.get().isEmpty() &&
        extension.includeTypes.get().isEmpty() &&
        !extension.generateWindowsSdkProjection.get()

private data class KotlinWinRTLocalGenerationMetadataPlan(
    val sources: List<WinRTMetadataSource>,
    val hasLocalProjectionSelection: Boolean,
)

private fun kotlinWinRTLocalGenerationMetadataPlan(
    extension: BaseWinRTExtension,
    dependencyIdentityFiles: Set<File>,
): KotlinWinRTLocalGenerationMetadataPlan {
    val explicitSources = extension.metadataInputs.get().map(WinRTMetadataSource::parse)
    val hasProjectionFilter = extension.includeNamespaces.get().isNotEmpty() || extension.includeTypes.get().isNotEmpty()
    val projectionPackageSpecs = projectionNuGetPackageSpecs(extension)
    val packageSpecs = (projectionPackageSpecs + dependencyIdentityFiles.flatMap(::readNuGetPackages))
        .distinct()
        .sorted()
    val sdkSource = if (extension.windowsSdkDeclared.get()) {
        listOf(
            WinRTMetadataSource.windowsSdk(
                version = extension.windowsSdkVersion.orNull,
                includeExtensions = extension.includeWindowsSdkExtensions.get(),
            ),
        )
    } else {
        emptyList()
    }
    val nugetSources = if (packageSpecs.isEmpty()) {
        emptyList()
    } else {
        val nugetRoots = WinRTNuGetPackageResolver.globalPackagesRoots(
            explicitRoots = extension.nugetGlobalPackagesRoots.get().map(Path::of),
        )
        packageSpecs
            .map(::parseNuGetPackageIdentity)
            .map { identity ->
                WinRTMetadataSource.nugetPackage(
                    packageId = identity.normalizedPackageId,
                    version = identity.normalizedVersion,
                    globalPackagesRoots = nugetRoots,
                )
            }
    }
    return KotlinWinRTLocalGenerationMetadataPlan(
        sources = explicitSources + sdkSource + nugetSources,
        hasLocalProjectionSelection = explicitSources.isNotEmpty() ||
            hasProjectionFilter ||
            extension.generateWindowsSdkProjection.get() ||
            projectionPackageSpecs.isNotEmpty(),
    )
}

private fun kotlinWinRTLocalCompilerSupportDependencies(
    project: Project,
    mergeCompilerSupportTask: TaskProvider<MergeWinRTCompilerSupportTask>,
): Provider<List<TaskProvider<MergeWinRTCompilerSupportTask>>> =
    project.provider {
        if (kotlinWinRTLocalGenerationRequired(project).get()) {
            listOf(mergeCompilerSupportTask)
        } else {
            emptyList()
        }
    }

private fun addGeneratedProjectionSourcesToKotlinMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java) ?: return
    kotlinExtension.sourceSets.named("main").configure { sourceSet ->
        sourceSet.kotlin.srcDir(conditionalGeneratedProjectionSourceDirectory(project, generatedSources))
    }
}

private fun addGeneratedSourcesToKotlinMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java) ?: return
    kotlinExtension.sourceSets.named("main").configure { sourceSet ->
        sourceSet.kotlin.srcDir(generatedSources)
    }
}

private fun configureWinRTIdentityProjectDependencies(
    project: Project,
    identityDependencies: org.gradle.api.artifacts.Configuration,
    includeExternalModules: Boolean,
) {
    val registeredProjectPaths = linkedSetOf<String>()
    val registeredExternalModules = linkedSetOf<String>()
    fun canRegisterIdentityDependency(): Boolean =
        identityDependencies.state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED

    fun registerKnownWinRTProjectDependency(dependency: ProjectDependency) {
        if (!canRegisterIdentityDependency() || !registeredProjectPaths.add(dependency.path)) {
            return
        }
        identityDependencies.dependencies.add(dependency.copy())
    }

    fun registerDependency(
        dependency: ProjectDependency,
        allowUnverifiedKmpProducer: Boolean,
    ) {
        val dependencyProject = project.findProject(dependency.path)
        if (allowUnverifiedKmpProducer || dependencyProject?.hasKotlinWinRTIdentityMetadata() == true) {
            registerKnownWinRTProjectDependency(dependency)
        }
        dependencyProject?.plugins?.withType(KotlinWinRTPlugin::class.java)?.all(
            Action {
                registerKnownWinRTProjectDependency(dependency)
            },
        )
    }
    fun registerDependency(dependency: ExternalModuleDependency) {
        if (!canRegisterIdentityDependency()) {
            return
        }
        kotlinWinRTIdentityExternalModuleNames(dependency.name).forEachIndexed { index, moduleName ->
            val key = listOf(dependency.group, moduleName, dependency.version).joinToString(":")
            if (!registeredExternalModules.add(key)) {
                return@forEachIndexed
            }
            val identityDependency = if (index == 0) {
                dependency.copy()
            } else {
                val group = dependency.group
                val version = dependency.version
                project.dependencies.create("$group:$moduleName:$version")
            }
            identityDependencies.dependencies.add(identityDependency)
        }
    }
    fun observeConfiguration(configuration: org.gradle.api.artifacts.Configuration) {
        if (!configuration.name.isWinRTIdentityDependencySourceConfiguration()) {
            return
        }
        configuration.dependencies.all { dependency ->
            when (dependency) {
                is ProjectDependency -> registerDependency(
                    dependency = dependency,
                    allowUnverifiedKmpProducer = includeExternalModules && configuration.name
                        .isKotlinMultiplatformWinRTIdentitySourceConfiguration(),
                )
                is ExternalModuleDependency -> if (includeExternalModules) registerDependency(dependency)
            }
        }
    }
    project.configurations.configureEach(::observeConfiguration)
}

private fun String.isWinRTIdentityDependencySourceConfiguration(): Boolean =
    this == "api" ||
        this == "implementation" ||
        isKotlinMultiplatformWinRTIdentitySourceConfiguration()

private fun String.isKotlinMultiplatformWinRTIdentitySourceConfiguration(): Boolean =
    "Main" in this && (
        endsWith("Api") ||
            endsWith("Implementation") ||
            endsWith("ApiMetadata") ||
            endsWith("ImplementationMetadata")
        )

internal fun kotlinWinRTIdentityExternalModuleNames(moduleName: String): List<String> {
    val rootModuleName = kotlinTargetModuleSuffixes.firstNotNullOfOrNull { suffix ->
        moduleName.removeSuffix("-$suffix").takeIf { candidate ->
            candidate != moduleName && candidate.isNotBlank()
        }
    }
    return if (rootModuleName == null) listOf(moduleName) else listOf(moduleName, rootModuleName)
}

private val kotlinTargetModuleSuffixes = listOf(
    "androidnativearm32",
    "androidnativearm64",
    "androidnativex64",
    "androidnativex86",
    "iossimulatorarm64",
    "watchossimulatorarm64",
    "tvossimulatorarm64",
    "linuxarm64",
    "linuxx64",
    "macosarm64",
    "macosx64",
    "mingwx64",
    "wasmwasi",
    "wasmjs",
    "iosarm64",
    "iosx64",
    "tvosarm64",
    "tvosx64",
    "watchosarm32",
    "watchosarm64",
    "watchosx64",
    "mingw",
    "jvm",
    "js",
)

private fun kotlinWinRTIdentityFiles(
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

private fun configureKotlinWinRTMultiplatformWinuiSourceSet(project: Project) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    val winuiMain = kotlinExtension.sourceSets.maybeCreate("winuiMain")
    val commonMain = kotlinExtension.sourceSets.maybeCreate("commonMain")
    winuiMain.dependsOnIfAbsent(commonMain)
    val winuiTest = kotlinExtension.sourceSets.maybeCreate("winuiTest")
    val commonTest = kotlinExtension.sourceSets.maybeCreate("commonTest")
    winuiTest.dependsOnIfAbsent(commonTest)
    kotlinExtension.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
        target.compilations.named("main").configure { compilation ->
            compilation.defaultSourceSet.dependsOnIfAbsent(winuiMain)
        }
        target.compilations.matching { compilation -> compilation.name == "test" }.configureEach { compilation ->
            compilation.defaultSourceSet.dependsOnIfAbsent(winuiTest)
        }
    }
    kotlinExtension.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
        if (!target.isMingwX64Target()) {
            return@configureEach
        }
        target.compilations.named("main").configure { compilation ->
            compilation.defaultSourceSet.dependsOnIfAbsent(winuiMain)
        }
        target.compilations.matching { compilation -> compilation.name == "test" }.configureEach { compilation ->
            compilation.defaultSourceSet.dependsOnIfAbsent(winuiTest)
        }
    }
}

private fun KotlinSourceSet.dependsOnIfAbsent(sourceSet: KotlinSourceSet) {
    if (!dependsOn.contains(sourceSet)) {
        dependsOn(sourceSet)
    }
}

private fun addGeneratedProjectionSourcesToKotlinMultiplatformWinuiMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlinExtension.sourceSets.matching { sourceSet -> sourceSet.name == "winuiMain" }.configureEach { sourceSet ->
        sourceSet.kotlin.srcDir(conditionalGeneratedProjectionSourceDirectory(project, generatedSources))
    }
}

private fun conditionalGeneratedProjectionSourceDirectory(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
): org.gradle.api.provider.Provider<File> =
    project.provider {
        if (kotlinWinRTLocalGenerationRequired(project).get()) {
            generatedSources.get().asFile
        } else {
            project.layout.buildDirectory.dir("generated/kotlin-winrt-disabled").get().asFile
        }
    }

private fun addGeneratedSourcesToKotlinMultiplatformWinuiMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlinExtension.sourceSets.matching { sourceSet -> sourceSet.name == "winuiMain" }.configureEach { sourceSet ->
        sourceSet.kotlin.srcDir(generatedSources)
    }
}

private fun addGeneratedSourcesToKotlinMultiplatformWinuiMain(
    project: Project,
    generatedSourcesTask: TaskProvider<out Task>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlinExtension.sourceSets.matching { sourceSet -> sourceSet.name == "winuiMain" }.configureEach { sourceSet ->
        sourceSet.kotlin.srcDir(generatedSourcesTask)
    }
}

private fun addGeneratedSourcesToKotlinMultiplatformMingwX64Main(
    project: Project,
    generatedSourcesTask: TaskProvider<out Task>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlinExtension.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
        if (!target.isMingwX64Target()) {
            return@configureEach
        }
        target.compilations.named("main").configure { compilation ->
            compilation.defaultSourceSet.kotlin.srcDir(generatedSourcesTask)
        }
    }
}

private fun KotlinNativeTarget.isMingwX64Target(): Boolean =
    konanTarget.name.equals("mingw_x64", ignoreCase = true)

private fun KotlinNativeCompile.isMingwX64CompileTask(): Boolean =
    target.equals("mingw_x64", ignoreCase = true) ||
        target.equals("mingwX64", ignoreCase = true) ||
        name.contains("mingw", ignoreCase = true)

private fun kotlinWinRTAuthoringTargetArtifactName(project: Project): Provider<String> =
    kotlinWinRTAuthoringTargetArtifactName(project, compileTaskName = null)

private fun kotlinWinRTAuthoringTargetArtifactName(
    project: Project,
    compileTaskName: String?,
): Provider<String> =
    project.provider {
        kotlinWinRTJvmTargetJarArchiveFileName(project, compileTaskName)
            ?: kotlinWinRTJavaJarArchiveFileName(project)
            ?: "${project.name}.jar"
    }

private fun kotlinWinRTJvmTargetJarArchiveFileName(
    project: Project,
    compileTaskName: String?,
): String? =
    kotlinWinRTJvmTargetJarTaskNames(project, compileTaskName)
        .asSequence()
        .mapNotNull { taskName -> project.tasks.findByName(taskName) as? Jar }
        .firstOrNull()
        ?.archiveFileName
        ?.get()

private fun kotlinWinRTJavaJarArchiveFileName(project: Project): String? =
    (project.tasks.findByName("jar") as? Jar)?.archiveFileName?.get()

private fun kotlinWinRTJvmTargetJarTask(
    project: Project,
    compileTaskName: String,
): TaskProvider<Jar>? =
    kotlinWinRTJvmTargetJarTaskNames(project, compileTaskName)
        .firstNotNullOfOrNull { taskName ->
            runCatching { project.tasks.named(taskName, Jar::class.java) }.getOrNull()
        }

private fun kotlinWinRTJvmTargetJarArchiveFilePathCollection(
    project: Project,
    compileTaskName: String,
): org.gradle.api.file.FileCollection =
    project.files(
        project.provider {
            kotlinWinRTJvmTargetJarTaskNames(project, compileTaskName)
                .asSequence()
                .mapNotNull { taskName -> project.tasks.findByName(taskName) as? Jar }
                .map { task -> task.archiveFile.get().asFile }
                .firstOrNull()
                ?.let(::listOf)
                .orEmpty()
        },
    )

private fun kotlinWinRTJvmTargetJarTaskNames(
    project: Project,
    compileTaskName: String?,
): List<String> {
    val compileTargetName = compileTaskName
        ?.takeIf { taskName -> taskName.startsWith("compileKotlin") && taskName != "compileKotlin" }
        ?.removePrefix("compileKotlin")
        ?.replaceFirstChar(Char::lowercaseChar)
    if (!compileTargetName.isNullOrBlank()) {
        return listOf("${compileTargetName}Jar")
    }
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return emptyList()
    val jvmTargets = kotlinExtension.targets.withType(KotlinJvmTarget::class.java).map { target -> target.name }
    return if (jvmTargets.size == 1) {
        listOf("${jvmTargets.single()}Jar")
    } else {
        emptyList()
    }
}

private fun kotlinWinRTNativeAuthoringTargetArtifactName(project: Project): Provider<String> =
    project.provider { "${kotlinNativeSharedLibraryFileStem(project.name)}.dll" }

private fun kotlinNativeSharedLibraryFileStem(name: String): String =
    name
        .map { char -> if (char.isLetterOrDigit() || char == '_') char else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "winrt_component" }

private fun configureKotlinWinRTCompilerPluginOptions(
    project: Project,
    metadataIndex: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
    authoringAssemblyName: org.gradle.api.provider.Provider<String>,
    authoringTargetArtifactName: org.gradle.api.provider.Provider<String>,
    nativeAuthoringTargetArtifactName: org.gradle.api.provider.Provider<String> = authoringTargetArtifactName,
    compilerSupportManifest: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
) {
    project.tasks.withType(KotlinNativeCompile::class.java).configureEach(Action<KotlinNativeCompile> { task ->
        val freeCompilerArgs = task.compilerOptions.freeCompilerArgs
        addWinRTCompilerPluginOptions(
            project = project,
            freeCompilerArgs = freeCompilerArgs,
            metadataIndex = metadataIndex,
            outputs = compilerAuthoringOutputs(
                outputDirectory = nativeAuthoringOutputDirectory(project, task.name),
                projectName = project.name,
            ),
            authoringAssemblyName = authoringAssemblyName,
            authoringTargetArtifactName = nativeAuthoringTargetArtifactName,
            compilerSupportManifest = compilerSupportManifest,
        )
    })
    project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
        task.compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
        task.compilerOptions.freeCompilerArgs.add("-Xjdk-release=25")
        val freeCompilerArgs = task.compilerOptions.freeCompilerArgs
        val outputs = compilerAuthoringOutputs(
            outputDirectory = project.layout.dir(project.provider {
                task.destinationDirectory.get().asFile
            }),
            projectName = project.name,
        )
        task.outputs.file(outputs.typeIndex)
        task.outputs.file(outputs.authoredCandidates)
        task.outputs.file(outputs.authoredMetadata)
        task.outputs.file(outputs.authoredWinmd)
        task.outputs.file(outputs.authoredHostManifest)
        addWinRTCompilerPluginOptions(
            project = project,
            freeCompilerArgs = freeCompilerArgs,
            metadataIndex = metadataIndex,
            outputs = outputs,
            authoringAssemblyName = authoringAssemblyName,
            authoringTargetArtifactName = kotlinWinRTAuthoringTargetArtifactName(project, task.name),
            compilerSupportManifest = compilerSupportManifest,
        )
    })
}

private fun addWinRTCompilerPluginOptions(
    project: Project,
    freeCompilerArgs: org.gradle.api.provider.ListProperty<String>,
    metadataIndex: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
    outputs: CompilerAuthoringOutputs,
    authoringAssemblyName: org.gradle.api.provider.Provider<String>,
    authoringTargetArtifactName: org.gradle.api.provider.Provider<String>,
    compilerSupportManifest: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
) {
    freeCompilerArgs.addAll(
        project.provider {
            val authoringOptions = listOf(
                "metadataIndex=${metadataIndex.get().asFile.absolutePath}",
                "typeIndexOutput=${outputs.typeIndex.get().asFile.absolutePath}",
                "authoredCandidatesOutput=${outputs.authoredCandidates.get().asFile.absolutePath}",
                "authoredMetadataOutput=${outputs.authoredMetadata.get().asFile.absolutePath}",
                "authoredWinmdOutput=${outputs.authoredWinmd.get().asFile.absolutePath}",
                "authoredHostManifestOutput=${outputs.authoredHostManifest.get().asFile.absolutePath}",
                "authoringAssemblyName=${authoringAssemblyName.get()}",
                "authoringTargetArtifactName=${authoringTargetArtifactName.get()}",
            )
            val projectionSupportOptions = if (kotlinWinRTLocalGenerationRequired(project).get()) {
                listOf(
                    "compilerSupportManifest=${compilerSupportManifest.get().asFile.absolutePath}",
                    "compilerSupportClassOutputDirectory=${outputs.outputDirectory.get().asFile.absolutePath}",
                )
            } else {
                emptyList()
            }
            (authoringOptions + projectionSupportOptions)
                .flatMap { option -> listOf("-P", "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:$option") }
        },
    )
}

private fun kotlinWinRTAuthoringSourceDirs(project: Project): List<File> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.let { kotlinExtension ->
        val sourceSets = linkedSetOf<KotlinSourceSet>()
        fun collectSourceSet(sourceSet: KotlinSourceSet) {
            if (!sourceSets.add(sourceSet)) {
                return
            }
            sourceSet.dependsOn.forEach(::collectSourceSet)
        }
        kotlinExtension.targets.withType(KotlinJvmTarget::class.java).forEach { target ->
            collectSourceSet(target.compilations.getByName("main").defaultSourceSet)
        }
        kotlinExtension.targets.withType(KotlinNativeTarget::class.java)
            .filter(KotlinNativeTarget::isMingwX64Target)
            .forEach { target ->
                collectSourceSet(target.compilations.getByName("main").defaultSourceSet)
            }
        return sourceSets
            .flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
            .distinctBy { sourceDir -> sourceDir.toPath().toAbsolutePath().normalize() }
            .filter(::containsKotlinSourceFile)
    }
    val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java) ?: return emptyList()
    return kotlinExtension.sourceSets.findByName("main")
        ?.kotlin
        ?.srcDirs
        .orEmpty()
        .filter(::containsKotlinSourceFile)
}

private fun containsKotlinSourceFile(sourceDir: File): Boolean {
    if (!sourceDir.isDirectory) return false
    return sourceDir.walkTopDown().any { file -> file.isFile && file.extension == "kt" }
}

private fun localAuthoredHostManifestFiles(project: Project, manifestFile: Provider<RegularFile>) =
    project.files(manifestFile)
        .filter(::authoredHostManifestDeclaresActivatableClasses)

private fun existingNuGetPackageContentRoots(
    packageSpecs: List<String>,
    explicitGlobalPackagesRoots: List<String>,
): List<File> {
    val roots = WinRTNuGetPackageResolver.globalPackagesRoots(
        explicitRoots = explicitGlobalPackagesRoots.map(Path::of),
    )
    return packageSpecs
        .map(::parseNuGetPackageIdentity)
        .flatMap { identity ->
            runCatching {
                WinRTNuGetPackageResolver.resolveClosure(identity, roots)
            }.getOrElse {
                emptyList()
            }
        }
        .map { resolved -> resolved.packageRoot.toFile() }
        .distinctBy { it.toPath().toAbsolutePath().normalize().toString().lowercase() }
}

private fun Project.hasKotlinWinRTIdentityMetadata(): Boolean =
    configurations.findByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)?.isCanBeConsumed == true
