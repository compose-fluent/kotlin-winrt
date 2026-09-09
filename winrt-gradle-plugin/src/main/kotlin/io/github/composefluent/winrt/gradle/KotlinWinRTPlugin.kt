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
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.attributes.Attribute
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
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import java.io.File
import java.util.Properties
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Path
import java.nio.file.Files
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

class KotlinWinRTPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("winRT", WinRTExtension::class.java, project)
        val windowsSdkRegistryRoots = windowsSdkRegistryRootsProvider(project)
        configureWinRTRuntimeDependency(project)
        configureWinRTGeneration(project, extension, windowsSdkRegistryRoots)
        configureWinRTLibraryModel(project, extension, windowsSdkRegistryRoots)
        configureWinRTApplicationModel(project, extension, windowsSdkRegistryRoots)
        configureAppxResourceGeneration(project, extension)
    }
}

const val KOTLIN_WINRT_IDENTITY_CONFIGURATION: String = "kotlinWinRTIdentity"
const val KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION: String = "kotlinWinRTIdentityElements"
const val KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION: String = "kotlinWinRTCompilerPlugin"
const val KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION: String = "kotlinWinRTGeneratorWorker"
const val KOTLIN_WINRT_IDENTITY_USAGE: String = "kotlin-winrt-identity"
const val KOTLIN_WINRT_APPX_RESOURCES_ELEMENTS_CONFIGURATION: String = "kotlinWinRTAppxResourcesElements"
const val KOTLIN_WINRT_APPX_RESOURCES_CONFIGURATION: String = "kotlinWinRTAppxResources"
const val KOTLIN_WINRT_APPX_RESOURCES_USAGE: String = "kotlin-winrt-appx-resources"
const val KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY: String = "kotlin-winrt-runtime-assets"
internal val KOTLIN_WINRT_APPX_RESOURCE_TARGET_ATTRIBUTE: Attribute<String> =
    Attribute.of("io.github.composefluent.winrt.appx-resource-target", String::class.java)
private const val KOTLIN_WINRT_COMPILER_PLUGIN_ID: String = "io.github.composefluent.winrt.compiler"
private const val KOTLIN_WINRT_LIBRARY_DEPENDENCY_IDENTITY_CONFIGURATION: String = "kotlinWinRTLibraryDependencyIdentity"

/** Resolves the configured Gradle Java toolchain instead of inheriting the daemon JVM. */
private fun configuredJvmToolchainHome(
    project: Project,
    options: WinRTApplicationOptions,
): Provider<String> = project.provider {
    val service = project.extensions.findByType(JavaToolchainService::class.java)
        ?: throw org.gradle.api.GradleException(
            "Gradle Java toolchain service is unavailable; cannot build the Kotlin/WinRT JVM host. " +
                "Apply a Kotlin/JVM or Java plugin with toolchain support.",
        )
    val launcher = service.launcherFor(Action<JavaToolchainSpec> { spec ->
        spec.languageVersion.set(JavaLanguageVersion.of(options.jvmToolchainVersion.get()))
    }).get()
    launcher.metadata.installationPath.asFile.absolutePath
}

fun Project.registerWinRTApplicationHostRunTask(
    name: String,
): TaskProvider<RunWinRTApplicationHostTask> =
    registerWinRTApplicationHostRunTask(name, Action {})

fun Project.registerWinRTApplicationHostRunTask(
    name: String,
    configure: Action<in RunWinRTApplicationHostTask>,
): TaskProvider<RunWinRTApplicationHostTask> {
    val application = extensions.getByType(WinRTExtension::class.java).application
    require(application.variants.isEmpty()) {
        "Named applications require application.variants.named(\"name\") { runTask(...) } to select a host."
    }
    val jvmVariants = defaultWinRTApplicationVariants(this)
        .filter { variant -> variant.kind == WinRTApplicationVariantKind.Jvm }
    require(jvmVariants.size == 1) {
        "A custom JVM run task requires exactly one matching JVM target variant. " +
            "Use a named application or run the generated runWinRTApplicationHost<Target><Compilation> task."
    }
    val applicationHostTask = tasks.named(
        "buildWinRTApplicationHost${winRTApplicationTaskSuffix(jvmVariants.single())}",
        BuildWinRTApplicationHostTask::class.java,
    )
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
    windowsSdkRegistryRoots: Provider<List<String>>,
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
    val appxResourceArtifactTask = project.tasks.register(
        "packageWinRTAppxResources",
        GenerateAppxResourcesArtifactTask::class.java,
        Action<GenerateAppxResourcesArtifactTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Packages this module's AppX resources for downstream WinRT applications."
            task.outputFile.set(project.layout.buildDirectory.file("libs/${project.name}-appx-resources.zip"))
            task.resourceRoots.set(project.provider { appxResourceArtifactRoots(project).map(Path::toString) })
            task.resourceInputs.from(
                project.provider { appxResourceFiles(appxResourceArtifactRoots(project)).map(Path::toFile) },
            )
        },
    )
    val appxResourceElements = project.configurations.create(
        KOTLIN_WINRT_APPX_RESOURCES_ELEMENTS_CONFIGURATION,
        Action { configuration ->
            configuration.isCanBeConsumed = true
            configuration.isCanBeResolved = false
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_APPX_RESOURCES_USAGE),
            )
            configuration.outgoing.artifact(appxResourceArtifactTask.flatMap { it.outputFile }, Action { artifact ->
                artifact.builtBy(appxResourceArtifactTask)
                artifact.type = "zip"
            })
        },
    )
    val dependencyAppxResources = project.configurations.create(
        KOTLIN_WINRT_APPX_RESOURCES_CONFIGURATION,
        Action { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_APPX_RESOURCES_USAGE),
            )
        },
    )
    project.plugins.withId("maven-publish") {
        project.components.withType(AdhocComponentWithVariants::class.java).configureEach { component ->
            component.addVariantsFromConfiguration(appxResourceElements) { details ->
                if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) details.skip() else details.mapToOptional()
            }
        }
    }
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        appxResourceElements.isCanBeConsumed = false
    }
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
    // A plain Java/Kotlin library has one generic resource variant rather than target-specific
    // KMP variants. Mirror its ordinary source dependencies into that optional variant so Maven
    // metadata keeps the transitive AppX resource graph (for example library -> base).
    configureWinRTAppxResourceDependencies(
        project = project,
        appxResourceDependencies = dependencyAppxResources,
        selectedVariant = null,
        applicationEnabled = extension.applicationEnabled,
    )
    // Keep the optional resource variant's dependency graph separate from JVM/Native runtime
    // classpaths while still publishing WinRT resource artifacts of transitive modules.
    appxResourceElements.extendsFrom(dependencyAppxResources)
    configureKmpAppxResourceArtifactVariants(project)
    val dependencyIdentityFiles = kotlinWinRTIdentityFiles(project, dependencyIdentities)
    val localGenerationRequired = kotlinWinRTLocalGenerationRequired(
        project = project,
        extension = extension,
        dependencyIdentityFiles = dependencyIdentityFiles,
        windowsSdkRegistryRoots = windowsSdkRegistryRoots,
    )
    project.extensions.extraProperties["kotlinWinRTLocalGenerationRequired"] = localGenerationRequired
    project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
        task.emitProjectionSources.set(localGenerationRequired)
    }
    project.tasks.named("generateWinAppConfiguration", GenerateWinAppConfigurationTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.named("restoreWinAppDependencies", RestoreWinAppDependenciesTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.named("mergeWinRTCompilerSupport", MergeWinRTCompilerSupportTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.tasks.withType(GenerateWinRTCompilerAuthoredTypeDetailsTask::class.java).configureEach { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    project.extensions.extraProperties["kotlinWinRTIdentityElements"] = identityElements.name
    project.extensions.extraProperties["kotlinWinRTAppxResourcesElements"] = appxResourceElements.name
    project.extensions.extraProperties["kotlinWinRTAppxResources"] = dependencyAppxResources.name
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
    windowsSdkRegistryRoots: Provider<List<String>>,
) {
    var configured = false
    extension.whenApplicationConfigured {
        if (configured) return@whenApplicationConfigured
        configured = true
        val application = extension.application
        if (application.variants.isEmpty()) {
            configureDefaultWinRTApplicationVariants(project, extension, windowsSdkRegistryRoots)
        } else {
            application.bindRunTasks {
                throw org.gradle.api.GradleException("Configure runTask inside an application variant when declaring named applications.")
            }
            val aggregates = registerWinRTApplicationAggregateTasks(project, "named WinRT applications")
            val taskSuffixes = linkedSetOf<String>()
            application.variants.all { options ->
                val suffix = winRTApplicationTaskSuffix(options.name)
                require(taskSuffixes.add(suffix.lowercase(Locale.ROOT))) {
                    "Application variant '${options.name}' has a task name that collides with another variant."
                }
                val selectedVariant = project.provider {
                    resolveWinRTApplicationVariant(project, options).let { selected ->
                        selected.copy(id = "${options.name}--${selected.id}")
                    }
                }
                configureWinRTApplicationTasks(
                    project = project,
                    extension = extension,
                    windowsSdkRegistryRoots = windowsSdkRegistryRoots,
                    options = options,
                    selectedVariant = selectedVariant,
                    taskSuffix = suffix,
                    bindRunTasks = true,
                    eagerJvmSelection = false,
                    observeVariantDependenciesImmediately = false,
                )
                aggregates.forEach { (name, aggregate) ->
                    aggregate.configure { it.dependsOn(project.tasks.named(name + suffix)) }
                }
            }
            project.afterEvaluate {
                val nativeOwners = linkedMapOf<String, String>()
                application.variants.forEach { options ->
                    val selected = resolveWinRTApplicationVariant(project, options)
                    if (selected.kind == WinRTApplicationVariantKind.MingwX64) {
                        val previous = nativeOwners.putIfAbsent(selected.id, options.name)
                        require(previous == null) {
                            "Application variants '$previous' and '${options.name}' select the same Native executable '${selected.id}'. " +
                                "Declare a separate executable for each application."
                        }
                    }
                }
                validateWinRTApplicationPackageOutputs(
                    project,
                    application.variants.map { options -> options.name to winRTApplicationTaskSuffix(options.name) },
                )
            }
        }
    }
}

private fun configureDefaultWinRTApplicationVariants(
    project: Project,
    extension: WinRTExtension,
    windowsSdkRegistryRoots: Provider<List<String>>,
) {
    val application = extension.application
    application.variants.whenObjectAdded {
        throw org.gradle.api.GradleException("Declare named applications in the first winRT.application block.")
    }
    val aggregates = registerWinRTApplicationAggregateTasks(project, "Kotlin target variants")
    val registeredVariants = linkedMapOf<String, Pair<String, String>>()
    val taskSuffixOwners = linkedMapOf<String, String>()

    fun registerVariant(variant: WinRTApplicationVariant, eagerJvmSelection: Boolean) {
        if (!variant.isDefaultApplicationVariant) return
        val variantKey = variant.id.lowercase(Locale.ROOT)
        if (variantKey in registeredVariants) return
        val suffix = winRTApplicationTaskSuffix(variant)
        val previous = taskSuffixOwners.putIfAbsent(suffix.lowercase(Locale.ROOT), variant.id)
        require(previous == null) {
            "Kotlin application variants '$previous' and '${variant.id}' produce the same task suffix '$suffix'."
        }
        registeredVariants[variantKey] = variant.id to suffix
        configureWinRTApplicationTasks(
            project = project,
            extension = extension,
            windowsSdkRegistryRoots = windowsSdkRegistryRoots,
            options = application,
            selectedVariant = project.provider { variant },
            taskSuffix = suffix,
            integrateDefaultJvmLifecycle =
                project.extensions.findByType(KotlinMultiplatformExtension::class.java) == null,
            bindRunTasks = false,
            eagerJvmSelection = eagerJvmSelection,
            observeVariantDependenciesImmediately = true,
        )
        aggregates.forEach { (name, aggregate) ->
            aggregate.configure { it.dependsOn(project.tasks.named(name + suffix)) }
        }
    }

    fun registerDiscoveredVariants() {
        defaultWinRTApplicationVariants(project).forEach { variant ->
            registerVariant(variant, eagerJvmSelection = true)
        }
    }

    registerDiscoveredVariants()
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.targets.withType(KotlinJvmTarget::class.java).all { target ->
            target.compilations.all { compilation ->
                registerVariant(
                    jvmWinRTApplicationVariant(target, compilation),
                    eagerJvmSelection = false,
                )
            }
        }
        kotlin.targets.withType(KotlinNativeTarget::class.java).all { target ->
            if (target.isMingwX64Target()) {
                target.binaries.withType(Executable::class.java).all { executable ->
                    registerVariant(
                        mingwWinRTApplicationVariant(target, executable),
                        eagerJvmSelection = false,
                    )
                }
            }
        }
    }
    application.bindRunTasks { registration ->
        registerDiscoveredVariants()
        val jvmVariants = defaultWinRTApplicationVariants(project)
            .filter { variant -> variant.kind == WinRTApplicationVariantKind.Jvm }
        require(jvmVariants.size == 1) {
            "A custom JVM run task requires exactly one matching JVM target variant. " +
                "Use a named application or run the generated runWinRTApplicationHost<Target><Compilation> task."
        }
        val hostTask = project.tasks.named(
            "buildWinRTApplicationHost${winRTApplicationTaskSuffix(jvmVariants.single())}",
            BuildWinRTApplicationHostTask::class.java,
        )
        project.registerWinRTApplicationHostRunTask(registration.name, hostTask, registration.action)
    }
    project.afterEvaluate {
        require(registeredVariants.isNotEmpty()) {
            "No supported Kotlin/WinRT application variant was found. " +
                "Declare a Kotlin/JVM target or a mingwX64 executable before configuring winRT.application."
        }
        validateWinRTApplicationPackageOutputs(
            project,
            registeredVariants.values,
        )
    }
}

private fun registerWinRTApplicationAggregateTasks(
    project: Project,
    scope: String,
): Map<String, TaskProvider<Task>> =
    WINRT_APPLICATION_TASK_NAMES.associateWith { name ->
        project.tasks.register(name) { task ->
            task.group = "kotlin-winrt"
            task.description = "Runs $name for all $scope."
        }
    }

private fun validateWinRTApplicationPackageOutputs(
    project: Project,
    taskOwners: Iterable<Pair<String, String>>,
) {
    val outputOwners = linkedMapOf<Path, String>()
    taskOwners.forEach { (owner, suffix) ->
        val outputs = listOf(
            project.tasks.named("packageWinRTApplication$suffix", PackageWinRTApplicationTask::class.java).get().outputFile,
            project.tasks.named("signWinRTApplicationPackage$suffix", SignWinRTApplicationPackageTask::class.java).get().outputFile,
        )
        outputs.forEach { output ->
            output.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()?.let { path ->
                val key = Path.of(path.toString().lowercase(Locale.ROOT))
                val previous = outputOwners.putIfAbsent(key, owner)
                require(previous == null) {
                    "Application variants '$previous' and '$owner' share package output $path."
                }
            }
        }
    }
}

private val WINRT_APPLICATION_TASK_NAMES = listOf(
    "generateWinRTApplicationIdentity",
    "buildWinRTAuthoringHost",
    "resolveWinRTRuntimeNuGetPackages",
    "stageWinRTRuntimeAssets",
    "prepareWinRTJvmRuntimeImage",
    "generateWinRTMingwApplicationEntry",
    "stageWinRTApplicationPackage",
    "buildWinRTApplicationHost",
    "runWinRTApplicationHost",
    "packageWinRTApplication",
    "verifyWinRTApplicationPackage",
    "signWinRTApplicationPackage",
    "installWinRTApplicationPackage",
)

internal fun winRTApplicationTaskSuffix(name: String): String {
    require(name.matches(Regex("[A-Za-z][A-Za-z0-9_-]*"))) {
        "Application variant '$name' must start with a letter and contain only letters, digits, '-' or '_'."
    }
    return name.split('-', '_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
}

internal fun winRTApplicationTaskSuffix(variant: WinRTApplicationVariant): String =
    winRTApplicationTaskSuffix(
        listOfNotNull(variant.targetName, variant.compilationName, variant.executableName).joinToString("-"),
    )

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
    windowsSdkRegistryRoots: Provider<List<String>>,
    options: WinRTApplicationOptions,
    selectedVariant: Provider<WinRTApplicationVariant>,
    taskSuffix: String,
    integrateDefaultJvmLifecycle: Boolean = false,
    bindRunTasks: Boolean,
    eagerJvmSelection: Boolean,
    observeVariantDependenciesImmediately: Boolean,
) {
    fun taskName(base: String): String = base + taskSuffix
    val namedOutputPath = "/variant-$taskSuffix"
    val configurationSuffix = "Application$taskSuffix"
    val identityConfigurationName = KOTLIN_WINRT_IDENTITY_CONFIGURATION + configurationSuffix
    val resourceConfigurationName = KOTLIN_WINRT_APPX_RESOURCES_CONFIGURATION + configurationSuffix
    if (project.configurations.findByName(identityConfigurationName) != null) {
        return
    }
    val unpackagedMode = options.packageMode.map { it == WinRTApplicationPackageMode.Unpackaged }
    val identityDependencies = project.configurations.create(
        identityConfigurationName,
        Action { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
            )
        },
    )
    val dependencyIdentityFiles = kotlinWinRTIdentityFiles(project, identityDependencies)
    val dependencyAppxResources = project.configurations.maybeCreate(resourceConfigurationName).apply {
        isCanBeConsumed = false
        isCanBeResolved = true
        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, KOTLIN_WINRT_APPX_RESOURCES_USAGE))
    }
    configureWinRTIdentityProjectDependencies(
        project, identityDependencies, includeExternalModules = true,
        selectedVariant = selectedVariant.takeIf {
            project.extensions.findByType(KotlinMultiplatformExtension::class.java) != null
        },
        observeSelectedVariantImmediately = observeVariantDependenciesImmediately,
    )
    configureWinRTAppxResourceDependencies(project, dependencyAppxResources, selectedVariant)
    val appxResourceVariantRegistry = AppxResourceVariantRegistry()
    project.dependencies.components.all(Action<ComponentMetadataDetails> { metadata ->
        metadata.allVariants { variant ->
            variant.attributes { attributes ->
                if (isAppxResourceUsage(attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name)) {
                    appxResourceVariantRegistry.observeModule(metadata.id.group, metadata.id.name)
                }
            }
        }
    })
    val dependencyAppxResourceView = project.configurations
        .getByName(resourceConfigurationName)
        .incoming
        .artifactView { view ->
            // Ordinary source dependencies do not publish this optional capability. The view is
            // lenient only so those dependencies can remain absent; the provider below turns any
            // failure for a component that actually publishes a WinRT resource variant into a
            // hard error before the files reach package staging.
            view.isLenient = true
            view.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_APPX_RESOURCES_USAGE),
            )
            view.attributes.attributeProvider(
                KOTLIN_WINRT_APPX_RESOURCE_TARGET_ATTRIBUTE,
                selectedVariant.map { variant -> variant.appxResourceTargetIdentity() },
            )
        }
    val dependencyAppxResourceArchives = project.configurations
        .getByName(resourceConfigurationName)
        .let {
            project.files(project.provider {
                discoverAppxResourceVariants(project, it, appxResourceVariantRegistry)
                val artifacts = dependencyAppxResourceView.artifacts
                validateAppxResourceVariantResolution(
                    configuration = it,
                    failures = artifacts.failures,
                    resolvedArtifacts = artifacts.artifacts,
                    requestedTarget = selectedVariant.get().appxResourceTargetIdentity(),
                    resourceVariantRegistry = appxResourceVariantRegistry,
                )
                artifacts.artifactFiles.files
            })
        }
    dependencyAppxResourceArchives.builtBy(dependencyAppxResourceView.files)
    val projectName = project.name
    // Attribute the optional resource graph only after the final application variant is known.
    // KMP producers only expose target-specific artifacts. A generic artifact is reserved for
    // a genuinely target-independent Java/JVM producer.
    project.afterEvaluate {
        dependencyAppxResources.attributes.attribute(
            KOTLIN_WINRT_APPX_RESOURCE_TARGET_ATTRIBUTE,
            selectedVariant.get().appxResourceTargetIdentity(),
        )
    }
    val hasMingwReleaseExecutable = selectedVariant.map { variant ->
        variant.kind == WinRTApplicationVariantKind.MingwX64
    }
    val appxResourceTargetSourceSetNames = project.provider {
        listOf(selectedVariant.get().sourceSetName)
    }
    val appxResourceRoots = project.provider {
        appxResourceRoots(project, appxResourceTargetSourceSetNames.get())
    }
    val defaultAppxManifestFiles = project.provider {
        if (options.appxManifestFiles.files.isNotEmpty()) {
            emptyList<File>()
        } else {
            findAppxManifest(collectAppxResourceInputs(appxResourceRoots.get()))
                ?.let(::listOf)
                .orEmpty()
        }
    }
    val defaultAppxResourceFiles = project.provider {
        appxResourceFiles(appxResourceRoots.get()).map { path -> path.toFile() }
    }
    val restoreWinAppDependenciesTask = project.tasks.named(
        "restoreWinAppDependencies",
        RestoreWinAppDependenciesTask::class.java,
    )
    project.tasks.named("generateWinAppConfiguration", GenerateWinAppConfigurationTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
    restoreWinAppDependenciesTask.configure { task ->
        task.dependencyIdentityFiles.from(dependencyIdentityFiles)
    }
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
        taskName("generateWinRTApplicationIdentity"),
        GenerateWinRTApplicationIdentityTask::class.java,
        Action<GenerateWinRTApplicationIdentityTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Aggregates Kotlin WinRT identity metadata from application dependencies."
            task.outputFile.set(project.layout.buildDirectory.file("generated/kotlin-winrt/identity$namedOutputPath/kotlin-winrt-application.json"))
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
    val runtimeAssetsDirectory = project.layout.buildDirectory.dir(
        project.provider {
            "kotlin-winrt/application-layout/${selectedVariant.get().id.toSafeDirectoryName()}/runtime-assets"
        },
    )
    val buildAuthoringHostTask = project.tasks.register(
        taskName("buildWinRTAuthoringHost"),
        BuildWinRTAuthoringHostTask::class.java,
        Action<BuildWinRTAuthoringHostTask> { task ->
            task.group = "kotlin-winrt"
            task.applicationCompilationTasks.set(selectedVariant.map { it.compilationTaskNames(project) })
            task.description = "Builds reference-aligned native JVM host DLLs for authored WinRT activation."
            task.outputDirectory.set(
                project.layout.buildDirectory.dir(
                    project.provider {
                        "kotlin-winrt/authoring-host/${selectedVariant.get().id.toSafeDirectoryName()}/bin"
                    },
                ),
            )
            task.generatedSourceDirectory.set(
                project.layout.buildDirectory.dir(
                    project.provider {
                        "kotlin-winrt/authoring-host/${selectedVariant.get().id.toSafeDirectoryName()}/src"
                    },
                ),
            )
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.javaHome.set(configuredJvmToolchainHome(project, options))
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            task.commandWorkingDirectory.set(project.layout.projectDirectory)
            task.dependencyIdentityFiles.from(dependencyIdentityFiles)
            task.onlyIf {
                // A release mingw executable is self-contained native output; building the JVM
                // authoring host would require MSVC/clang-cl and would only pollute its package.
                !hasMingwReleaseExecutable.get()
            }
        },
    )
    val resolveRuntimeNuGetPackagesTask = project.tasks.register(
        taskName("resolveWinRTRuntimeNuGetPackages"),
        ResolveWinRTRuntimeNuGetPackagesTask::class.java,
        Action<ResolveWinRTRuntimeNuGetPackagesTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Resolves WinRT NuGet runtime package roots for application asset staging."
            task.outputFile.set(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/runtime-nuget-packages$namedOutputPath/kotlin-winrt-runtime-nuget-packages.json",
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
            task.onlyIf { !task.restoreNuGetPackages.get() }
        },
    )
    val stageRuntimeAssetsTask = project.tasks.register(
        taskName("stageWinRTRuntimeAssets"),
        StageWinRTRuntimeAssetsTask::class.java,
        Action<StageWinRTRuntimeAssetsTask> { task ->
            task.group = "kotlin-winrt"
            task.applicationCompilationTasks.set(selectedVariant.map { it.compilationTaskNames(project) })
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
            task.winAppRuntimeAssetDirectories.from(
                restoreWinAppDependenciesTask.flatMap { restore -> restore.winAppDirectory }.map { directory ->
                    directory.dir("bin")
                },
            )
            task.winAppRestoreLockFiles.from(restoreWinAppDependenciesTask.flatMap { restore -> restore.winmdLockFile })
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
            task.includeFrameworkRuntimeAssets.set(project.provider {
                val outputFile = options.packageOutputFile.orNull?.asFile
                options.windowsAppSdkDeployment.get() == WinRTWindowsAppSdkDeployment.SelfContained ||
                    options.packageMode.get() != WinRTApplicationPackageMode.Packaged ||
                    !options.generatePackage.get() ||
                    options.makeAppxExecutable.get().isNotBlank() ||
                    outputFile?.name?.endsWith(".appx", ignoreCase = true) == true
            })
            task.includeJvmAuthoringArtifacts.set(hasMingwReleaseExecutable.map { hasNativeExecutable -> !hasNativeExecutable })
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.generateProjectPri.set(options.generateProjectPri)
            task.projectPriIndexName.set(project.provider { options.projectPriIndexName.orNull.orEmpty() })
            task.projectPriFallbackIndexName.set(project.name)
            task.projectPriInitialPath.set(options.projectPriInitialPath)
            task.projectPriDefaultLanguage.set(options.projectPriDefaultLanguage)
            task.projectPriDefaultQualifiers.set(options.projectPriDefaultQualifiers)
            task.enableDefaultProjectPriResources.set(options.enableDefaultProjectPriResources)
            task.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            task.defaultProjectPriResourceFiles.from(
                project.provider {
                    if (options.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.resw")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                            spec.exclude("**/appxResources/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriLayoutFiles.from(
                project.provider {
                    if (options.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.xaml")
                            spec.include("**/*.xbf")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                            spec.exclude("**/appxResources/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriContentFiles.from(
                project.provider {
                    if (options.enableDefaultProjectPriResources.get()) {
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
                            spec.exclude("**/appxResources/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.appxManifestFiles.from(options.appxManifestFiles, defaultAppxManifestFiles)
            task.resolvedNuGetPackageManifestFiles.from(resolveRuntimeNuGetPackagesTask.flatMap { it.outputFile })
            task.winAppRestoreLockFiles.from(restoreWinAppDependenciesTask.flatMap { it.winmdLockFile })
            task.projectPriResourceFiles.from(options.projectPriResourceFiles)
            task.projectPriLayoutFiles.from(options.projectPriLayoutFiles)
            task.projectPriContentFiles.from(options.projectPriContentFiles)
            task.projectPriEmbedFiles.from(options.projectPriEmbedFiles)
            task.projectPriTargetPaths.set(options.projectPriTargetPaths)
            task.projectPriExcludedFromBuildPaths.set(options.projectPriExcludedFromBuildPaths)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            task.executableBaseName.set(project.name)
            task.dependencyIdentityFiles.from(dependencyIdentityFiles)
            task.authoredHostDllFiles.from(project.fileTree(buildAuthoringHostTask.flatMap { it.outputDirectory }) { spec ->
                spec.include("*.dll")
            })
            task.dependsOn("generateWinRTProjections")
            task.dependsOn(buildAuthoringHostTask)
            task.dependsOn(resolveRuntimeNuGetPackagesTask)
            task.dependsOn(restoreWinAppDependenciesTask)
        },
    )
    val prepareJvmRuntimeImageTask = project.tasks.register(
        taskName("prepareWinRTJvmRuntimeImage"),
        PrepareWinRTJvmRuntimeImageTask::class.java,
        Action<PrepareWinRTJvmRuntimeImageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Prepares the bundled JVM runtime image for the selected WinRT application variant."
            task.runtimeMode.set(options.jvmRuntimeMode.map { it.name })
            task.javaHome.set(configuredJvmToolchainHome(project, options))
            task.expectedJavaMajor.set(options.jvmToolchainVersion)
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.sourceImage.set(options.jvmRuntimeImage)
            task.modules.set(options.jvmRuntimeModules)
            task.outputDirectory.set(
                project.provider {
                    project.layout.buildDirectory
                        .dir("kotlin-winrt/application-layout/${selectedVariant.get().id.toSafeDirectoryName()}/jvm-runtime")
                        .get()
                },
            )
            task.onlyIf {
                selectedVariant.get().kind == WinRTApplicationVariantKind.Jvm &&
                    task.runtimeMode.get() == WinRTJvmRuntimeMode.Bundled.name
            }
        },
    )
    val mingwApplicationEntryTask = project.tasks.register(
        taskName("generateWinRTMingwApplicationEntry"),
        GenerateWinRTMingwApplicationEntryTask::class.java,
        Action<GenerateWinRTMingwApplicationEntryTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Generates the Kotlin/Native mingw application entry wrapper with WinUI bootstrap."
            task.outputDirectory.set(
                project.provider {
                    project.layout.buildDirectory.dir(
                        "generated/kotlin-winrt-application-entry/${selectedVariant.get().id.toSafeDirectoryName()}/src/kotlin",
                    ).get()
                },
            )
            task.entryPointFunctionName.set("main$taskSuffix")
            task.mainClass.set(options.mainClass)
            task.packageMode.set(project.provider { options.packageMode.get().name })
        },
    )
    addGeneratedSourcesToSelectedKotlinMultiplatformMingwCompilation(
        project,
        mingwApplicationEntryTask,
        selectedVariant,
    )
    project.tasks.matching { task -> task.name == "generateWinRTProjections" }.configureEach(
        Action<Task> { task ->
            // Projection generation inspects KMP source roots, including the selected Native
            // compilation. Declare the entry generator dependency explicitly for Gradle validation.
            task.dependsOn(mingwApplicationEntryTask)
        },
    )
    project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
        task.dependsOn(mingwApplicationEntryTask)
    })
    fun registerApplicationPackageStage(baseName: String) = project.tasks.register(
        taskName(baseName),
        StageWinRTApplicationPackageTask::class.java,
        Action<StageWinRTApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Stages WinRT application package resources and generates the application PRI."
            task.runtimeAssetsDirectory.set(stageRuntimeAssetsTask.flatMap { it.outputDirectory })
            task.outputDirectory.set(
                project.provider {
                    project.layout.buildDirectory
                        .dir("kotlin-winrt/application-layout/${selectedVariant.get().id.toSafeDirectoryName()}/package")
                        .get()
                },
            )
            task.applicationVariant.set(selectedVariant.map { it.id })
            task.resourceResolutionReport.set(
                project.provider {
                    project.layout.buildDirectory.file(
                        "kotlin-winrt/reports/${selectedVariant.get().id.toSafeDirectoryName()}/appx-resource-resolution.json",
                    ).get()
                },
            )
            task.generateProjectPri.set(options.generateProjectPri)
            task.projectPriIndexName.set(project.provider { options.projectPriIndexName.orNull.orEmpty() })
            task.projectPriFallbackIndexName.set(project.name)
            task.projectPriInitialPath.set(options.projectPriInitialPath)
            task.projectPriDefaultLanguage.set(options.projectPriDefaultLanguage)
            task.projectPriDefaultQualifiers.set(options.projectPriDefaultQualifiers)
            task.enableDefaultProjectPriResources.set(options.enableDefaultProjectPriResources)
            task.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            task.defaultProjectPriResourceFiles.from(
                project.provider {
                    if (options.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.resw")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                            spec.exclude("**/appxResources/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriLayoutFiles.from(
                project.provider {
                    if (options.enableDefaultProjectPriResources.get()) {
                        project.fileTree(project.projectDir) { spec ->
                            spec.include("**/*.xaml")
                            spec.include("**/*.xbf")
                            spec.exclude(".gradle/**")
                            spec.exclude("build/**")
                            spec.exclude("**/.gradle/**")
                            spec.exclude("**/build/**")
                            spec.exclude("**/appxResources/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.defaultProjectPriContentFiles.from(
                project.provider {
                    if (options.enableDefaultProjectPriResources.get()) {
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
                            spec.exclude("**/appxResources/**")
                        }
                    } else {
                        project.files()
                    }
                },
            )
            task.appxManifestFiles.from(options.appxManifestFiles, defaultAppxManifestFiles)
            task.resolvedNuGetPackageManifestFiles.from(resolveRuntimeNuGetPackagesTask.flatMap { it.outputFile })
            task.projectPriResourceFiles.from(options.projectPriResourceFiles)
            task.projectPriLayoutFiles.from(options.projectPriLayoutFiles)
            task.projectPriContentFiles.from(options.projectPriContentFiles)
            task.projectPriEmbedFiles.from(options.projectPriEmbedFiles)
            task.packagePayloadFiles.from(options.packagePayloadFiles)
            task.defaultAppxResourceRoots.set(
                appxResourceRoots.map { roots -> roots.map(Path::toString) },
            )
            task.defaultAppxResourceFiles.from(defaultAppxResourceFiles)
            task.appxResourceArchives.from(dependencyAppxResourceArchives)
            task.projectPriTargetPaths.set(options.projectPriTargetPaths)
            task.projectPriExcludedFromBuildPaths.set(options.projectPriExcludedFromBuildPaths)
            task.makePriExecutable.set(options.makePriExecutable)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.includeFrameworkPackageDependencies.set(project.provider {
                val packageOutput = options.packageOutputFile.orNull?.asFile
                val usesLegacyMakeAppx = options.makeAppxExecutable.get().isNotBlank() ||
                    packageOutput?.name?.endsWith(".appx", ignoreCase = true) == true
                options.packageMode.get() == WinRTApplicationPackageMode.Packaged &&
                    options.windowsAppSdkDeployment.get() == WinRTWindowsAppSdkDeployment.FrameworkDependent &&
                    usesLegacyMakeAppx
            })
            task.executableBaseName.set(project.name)
            task.deferredManifestPayloadPaths.set(
                hasMingwReleaseExecutable.map { hasNativeExecutable ->
                    if (hasNativeExecutable) emptyList() else listOf("$projectName.exe")
                },
            )
            task.reservedPackageFiles.set(
                hasMingwReleaseExecutable.map { native -> if (native) emptyList() else listOf("$projectName.exe") },
            )
            task.reservedPackageDirectories.set(
                hasMingwReleaseExecutable.map { native -> if (native) emptyList() else listOf("runtime", "lib") },
            )
            task.dependsOn(stageRuntimeAssetsTask)
        },
    )
    val stageApplicationPackageTask = registerApplicationPackageStage("stageWinRTApplicationPackage")
    configureMingwApplicationEntry(
        project,
        mingwApplicationEntryTask,
        stageRuntimeAssetsTask,
        stageApplicationPackageTask,
        selectedVariant,
        options.console,
    )
    val applicationHostTask = project.tasks.register(
        taskName("buildWinRTApplicationHost"),
        BuildWinRTApplicationHostTask::class.java,
        Action<BuildWinRTApplicationHostTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Builds the native Kotlin/WinRT JVM application host with Windows App SDK deployment initialization."
            task.outputDirectory.set(
                project.provider {
                    project.layout.buildDirectory
                        .dir("kotlin-winrt/application-layout/${selectedVariant.get().id.toSafeDirectoryName()}/jvm-host")
                        .get()
                },
            )
            task.applicationVariant.set(selectedVariant.map { it.id })
            task.generatedSourceDirectory.set(
                project.provider {
                    project.layout.buildDirectory.get()
                        .dir("kotlin-winrt/application-host/${selectedVariant.get().id.toSafeDirectoryName()}/src")
                },
            )
            task.packageMode.set(project.provider { options.packageMode.get().name })
            task.console.set(options.console)
            task.executableBaseName.set(project.name)
            task.javaHome.set(configuredJvmToolchainHome(project, options))
            task.expectedJavaMajor.set(options.jvmToolchainVersion)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.commandWorkingDirectory.set(project.layout.projectDirectory)
            task.runtimeAssetsDirectory.from(stageApplicationPackageTask.flatMap { it.outputDirectory })
            task.jvmRuntimeMode.set(options.jvmRuntimeMode.map { it.name })
            task.runtimeImageDirectory.set(prepareJvmRuntimeImageTask.flatMap { it.outputDirectory })
            task.externalJvmHome.set(
                project.provider {
                    options.externalJvmHome.orNull?.asFile?.absolutePath.orEmpty()
                },
            )
            task.onlyIf { selectedVariant.get().kind == WinRTApplicationVariantKind.Jvm }
            task.dependsOn("generateWinRTProjections")
            task.dependsOn("mergeWinRTCompilerSupport")
            task.dependsOn(stageRuntimeAssetsTask)
            task.dependsOn(stageApplicationPackageTask)
            task.dependsOn(buildAuthoringHostTask)
            task.dependsOn(prepareJvmRuntimeImageTask)
        },
    )
    val runApplicationHostTask = project.registerWinRTApplicationHostRunTask(
        taskName("runWinRTApplicationHost"),
        applicationHostTask,
        Action {},
    )
    runApplicationHostTask.configure { task ->
        task.onlyIf { selectedVariant.get().kind == WinRTApplicationVariantKind.Jvm }
    }
    if (bindRunTasks) {
        options.bindRunTasks { registration ->
            project.registerWinRTApplicationHostRunTask(
                registration.name,
                applicationHostTask,
                registration.action,
            )
        }
    }
    val applicationPackageDirectory = project.provider {
        if (selectedVariant.get().kind == WinRTApplicationVariantKind.MingwX64) {
            stageApplicationPackageTask.get().outputDirectory.get()
        } else {
            applicationHostTask.get().outputDirectory.get()
        }
    }
    val developmentPackageTask = registerApplicationPackageStage("stageWinRTApplicationDevelopmentPackage")
    developmentPackageTask.configure { task ->
        task.description = "Stages an isolated development identity and regenerates its application PRI."
        task.runtimeAssetsDirectory.set(applicationPackageDirectory)
        task.developmentIdentity.set(true)
        task.outputDirectory.set(project.layout.buildDirectory.dir(
            selectedVariant.map { "kotlin-winrt/application-run/${it.id.toSafeDirectoryName()}/input" },
        ))
        task.resourceResolutionReport.set(project.layout.buildDirectory.file(
            selectedVariant.map { "kotlin-winrt/reports/${it.id.toSafeDirectoryName()}/development-appx-resource-resolution.json" },
        ))
        task.dependsOn(project.provider {
            if (selectedVariant.get().kind == WinRTApplicationVariantKind.Jvm) {
                applicationHostTask
            } else {
                stageApplicationPackageTask
            }
        })
    }
    // Launch tasks are concrete-only: variants can share a package identity and cannot be
    // registered concurrently by an aggregate run task.
    val runApplicationPackageTask = project.tasks.register(
        taskName("runWinRTApplicationPackage"),
        RunWinRTApplicationPackageTask::class.java,
        Action<RunWinRTApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Registers and runs this variant as a packaged development application through WinApp CLI."
            task.packageDirectory.set(developmentPackageTask.flatMap { it.outputDirectory })
            task.deploymentDirectory.set(
                project.layout.buildDirectory.dir(
                    selectedVariant.map { "kotlin-winrt/application-run/${it.id.toSafeDirectoryName()}/AppX" },
                ),
            )
            task.packageMode.set(options.packageMode.map { it.name })
            task.selfContained.set(options.windowsAppSdkDeployment.map {
                it == WinRTWindowsAppSdkDeployment.SelfContained
            })
            task.applicationVariant.set(selectedVariant.map { it.id })
            task.winAppCliExecutable.set(extension.winAppCliExecutable)
            task.winAppCliCacheDirectory.set(
                project.layout.dir(project.provider {
                    project.gradle.gradleUserHomeDir.resolve("caches/kotlin-winrt/winapp-cli")
                }),
            )
            task.winAppWorkspace.set(
                project.layout.dir(
                    restoreWinAppDependenciesTask.flatMap { it.configurationFile }
                        .map { it.asFile.parentFile },
                ),
            )
            task.offline.set(project.provider { project.gradle.startParameter.isOffline })
            task.dependsOn(restoreWinAppDependenciesTask)
            task.dependsOn(developmentPackageTask)
        },
    )
    val packageApplicationTask = project.tasks.register(
        taskName("packageWinRTApplication"),
        PackageWinRTApplicationTask::class.java,
        Action<PackageWinRTApplicationTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Packages the staged WinRT application payload into an appx/msix package."
            task.applicationVariant.set(selectedVariant.map { it.id })
            task.packageDirectory.set(applicationPackageDirectory)
            task.outputFile.set(
                options.packageOutputFile.orElse(
                    project.layout.buildDirectory.file(
                        project.provider {
                            "kotlin-winrt/packages/${project.name}-${selectedVariant.get().id.toSafeDirectoryName()}.msix"
                        },
                    ),
                ),
            )
            task.generatePackage.set(options.generatePackage)
            task.packageMode.set(options.packageMode.map { it.name })
            task.selfContained.set(options.windowsAppSdkDeployment.map {
                it == WinRTWindowsAppSdkDeployment.SelfContained
            })
            task.makeAppxExecutable.set(options.makeAppxExecutable)
            task.winAppCliExecutable.set(extension.winAppCliExecutable)
            task.winAppCliVersion.set(WinAppCliDefaults.VERSION)
            task.winAppCliPackageSha512.set(WinAppCliDefaults.PACKAGE_SHA512)
            task.winAppCliCacheDirectory.set(
                project.layout.dir(
                    project.provider {
                        project.gradle.gradleUserHomeDir.resolve("caches/kotlin-winrt/winapp-cli")
                    },
                ),
            )
            task.winAppWorkspace.set(
                project.layout.dir(
                    restoreWinAppDependenciesTask.flatMap { restore -> restore.configurationFile }
                        .map { configuration -> configuration.asFile.parentFile },
                ),
            )
            task.offline.set(project.provider { project.gradle.startParameter.isOffline })
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.onlyIf { task.packageMode.get() == WinRTApplicationPackageMode.Packaged.name }
            task.dependsOn(stageApplicationPackageTask)
            task.dependsOn(restoreWinAppDependenciesTask)
        },
    )
    val verifyPackageTask = project.tasks.register(
        taskName("verifyWinRTApplicationPackage"),
        VerifyWinRTApplicationPackageTask::class.java,
        Action<VerifyWinRTApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Verifies the WinRT application appx/msix package layout with WinApp CLI."
            task.applicationVariant.set(selectedVariant.map { it.id })
            task.packageFile.set(packageApplicationTask.flatMap { it.outputFile })
            task.resourceResolutionReport.set(stageApplicationPackageTask.flatMap { it.resourceResolutionReport })
            task.markerFile.set(
                project.layout.buildDirectory.file(
                    project.provider {
                        "kotlin-winrt/packages/${project.name}-${selectedVariant.get().id.toSafeDirectoryName()}.verify.marker"
                    },
                ),
            )
            task.unpackDirectory.set(
                project.layout.buildDirectory.dir(
                    project.provider {
                        "kotlin-winrt/package-verification/${project.name}/${selectedVariant.get().id.toSafeDirectoryName()}"
                    },
                ),
            )
            task.verifyPackage.set(options.verifyPackage)
            task.packageMode.set(options.packageMode.map { it.name })
            task.generatePackage.set(options.generatePackage)
            task.makeAppxExecutable.set(options.makeAppxExecutable)
            task.winAppCliExecutable.set(extension.winAppCliExecutable)
            task.winAppCliVersion.set(WinAppCliDefaults.VERSION)
            task.winAppCliPackageSha512.set(WinAppCliDefaults.PACKAGE_SHA512)
            task.winAppCliCacheDirectory.set(
                project.layout.dir(
                    project.provider {
                        project.gradle.gradleUserHomeDir.resolve("caches/kotlin-winrt/winapp-cli")
                    },
                ),
            )
            task.winAppWorkspace.set(
                project.layout.dir(
                    restoreWinAppDependenciesTask.flatMap { restore -> restore.configurationFile }
                        .map { configuration -> configuration.asFile.parentFile },
                ),
            )
            task.offline.set(project.provider { project.gradle.startParameter.isOffline })
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.onlyIf {
                task.packageMode.get() == WinRTApplicationPackageMode.Packaged.name &&
                    task.verifyPackage.get() &&
                    task.generatePackage.get()
            }
            task.dependsOn(packageApplicationTask)
        },
    )
    val signPackageTask = project.tasks.register(
        taskName("signWinRTApplicationPackage"),
        SignWinRTApplicationPackageTask::class.java,
        Action<SignWinRTApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Signs the WinRT application appx/msix package with signtool."
            task.inputPackageFile.set(packageApplicationTask.flatMap { it.outputFile })
            task.outputFile.set(
                options.signedPackageOutputFile.orElse(
                    project.layout.buildDirectory.file(
                        project.provider {
                            "kotlin-winrt/packages/${project.name}-${selectedVariant.get().id.toSafeDirectoryName()}-signed.msix"
                        },
                    ),
                ),
            )
            task.signPackage.set(options.signPackage)
            task.packageMode.set(options.packageMode.map { it.name })
            task.signToolExecutable.set(options.signToolExecutable)
            task.windowsSdkVersion.set(project.provider { extension.windowsSdkVersion.orNull.orEmpty() })
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.signingCertificateThumbprint.set(options.signingCertificateThumbprint)
            task.signingCertificateFile.set(options.signingCertificateFile)
            task.signingCertificatePassword.set(options.signingCertificatePassword)
            task.signingTimestampUrl.set(options.signingTimestampUrl)
            task.signingHashAlgorithm.set(options.signingHashAlgorithm)
            task.onlyIf {
                task.packageMode.get() == WinRTApplicationPackageMode.Packaged.name &&
                    task.signPackage.get()
            }
            task.dependsOn(packageApplicationTask)
            task.dependsOn(verifyPackageTask)
        },
    )
    val installPackageTask = project.tasks.register(
        taskName("installWinRTApplicationPackage"),
        InstallWinRTApplicationPackageTask::class.java,
        Action<InstallWinRTApplicationPackageTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Installs the WinRT application appx/msix package for local test runs."
            val defaultInstallPackageFile = options.signPackage.flatMap { signPackage ->
                if (signPackage) {
                    signPackageTask.flatMap { it.outputFile }
                } else {
                    packageApplicationTask.flatMap { it.outputFile }
                }
            }
            task.packageFile.set(
                options.installPackageFile.orElse(defaultInstallPackageFile),
            )
            task.dependencyPackageFiles.from(options.dependencyPackageFiles)
            task.dependencyLockFiles.from(restoreWinAppDependenciesTask.flatMap { it.winmdLockFile })
            task.dependencyPackageSpecs.set(project.provider { allNuGetPackageSpecs(extension) })
            task.runtimeIdentifier.set(selectedVariant.map { variant -> variant.runtimeIdentifier })
            task.installPackage.set(options.installPackage)
            task.packageMode.set(options.packageMode.map { it.name })
            task.includeRestoredFrameworkDependencies.set(project.provider {
                options.windowsAppSdkDeployment.get() == WinRTWindowsAppSdkDeployment.FrameworkDependent
            })
            task.powerShellExecutable.set(options.installPowerShellExecutable)
            task.forceApplicationShutdown.set(options.installForceApplicationShutdown)
            task.onlyIf {
                task.packageMode.get() == WinRTApplicationPackageMode.Packaged.name &&
                    task.installPackage.get()
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
            if (integrateDefaultJvmLifecycle && unpackagedMode.get()) {
                task.dependsOn(stageApplicationPackageTask)
            }
            if (task is Copy) {
                if (integrateDefaultJvmLifecycle && unpackagedMode.get()) {
                    task.from(stageApplicationPackageTask.flatMap { it.outputDirectory }, Action<CopySpec> { spec ->
                        spec.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY)
                    })
                }
            }
        })
        project.tasks.withType(JavaExec::class.java).configureEach(Action<JavaExec> { task ->
            if (integrateDefaultJvmLifecycle && unpackagedMode.get()) {
                task.dependsOn(stageApplicationPackageTask)
                task.jvmArgumentProviders.add(
                    RuntimeAssetsRootJvmArgumentProvider(
                        stageApplicationPackageTask.flatMap { it.outputDirectory }.map { it.asFile.absolutePath },
                    ),
                )
            }
        })
    }
    configureKmpJvmApplicationHostClasspath(project, applicationHostTask, selectedVariant, eagerSelection = eagerJvmSelection)
    project.afterEvaluate {
        if (selectedVariant.get().kind == WinRTApplicationVariantKind.Jvm &&
            options.jvmRuntimeMode.get() == WinRTJvmRuntimeMode.External &&
            options.externalJvmHome.orNull == null
        ) {
            throw org.gradle.api.GradleException(
                "External JVM runtime mode was selected for ${selectedVariant.get().id}, but " +
                    "application.externalJvmHome was not configured.",
            )
        }
    }
    packageApplicationTask.configure { task ->
        task.dependsOn(
            project.provider {
                if (selectedVariant.get().kind == WinRTApplicationVariantKind.Jvm) {
                    listOf(applicationHostTask)
                } else {
                    emptyList<Any>()
                }
            },
        )
    }
    project.plugins.withId("application") {
        project.extensions.configure(JavaApplication::class.java, Action<JavaApplication> { application ->
            applicationHostTask.configure { task ->
                task.mainClass.set(options.mainClass.orElse(application.mainClass))
            }
        })
        if (integrateDefaultJvmLifecycle && unpackagedMode.get()) {
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
        task.mainClass.convention(options.mainClass)
    }
    project.extensions.extraProperties["kotlinWinRTIdentity" + taskSuffix] = identityDependencies.name
    project.extensions.extraProperties["kotlinWinRTApplicationIdentityTask" + taskSuffix] = applicationIdentityTask.name
    project.extensions.extraProperties["kotlinWinRTRuntimeNuGetPackagesTask" + taskSuffix] = resolveRuntimeNuGetPackagesTask.name
    project.extensions.extraProperties["kotlinWinRTRuntimeAssetsTask" + taskSuffix] = stageRuntimeAssetsTask.name
    project.extensions.extraProperties["kotlinWinRTApplicationPackageTask" + taskSuffix] = stageApplicationPackageTask.name
    project.extensions.extraProperties["kotlinWinRTApplicationHostTask" + taskSuffix] = applicationHostTask.name
    project.extensions.extraProperties["kotlinWinRTRunApplicationHostTask" + taskSuffix] = runApplicationHostTask.name
    project.extensions.extraProperties["kotlinWinRTRunApplicationPackageTask" + taskSuffix] = runApplicationPackageTask.name
    project.extensions.extraProperties["kotlinWinRTPackageTask" + taskSuffix] = packageApplicationTask.name
    project.extensions.extraProperties["kotlinWinRTVerifyPackageTask" + taskSuffix] = verifyPackageTask.name
    project.extensions.extraProperties["kotlinWinRTSignPackageTask" + taskSuffix] = signPackageTask.name
    project.extensions.extraProperties["kotlinWinRTInstallPackageTask" + taskSuffix] = installPackageTask.name
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
    selectedVariant: Provider<WinRTApplicationVariant>,
    eagerSelection: Boolean,
) {
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        // Keep the selected KMP target in a separate file collection. The application host may
        // be configured before all targets exist; wiring every target as it appears leaks jars
        // from unrelated JVM targets into the eventual application classpath.
        val selectedJvmClasspath = project.files()
        applicationHostTask.configure { task -> task.runtimeClasspath.from(selectedJvmClasspath) }

        var selectedTargetAttached = false

        // Kotlin MPP targets may be declared either before or after the WinRT application block.
        // The application callback handles the normal ProjectBuilder/configuration path, while
        // the afterEvaluate retry handles targets declared later in the build script.
        fun attachSelectedTarget(failIfUnavailable: Boolean) {
            if (selectedTargetAttached) return
            val variant = runCatching { selectedVariant.get() }
                .getOrElse { error ->
                    if (failIfUnavailable) throw error
                    return
                }
            if (variant.kind != WinRTApplicationVariantKind.Jvm) {
                selectedTargetAttached = true
                return
            }
            val target = project.extensions
                .getByType(KotlinMultiplatformExtension::class.java)
                .targets
                .withType(KotlinJvmTarget::class.java)
                .singleOrNull { candidate -> candidate.name.equals(variant.targetName, ignoreCase = true) }
                ?: if (failIfUnavailable) {
                    throw org.gradle.api.GradleException(
                        "Selected JVM target '${variant.targetName}' is no longer available.",
                    )
                } else {
                    return
                }
            val compilation = target.compilations.findByName(variant.compilationName)
                ?: if (failIfUnavailable) {
                    throw org.gradle.api.GradleException(
                        "Selected JVM target '${variant.targetName}' has no '${variant.compilationName}' compilation.",
                    )
                } else {
                    return
                }
            val jvmCompilation = compilation
            val archiveTaskName = jvmCompilation.archiveTaskName
            val jar = if (archiveTaskName != null) {
                project.tasks.findByName(archiveTaskName) as? Jar
                    ?: if (failIfUnavailable) {
                        throw org.gradle.api.GradleException(
                            "Selected JVM target '${variant.targetName}' compilation '${variant.compilationName}' " +
                                "has no $archiveTaskName archive task.",
                        )
                    } else {
                        return
                    }
                project.tasks.named(archiveTaskName, Jar::class.java)
            } else {
                // Kotlin Gradle Plugin does not create archives for custom compilations unless an
                // internal opt-in is enabled. The application host still needs one stable Jar so
                // that its classpath matches the selected compilation rather than main.
                val fallbackTaskName =
                    "${variant.targetName}${variant.compilationName.replaceFirstChar(Char::uppercaseChar)}Jar"
                val existingFallbackTask = project.tasks.findByName(fallbackTaskName)
                if (existingFallbackTask != null && existingFallbackTask !is Jar) {
                    throw org.gradle.api.GradleException(
                        "Selected JVM compilation '${variant.targetName}:${variant.compilationName}' " +
                            "needs a Jar task named '$fallbackTaskName', but that task is " +
                            "${existingFallbackTask::class.java.name}.",
                    )
                }
                val fallbackJar = existingFallbackTask?.let {
                    project.tasks.named(fallbackTaskName, Jar::class.java)
                } ?: project.tasks.register(fallbackTaskName, Jar::class.java) { task ->
                    task.archiveAppendix.convention("${variant.targetName}-${variant.compilationName}")
                }
                fallbackJar.configure { task ->
                    task.from(jvmCompilation.output.allOutputs)
                    task.dependsOn(jvmCompilation.compileTaskProvider)
                    project.tasks.findByName(jvmCompilation.processResourcesTaskName)?.let(task::dependsOn)
                }
                fallbackJar
            }
            selectedJvmClasspath.setFrom(emptyList<Any>())
            selectedJvmClasspath.from(jvmCompilation.runtimeDependencyFiles, jar.flatMap { it.archiveFile })
            applicationHostTask.configure { task -> task.dependsOn(jar) }
            selectedTargetAttached = true
        }

        if (eagerSelection) {
            extension.whenApplicationConfigured { attachSelectedTarget(failIfUnavailable = false) }
        }
        project.afterEvaluate { attachSelectedTarget(failIfUnavailable = true) }
    }
}

private fun configureMingwApplicationEntry(
    project: Project,
    entryTask: TaskProvider<GenerateWinRTMingwApplicationEntryTask>,
    stageRuntimeAssetsTask: TaskProvider<StageWinRTRuntimeAssetsTask>,
    stageApplicationPackageTask: TaskProvider<StageWinRTApplicationPackageTask>,
    selectedVariant: Provider<WinRTApplicationVariant>,
    console: Provider<Boolean>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    val applicationLayoutDirectory = project.provider {
        project.layout.buildDirectory
            .dir("kotlin-winrt/application-layout/${selectedVariant.get().id.toSafeDirectoryName()}/package")
            .get()
            .asFile
    }
    project.afterEvaluate {
        val variant = selectedVariant.get()
        if (variant.kind != WinRTApplicationVariantKind.MingwX64) return@afterEvaluate
        val target = kotlinExtension.targets
            .withType(KotlinNativeTarget::class.java)
            .singleOrNull { it.name.equals(variant.targetName, ignoreCase = true) }
            ?: throw org.gradle.api.GradleException("Selected mingwX64 target '${variant.targetName}' is no longer available.")
        val executable = target.binaries.withType(Executable::class.java)
            .singleOrNull { it.name.equals(variant.executableName, ignoreCase = true) }
            ?: throw org.gradle.api.GradleException(
                "Selected mingwX64 executable '${variant.executableName}' is no longer available on target '${target.name}'.",
            )
        executable.entryPoint = entryTask.flatMap { it.entryPoint }.get()
        executable.linkerOpts(if (console.get()) "-Wl,/SUBSYSTEM:CONSOLE" else "-Wl,/SUBSYSTEM:WINDOWS")
        // Kotlin/Native's model name (for example, releaseExecutable) is not the staged file
        // name. Keep the output file Provider as the single source for payload, manifest and run
        // task wiring so custom binary names and target-specific base names remain consistent.
        val executableOutputFile = project.provider { executable.outputFile }
        val executableOutputName = executableOutputFile.map { outputFile -> outputFile.name }
        val executableOutputBaseName = executableOutputFile.map { outputFile -> outputFile.nameWithoutExtension }
        executable.linkTaskProvider.configure { task -> task.dependsOn(entryTask) }
        stageRuntimeAssetsTask.configure { task -> task.executableBaseName.set(executableOutputBaseName) }
        stageApplicationPackageTask.configure { task ->
            task.dependsOn(executable.linkTaskProvider)
            task.rootPackagePayloadFiles.from(executableOutputFile)
            task.executableBaseName.set(executableOutputBaseName)
        }
        executable.runTaskProvider?.configure { task ->
            task.dependsOn(stageRuntimeAssetsTask)
            task.dependsOn(stageApplicationPackageTask)
            task.workingDir(applicationLayoutDirectory.get())
            task.executable(applicationLayoutDirectory.get().resolve(executableOutputName.get()).absolutePath)
            task.environment(
                "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT",
                stageRuntimeAssetsTask.flatMap { it.outputDirectory }.get().asFile.absolutePath,
            )
        }
    }
}

private fun configureAppxResourceGeneration(
    project: Project,
    extension: WinRTExtension,
) {
    val marker = "kotlinWinRTAppxResourceGenerationConfigured"
    if (project.extensions.extraProperties.has(marker)) return
    project.extensions.extraProperties[marker] = true
    val generatedPackageName = extension.appxResourcePackageName
    val configuredSourceSets = linkedSetOf<String>()

    fun configureSourceSet(sourceSet: KotlinSourceSet) {
        if (!configuredSourceSets.add(sourceSet.name)) return
        val roots = project.provider { appxResourceRoots(project, listOf(sourceSet.name)) }
        val taskName = "generateWinRTAppxResources" + sourceSet.name.replaceFirstChar(Char::uppercaseChar)
        val task = project.tasks.register(taskName, GenerateAppxResourcesTask::class.java) { resourceTask ->
            resourceTask.group = "kotlin-winrt"
            resourceTask.description = "Generates AppX resource accessors for ${sourceSet.name}."
            resourceTask.outputDirectory.set(
                project.layout.buildDirectory.dir("generated/kotlin-winrt/appx-resources/${sourceSet.name}"),
            )
            resourceTask.packageName.set(
                generatedPackageName.map { packageName ->
                    appxResourceAccessorPackageName(packageName, sourceSet.name)
                },
            )
            resourceTask.targetSourceSet.set(sourceSet.name)
            resourceTask.resourceRoots.set(roots.map { resourceRoots -> resourceRoots.map(Path::toString) })
            resourceTask.resourceFiles.from(
                project.provider { appxResourceFiles(roots.get()).map { path -> path.toFile() } },
            )
        }
        sourceSet.kotlin.srcDir(task)
    }

    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        configureSourceSet(kotlin.sourceSets.maybeCreate("winuiMain"))
        // Generate the shared accessor and one accessor namespace for every supported target
        // compilation. A target compilation includes winuiMain, so target-owned resources must
        // not be emitted under the same FQN as the shared accessor.
        kotlin.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
            target.compilations.configureEach { compilation ->
                if (!compilation.name.endsWith("Test", ignoreCase = true)) {
                    configureSourceSet(compilation.defaultSourceSet)
                }
            }
        }
        kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
            if (!target.isMingwX64Target()) return@configureEach
            target.compilations.configureEach { compilation ->
                if (!compilation.name.endsWith("Test", ignoreCase = true)) {
                    configureSourceSet(compilation.defaultSourceSet)
                }
            }
        }
    }
    project.plugins.withId("org.jetbrains.kotlin.jvm") {
        val kotlin = project.extensions.getByType(KotlinProjectExtension::class.java)
        kotlin.sourceSets.matching { it.name == "main" }.configureEach(::configureSourceSet)
    }
}

internal fun appxGeneratedPackageName(mainClass: String): String {
    val normalized = mainClass.trim()
    if (normalized.isBlank()) return "io.github.composefluent.winrt.appx"
    val packageName = if (normalized.endsWith(".MainKt")) {
        normalized.removeSuffix(".MainKt")
    } else {
        normalized.substringBeforeLast('.', missingDelimiterValue = "")
    }
    return packageName.takeIf(String::isNotBlank) ?: "io.github.composefluent.winrt.appx"
}

private fun appxResourceRoots(project: Project, targetSourceSetNames: Iterable<String>): List<Path> {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
    val sourceSetsByName = kotlin?.sourceSets?.associateBy { sourceSet -> sourceSet.name }.orEmpty()
    val visited = linkedSetOf<String>()
    val orderedSourceSetNames = mutableListOf<String>()

    fun collect(name: String) {
        if (!visited.add(name)) return
        sourceSetsByName[name]
            ?.dependsOn
            ?.sortedBy { sourceSet -> sourceSet.name }
            ?.forEach { sourceSet -> collect(sourceSet.name) }
        orderedSourceSetNames += name
    }

    targetSourceSetNames.forEach(::collect)
    validateAppxResourceRootConflicts(project, orderedSourceSetNames, sourceSetsByName)
    return orderedSourceSetNames.map { sourceSetName ->
        project.projectDir.toPath().resolve("src").resolve(sourceSetName).resolve("appxResources")
    }
}

private fun validateAppxResourceRootConflicts(
    project: Project,
    sourceSetNames: List<String>,
    sourceSetsByName: Map<String, KotlinSourceSet>,
) {
    if (sourceSetsByName.isEmpty()) return
    val selectedByPath = linkedMapOf<String, Pair<String, Path>>()
    sourceSetNames.forEach { sourceSetName ->
        val root = project.projectDir.toPath().resolve("src").resolve(sourceSetName).resolve("appxResources")
        if (!root.isDirectory()) return@forEach
        Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() }
                .sorted()
                .forEach { source ->
                    val relative = source.relativeTo(root)
                    val key = relative.toString().replace('\\', '/').lowercase()
                    val previous = selectedByPath[key]
                    if (previous != null &&
                        !sourceSetsAreRelated(previous.first, sourceSetName, sourceSetsByName)
                    ) {
                        throw org.gradle.api.GradleException(
                            "Conflicting AppX resources target '$key' from unrelated source sets " +
                                "'${previous.first}' (${previous.second}) and '$sourceSetName' ($source). " +
                                "Declare an explicit dependency between the source sets or choose an explicit payload.",
                        )
                    }
                    selectedByPath[key] = sourceSetName to source
                }
        }
    }
}

private fun sourceSetsAreRelated(
    first: String,
    second: String,
    sourceSetsByName: Map<String, KotlinSourceSet>,
): Boolean =
    first == second ||
        sourceSetDependsOn(first, second, sourceSetsByName) ||
        sourceSetDependsOn(second, first, sourceSetsByName)

private fun sourceSetDependsOn(
    sourceSetName: String,
    expectedAncestor: String,
    sourceSetsByName: Map<String, KotlinSourceSet>,
    visited: MutableSet<String> = linkedSetOf(),
): Boolean {
    if (!visited.add(sourceSetName)) return false
    val dependencies = sourceSetsByName[sourceSetName]?.dependsOn ?: return false
    return dependencies.any { dependency ->
        dependency.name == expectedAncestor ||
            sourceSetDependsOn(dependency.name, expectedAncestor, sourceSetsByName, visited)
    }
}

private fun appxResourceArtifactRoots(project: Project): List<Path> {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
    return if (kotlin != null) {
        appxResourceRoots(project, listOf("winuiMain"))
    } else {
        appxResourceRoots(project, listOf("main"))
    }
}

private fun configureWinRTGeneration(
    project: Project,
    extension: BaseWinRTExtension,
    windowsSdkRegistryRoots: Provider<List<String>>,
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
    val winAppWorkspace = project.layout.buildDirectory.dir("generated/kotlin-winrt/winapp")
    val includeWinAppToolingPackages = project.provider {
        if (extension !is WinRTExtension || !extension.applicationEnabled.get()) {
            false
        } else {
            val applications = if (extension.application.variants.isEmpty()) {
                listOf(extension.application)
            } else {
                extension.application.variants.toList()
            }
            applications.any { application ->
                application.packageMode.get() == WinRTApplicationPackageMode.Packaged &&
                    application.generatePackage.get() && application.makeAppxExecutable.get().isBlank()
            }
        }
    }
    val generateWinAppConfigurationTask = project.tasks.register(
        "generateWinAppConfiguration",
        GenerateWinAppConfigurationTask::class.java,
        Action<GenerateWinAppConfigurationTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Generates the internal WinApp CLI configuration from Kotlin/WinRT NuGet declarations."
            task.nugetPackages.set(project.provider { allNuGetPackageSpecs(extension) })
            task.includeToolingPackages.set(includeWinAppToolingPackages)
            task.outputFile.set(winAppWorkspace.map { workspace -> workspace.file("winapp.yaml") })
        },
    )
    val restoreWinAppDependenciesTask = project.tasks.register(
        "restoreWinAppDependencies",
        RestoreWinAppDependenciesTask::class.java,
        Action<RestoreWinAppDependenciesTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Restores Kotlin/WinRT NuGet dependencies and WinMD inventory with WinApp CLI."
            task.configurationFile.set(generateWinAppConfigurationTask.flatMap { it.outputFile })
            task.winAppDirectory.set(winAppWorkspace.map { workspace -> workspace.dir(".winapp") })
            task.winmdLockFile.set(task.winAppDirectory.file("winmds.lock.json"))
            task.restoreBaseDirectory.set(
                project.layout.dir(project.provider {
                    extension.nugetConfigDirectory.orNull?.asFile
                        ?: extension.nugetConfigFile.orNull?.asFile?.parentFile
                        ?: project.projectDir
                }),
            )
            task.nugetConfigFile.set(extension.nugetConfigFile)
            task.nugetPackages.set(project.provider { allNuGetPackageSpecs(extension) })
            task.restoreEnabled.set(extension.restoreNuGetPackages)
            task.includeToolingPackages.set(includeWinAppToolingPackages)
            task.winAppCliExecutable.set(extension.winAppCliExecutable)
            task.winAppCliVersion.set(WinAppCliDefaults.VERSION)
            task.winAppCliPackageSha512.set(WinAppCliDefaults.PACKAGE_SHA512)
            task.winAppCliCacheDirectory.set(
                project.layout.dir(
                    project.provider {
                        project.gradle.gradleUserHomeDir.resolve("caches/kotlin-winrt/winapp-cli")
                    },
                ),
            )
            task.offline.set(project.provider { project.gradle.startParameter.isOffline })
            task.dependsOn(generateWinAppConfigurationTask)
        },
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
            task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
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
            task.winAppRestoreLockFiles.from(restoreWinAppDependenciesTask.flatMap { it.winmdLockFile })
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
                    val generatedAppxResourceSourcesPath = project.layout.buildDirectory
                        .dir("generated/kotlin-winrt/appx-resources")
                        .get()
                        .asFile
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                    kotlinWinRTAuthoringSourceDirs(project).filterNot { sourceDir ->
                        val normalizedSourceDir = sourceDir.toPath().toAbsolutePath().normalize()
                        normalizedSourceDir.startsWith(generatedSourcesPath) ||
                            normalizedSourceDir.startsWith(generatedAuthoringSourcesPath) ||
                            normalizedSourceDir.startsWith(generatedMingwApplicationEntrySourcesPath) ||
                            normalizedSourceDir.startsWith(generatedAppxResourceSourcesPath)
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
        task.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
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
            jvmToolchainVersion = (extension as? WinRTExtension)?.application?.jvmToolchainVersion,
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
            jvmToolchainVersion = (extension as? WinRTExtension)?.application?.jvmToolchainVersion,
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
            task.winAppRestoreLockFiles.from(
                project.tasks.named("restoreWinAppDependencies", RestoreWinAppDependenciesTask::class.java)
                    .flatMap { restore -> restore.winmdLockFile },
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
        val ownsCompilation = task.applicationCompilationTasks.map { names ->
            names.isEmpty() || compileTaskName in names
        }
        if (artifactPublication == WinRTAuthoredArtifactPublication.Jvm) {
            task.authoredMetadataFiles.from(project.provider {
                if (ownsCompilation.get()) listOf(outputs.authoredWinmd) else emptyList()
            })
            task.authoredHostManifestFiles.from(project.provider {
                if (ownsCompilation.get()) compilerAuthoredHostManifestFiles else project.files()
            })
        }
        task.dependsOn(ownsCompilation.map { owns -> if (owns) listOf(validationTask) else emptyList() })
    }
    if (artifactPublication == WinRTAuthoredArtifactPublication.Jvm) {
        project.tasks.withType(BuildWinRTAuthoringHostTask::class.java).configureEach { task ->
            val ownsCompilation = task.applicationCompilationTasks.map { names ->
                names.isEmpty() || compileTaskName in names
            }
            task.authoredHostManifestFiles.from(project.provider {
                if (ownsCompilation.get()) compilerAuthoredHostManifestFiles else project.files()
            })
            task.dependsOn(ownsCompilation.map { owns -> if (owns) listOf(validationTask) else emptyList() })
        }
    }
    project.tasks.matching { task -> task.name == "check" }.configureEach(Action<Task> { task ->
        task.dependsOn(validationTask)
    })
    project.tasks.matching { task ->
        task.name == "classes" ||
            task.name == "jar" ||
            task.name == "assemble" ||
            task.name == "processResources"
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
        project.tasks.withType(StageWinRTRuntimeAssetsTask::class.java).configureEach { task ->
            task.dependsOn(exportValidationTask)
        }
        project.tasks.withType(StageWinRTApplicationPackageTask::class.java).configureEach { task ->
            task.dependsOn(exportValidationTask)
        }
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
    runtimeDependencies += kotlinWinRTCompilerPluginSupportDependency(
        project = project,
        projectPath = ":winrt-compiler-plugin:callsite-contract",
        moduleName = "callsite-contract",
    )
    runtimeDependencies += kotlinWinRTCompilerPluginSupportDependency(
        project = project,
        projectPath = ":winrt-compiler-plugin:callsite-lowering",
        moduleName = "callsite-lowering",
    )
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

private fun kotlinWinRTCompilerPluginSupportDependency(
    project: Project,
    projectPath: String,
    moduleName: String,
): Any =
    kotlinWinRTLocalOrPluginUnderTestDependency(
        project = project,
        projectPath = projectPath,
        moduleName = moduleName,
    ) ?: "io.github.compose-fluent:$moduleName:${kotlinWinRTPluginVersion()}"

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
    windowsSdkRegistryRoots: Provider<List<String>>,
): Provider<Boolean> =
    memoizedBooleanProvider(project) {
        try {
            val initialPlan = kotlinWinRTLocalGenerationMetadataPlan(extension, emptySet())
            if (!initialPlan.hasLocalProjectionSelection) {
                false
            } else {
                kotlinWinRTCombinedProjectionHasLocalOutput(
                    extension = extension,
                    dependencyIdentityFiles = dependencyIdentityFiles.files,
                    registryRoots = windowsSdkRegistryRoots.get().orNullIfEmpty().orEmpty(),
                )
            }
        } catch (_: Exception) {
            true
        }
    }

@Suppress("UNCHECKED_CAST")
private fun kotlinWinRTLocalGenerationRequired(project: Project): Provider<Boolean> =
    project.extensions.extraProperties.properties["kotlinWinRTLocalGenerationRequired"] as? Provider<Boolean>
        ?: project.provider { false }

private fun windowsSdkRegistryRootsProvider(project: Project): Provider<List<String>> =
    project.providers.of(WindowsSdkRegistryRootsValueSource::class.java) {}

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
    registryRoots: List<String> = emptyList(),
): Boolean {
    if (kotlinWinRTApplicationPackagingOnly(extension)) {
        return false
    }
    val sources = kotlinWinRTLocalGenerationMetadataPlan(
        extension = extension,
        dependencyIdentityFiles = dependencyIdentityFiles,
        registryRoots = registryRoots,
    ).sources
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
    registryRoots: List<String> = emptyList(),
): KotlinWinRTLocalGenerationMetadataPlan {
    val registryRootPaths = registryRoots.orNullIfEmpty()?.map(Path::of)
    val explicitSources = extension.metadataInputs.get()
        .map(WinRTMetadataSource::parse)
        .map { source -> source.withWindowsSdkRegistryRoots(registryRootPaths) }
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
                registryRoots = registryRootPaths,
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
    selectedVariant: Provider<WinRTApplicationVariant>? = null,
    observeSelectedVariantImmediately: Boolean = false,
) {
    val registeredProjectPaths = linkedSetOf<String>()
    val registeredExternalModules = linkedSetOf<String>()
    val observedConfigurations = linkedSetOf<org.gradle.api.artifacts.Configuration>()
    var resolutionStarted = false
    identityDependencies.incoming.beforeResolve { resolutionStarted = true }
    fun canRegisterIdentityDependency(): Boolean =
        !resolutionStarted && identityDependencies.state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED

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
        if (!observedConfigurations.add(configuration)) {
            return
        }
        if (selectedVariant == null && !configuration.name.isWinRTIdentityDependencySourceConfiguration()) {
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
    if (selectedVariant == null) {
        project.configurations.configureEach(::observeConfiguration)
    } else {
        val observeSelectedVariant = {
            val selected = selectedVariant.get()
            val compilation = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                ?.targets?.getByName(selected.targetName)?.compilations?.getByName(selected.compilationName)
            resourceDependencyConfigurationGraph(project, compilation).forEach(::observeConfiguration)
        }
        if (observeSelectedVariantImmediately) {
            observeSelectedVariant()
        }
        project.gradle.projectsEvaluated { observeSelectedVariant() }
    }
    // KGP can add default dependencies while resolving a compilation's classpath. Optional
    // identity configurations may already be observed for task dependencies at that point,
    // even while Configuration.state still reports UNRESOLVED. End mirroring with the DSL.
    project.gradle.projectsEvaluated { resolutionStarted = true }
}

/**
 * Publishes one optional resource artifact for each supported KMP target source set. Gradle's
 * target attribute keeps Native payloads out of JVM applications. KMP does not publish the
 * generic fallback, which would otherwise conceal an unsupported target.
 */
private fun configureKmpAppxResourceArtifactVariants(
    project: Project,
) {
    val configuredSourceSets = linkedSetOf<String>()
    val resourceCompilations = linkedMapOf<String, KotlinCompilation<*>>()

    fun configureCompilation(compilation: KotlinCompilation<*>, resourceTarget: String) {
        val sourceSetName = compilation.defaultSourceSet.name
        if (!configuredSourceSets.add(sourceSetName)) {
            return
        }
        resourceCompilations[sourceSetName] = compilation
        val suffix = sourceSetName.replaceFirstChar(Char::uppercaseChar)
        val task = project.tasks.register(
            "packageWinRTAppxResources$suffix",
            GenerateAppxResourcesArtifactTask::class.java,
        ) { resourceTask ->
            resourceTask.group = "kotlin-winrt"
            resourceTask.description = "Packages AppX resources for the $sourceSetName target compilation."
            resourceTask.outputFile.set(
                project.layout.buildDirectory.file(
                    "libs/${project.name}-${sourceSetName}-appx-resources.zip",
                ),
            )
            resourceTask.resourceRoots.set(
                project.provider {
                    appxResourceRoots(project, listOf(sourceSetName)).map(Path::toString)
                },
            )
            resourceTask.resourceInputs.from(
                project.provider {
                    appxResourceFiles(appxResourceRoots(project, listOf(sourceSetName))).map(Path::toFile)
                },
            )
        }
        val targetDependencies = project.configurations.maybeCreate(
            "${KOTLIN_WINRT_APPX_RESOURCES_CONFIGURATION}${suffix}Dependencies",
        ).apply {
            isCanBeConsumed = false
            isCanBeResolved = false
            attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_APPX_RESOURCES_USAGE),
            )
        }
        val elements = project.configurations.maybeCreate(
            "${KOTLIN_WINRT_APPX_RESOURCES_ELEMENTS_CONFIGURATION}$suffix",
        ).apply {
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, KOTLIN_WINRT_APPX_RESOURCES_USAGE),
            )
            attributes.attribute(KOTLIN_WINRT_APPX_RESOURCE_TARGET_ATTRIBUTE, resourceTarget)
            extendsFrom(targetDependencies)
            outgoing.artifact(task.flatMap { it.outputFile }) { artifact ->
                artifact.builtBy(task)
                artifact.type = "zip"
            }
        }
        project.plugins.withId("maven-publish") {
            project.components.withType(AdhocComponentWithVariants::class.java).configureEach { component ->
                component.addVariantsFromConfiguration(elements) { details -> details.mapToOptional() }
            }
        }
    }

    // Register one lifecycle callback before Kotlin target callbacks start materializing source
    // sets. Each target-specific resource configuration then receives only its own reachable
    // source dependency graph.
    project.gradle.projectsEvaluated {
        resourceCompilations.forEach { (sourceSetName, compilation) ->
            val suffix = sourceSetName.replaceFirstChar(Char::uppercaseChar)
            val targetDependencies = project.configurations.findByName(
                "${KOTLIN_WINRT_APPX_RESOURCES_CONFIGURATION}${suffix}Dependencies",
            ) ?: return@forEach
            configureWinRTAppxResourceDependenciesForCompilation(project, targetDependencies, compilation)
        }
    }

    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
            target.compilations.configureEach { compilation ->
                if (!compilation.name.endsWith("Test", ignoreCase = true)) {
                    configureCompilation(
                        compilation,
                        appxResourceTargetIdentity(WinRTApplicationVariantKind.Jvm, compilation.name),
                    )
                }
            }
        }
        kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
            if (!target.isMingwX64Target()) return@configureEach
            target.compilations.configureEach { compilation ->
                if (!compilation.name.endsWith("Test", ignoreCase = true)) {
                    configureCompilation(
                        compilation,
                        appxResourceTargetIdentity(WinRTApplicationVariantKind.MingwX64, compilation.name),
                    )
                }
            }
        }
    }
}

/**
 * Mirrors the normal source dependency graph into the AppX resource artifact configuration.
 *
 * Resource artifacts are an optional Gradle variant. Keeping the declarations in a separate
 * consumer configuration means ordinary JVM/Native compilation does not accidentally put zip
 * payloads on its runtime classpath. Non-WinRT dependencies remain optional because they do not
 * publish this capability; the target attribute above still prevents a matching WinRT artifact
 * from leaking across targets.
 */
private fun configureWinRTAppxResourceDependencies(
    project: Project,
    appxResourceDependencies: org.gradle.api.artifacts.Configuration,
    selectedVariant: Provider<WinRTApplicationVariant>?,
    applicationEnabled: Provider<Boolean>? = null,
) {
    val registeredProjectPaths = linkedSetOf<String>()
    val registeredExternalModules = linkedSetOf<String>()

    fun canRegister(): Boolean =
        appxResourceDependencies.state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED

    fun registerProjectDependency(dependency: ProjectDependency) {
        if (!canRegister() || registeredProjectPaths.contains(dependency.path)) return
        val dependencyProject = project.findProject(dependency.path)
        if (dependencyProject?.plugins?.hasPlugin(KotlinWinRTPlugin::class.java) == true) {
            registeredProjectPaths.add(dependency.path)
            appxResourceDependencies.dependencies.add(dependency.copy())
        }
    }

    fun registerExternalDependency(dependency: ExternalModuleDependency) {
        if (!canRegister()) return
        val key = listOf(dependency.group, dependency.name, dependency.version).joinToString(":")
        if (registeredExternalModules.add(key)) {
            appxResourceDependencies.dependencies.add(dependency.copy())
        }
    }

    fun observe(configuration: org.gradle.api.artifacts.Configuration) {
        configuration.dependencies.forEach { dependency ->
            when (dependency) {
                is ProjectDependency -> registerProjectDependency(dependency)
                is ExternalModuleDependency -> registerExternalDependency(dependency)
            }
        }
    }

    // Snapshot only the selected compilation's dependency graph after every project has been
    // evaluated. Listening to dependency additions during resolution can run after this optional
    // configuration has been observed by Kotlin/Native, which makes Gradle reject late mutation.
    project.gradle.projectsEvaluated {
        // The library model is configured for every project, including applications. In an
        // application the selected-variant observer below owns this configuration; the generic
        // observer must stay dormant to avoid adding the same dependency graph twice. KMP
        // libraries likewise use target-specific observers registered by
        // configureKmpAppxResourceArtifactVariants.
        if (selectedVariant == null && (
                project.extensions.findByType(KotlinMultiplatformExtension::class.java) != null ||
                    applicationEnabled?.orNull == true
                )
        ) {
            return@projectsEvaluated
        }
        val variant = selectedVariant?.get()
        val configurations = resourceDependencyConfigurationGraph(
            project,
            variant?.let { selected ->
                project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    ?.targets?.getByName(selected.targetName)
                    ?.compilations?.getByName(selected.compilationName)
            },
        )
        configurations.forEach(::observe)
    }
}

private fun configureWinRTAppxResourceDependenciesForCompilation(
    project: Project,
    appxResourceDependencies: org.gradle.api.artifacts.Configuration,
    compilation: KotlinCompilation<*>,
) {
    val registeredProjectPaths = linkedSetOf<String>()
    val registeredExternalModules = linkedSetOf<String>()

    fun register(dependency: org.gradle.api.artifacts.Dependency) {
        when (dependency) {
            is ProjectDependency -> {
                if (!registeredProjectPaths.add(dependency.path)) return
                val dependencyProject = project.findProject(dependency.path)
                if (dependencyProject?.plugins?.hasPlugin(KotlinWinRTPlugin::class.java) == true) {
                    appxResourceDependencies.dependencies.add(dependency.copy())
                }
            }
            is ExternalModuleDependency -> {
                val key = listOf(dependency.group, dependency.name, dependency.version).joinToString(":")
                if (registeredExternalModules.add(key)) {
                    appxResourceDependencies.dependencies.add(dependency.copy())
                }
            }
        }
    }

    val collectDependencies = {
        resourceDependencyConfigurationGraph(project, compilation)
            .forEach { configuration -> configuration.dependencies.forEach(::register) }
    }
    if (project.state.executed) {
        collectDependencies()
    } else {
        project.afterEvaluate { collectDependencies() }
    }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
private fun resourceDependencyConfigurationGraph(
    project: Project,
    compilation: KotlinCompilation<*>?,
): Set<org.gradle.api.artifacts.Configuration> {
    val rootNames = if (compilation == null) {
        listOf("api", "implementation", "runtimeOnly")
    } else {
        (compilation.allAssociatedCompilations + compilation).flatMap { current ->
            val owners = current.allKotlinSourceSets + current
            owners.flatMap { owner ->
                listOf(owner.apiConfigurationName, owner.implementationConfigurationName, owner.runtimeOnlyConfigurationName)
            }
        }
    }
    val roots = rootNames.distinct().mapNotNull(project.configurations::findByName)
    val visited = linkedSetOf<org.gradle.api.artifacts.Configuration>()
    val pending = ArrayDeque(roots)
    while (pending.isNotEmpty()) {
        val configuration = pending.removeFirst()
        if (!visited.add(configuration)) continue
        pending.addAll(configuration.extendsFrom)
    }
    return visited
}

/**
 * Tracks external components that really publish the optional AppX resource capability.
 *
 * The consumer configuration also mirrors ordinary source dependencies so that a WinRT library
 * can be added without a second dependency declaration. Gradle cannot select the custom resource
 * usage for an ordinary Java/Kotlin module, so those modules are intentionally absent from the
 * artifact view. This registry lets the lenient view distinguish that expected absence from a
 * broken resource variant or a missing resource artifact in a module that advertised one.
 */
private class AppxResourceVariantRegistry {
    private val resourceModules = ConcurrentHashMap.newKeySet<String>()
    private val resourceProjects = ConcurrentHashMap.newKeySet<String>()

    fun observeModule(group: String, module: String) {
        resourceModules += moduleKey(group, module)
    }

    fun observe(component: ResolvedComponentResult) {
        val publishesResourceVariant = component.variants.any { variant ->
            val usage = variant.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name
            usage == KOTLIN_WINRT_APPX_RESOURCES_USAGE || usage == KOTLIN_WINRT_APPX_PUBLISHED_USAGE
        }
        if (!publishesResourceVariant) return
        when (val id = component.id) {
            is ModuleComponentIdentifier -> resourceModules += moduleKey(id.group, id.module)
            is ProjectComponentIdentifier -> resourceProjects += projectKey(id.build.buildPath, id.projectPath)
        }
    }

    fun observeProject(project: Project) {
        if (project.configurations.any { configuration ->
                configuration.isCanBeConsumed && configuration.attributes
                    .getAttribute(Usage.USAGE_ATTRIBUTE)
                    ?.name
                    ?.let(::isAppxResourceUsage) == true
            }
        ) {
            resourceProjects += projectKey(":", project.path)
        }
    }

    fun containsModule(group: String?, module: String?): Boolean =
        group != null && module != null && moduleKey(group, module) in resourceModules

    fun containsProject(buildPath: String?, projectPath: String?): Boolean =
        projectPath != null && (
            projectKey(buildPath ?: ":", projectPath) in resourceProjects ||
                resourceProjects.any { key -> key.endsWith(":$projectPath") }
            )

    fun containsComponent(id: org.gradle.api.artifacts.component.ComponentIdentifier): Boolean = when (id) {
        is ModuleComponentIdentifier -> containsModule(id.group, id.module)
        is ProjectComponentIdentifier -> containsProject(id.build.buildPath, id.projectPath)
        else -> false
    }
}

// Maven publication currently emits the optional AppX usage under this normalized name. Keep it
// as a read-side alias so published resources remain distinguishable from ordinary JVM modules.
private const val KOTLIN_WINRT_APPX_PUBLISHED_USAGE: String = "kotlin-winrt-appx"

private fun discoverAppxResourceVariants(
    project: Project,
    sourceConfiguration: org.gradle.api.artifacts.Configuration,
    registry: AppxResourceVariantRegistry,
) {
    project.rootProject.allprojects.forEach(registry::observeProject)
    val discoveryConfiguration = project.configurations.detachedConfiguration(
        *sourceConfiguration.dependencies.toTypedArray(),
    )
    discoveryConfiguration.attributes.attribute(
        Usage.USAGE_ATTRIBUTE,
        project.objects.named(Usage::class.java, KOTLIN_WINRT_APPX_RESOURCES_USAGE),
    )
    val components = discoveryConfiguration.incoming.resolutionResult.allComponents
    components.forEach { component ->
        registry.observe(component)
        (component.id as? ProjectComponentIdentifier)
            ?.let { identifier -> project.findProject(identifier.projectPath) }
            ?.let(registry::observeProject)
    }
}

private fun validateAppxResourceVariantResolution(
    configuration: org.gradle.api.artifacts.Configuration,
    failures: Collection<Throwable>,
    resolvedArtifacts: Collection<ResolvedArtifactResult>,
    requestedTarget: String,
    resourceVariantRegistry: AppxResourceVariantRegistry,
) {
    val unresolvedResourceDependencies = configuration.incoming.resolutionResult.allDependencies
        .asSequence()
        .filterIsInstance<UnresolvedDependencyResult>()
        .mapNotNull { dependency ->
            val selector = dependency.requested
            val coordinate = when (selector) {
                is ModuleComponentSelector -> {
                    if (!resourceVariantRegistry.containsModule(selector.group, selector.module)) return@mapNotNull null
                    listOf(selector.group, selector.module, selector.version).joinToString(":")
                }
                is ProjectComponentSelector -> {
                    if (!resourceVariantRegistry.containsProject(selector.buildPath, selector.projectPath)) return@mapNotNull null
                    "project(build=${selector.buildPath}, path=${selector.projectPath})"
                }
                else -> return@mapNotNull null
            }
            coordinate to dependency.failure
        }
        .toList()

    val resourceModuleKeys = unresolvedResourceDependencies
        .flatMapTo(linkedSetOf()) { (coordinate, _) ->
            if (coordinate.startsWith("project(")) {
                listOf(
                    coordinate,
                    coordinate.substringAfter("path=").removeSuffix(")"),
                )
            } else {
                listOf(coordinate.substringBeforeLast(":"))
            }
        }
    val artifactFailures = failures.filter { failure ->
        val message = failure.message.orEmpty()
        resourceModuleKeys.any { key -> message.contains(key) }
    }
    val resolvedResourceOwners = configuration.incoming.resolutionResult.allDependencies
        .asSequence()
        .filterIsInstance<ResolvedDependencyResult>()
        .map { dependency -> dependency.selected.id }
        .filter(resourceVariantRegistry::containsComponent)
        .toSet()
    val selectedVariantErrors = resolvedResourceOwners.flatMap { owner ->
        val ownerArtifacts = resolvedArtifacts.filter { artifact -> artifact.variant.owner == owner }
        if (ownerArtifacts.isEmpty()) {
            listOf("$owner did not produce a resolved AppX resource artifact for target '$requestedTarget'.")
        } else {
            ownerArtifacts.flatMap { artifact ->
                val usage = artifact.variant.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name
                val target = artifact.variant.attributes.getAttribute(KOTLIN_WINRT_APPX_RESOURCE_TARGET_ATTRIBUTE)
                when {
                    !isAppxResourceUsage(usage) -> listOf(
                        "$owner resolved ${artifact.variant.displayName} instead of an AppX resource variant.",
                    )
                    target != null && target != requestedTarget -> listOf(
                        "$owner resolved AppX resource target '$target', requested '$requestedTarget'.",
                    )
                    else -> emptyList()
                }
            }
        }
    }
    if (unresolvedResourceDependencies.isEmpty() && artifactFailures.isEmpty() && selectedVariantErrors.isEmpty()) return

    val details = (unresolvedResourceDependencies.map { (coordinate, failure) ->
        "$coordinate: ${failure.message ?: failure::class.java.simpleName}"
    } + artifactFailures.map { failure -> failure.message ?: failure::class.java.simpleName } + selectedVariantErrors)
        .distinct()
    throw org.gradle.api.GradleException(
        "Failed to resolve Kotlin/WinRT AppX resource variants for ${configuration.name} (target '$requestedTarget'):\n" +
            details.joinToString(separator = "\n") { "- $it" },
    )
}

private fun moduleKey(group: String, module: String): String = "$group:$module"

private fun projectKey(buildPath: String, projectPath: String): String = "$buildPath:$projectPath"

private fun isAppxResourceUsage(usage: String?): Boolean =
    usage == KOTLIN_WINRT_APPX_RESOURCES_USAGE || usage == KOTLIN_WINRT_APPX_PUBLISHED_USAGE

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

private fun addGeneratedSourcesToSelectedKotlinMultiplatformMingwCompilation(
    project: Project,
    generatedSourcesTask: TaskProvider<out Task>,
    selectedVariant: Provider<WinRTApplicationVariant>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    project.afterEvaluate {
        val variant = selectedVariant.get()
        if (variant.kind != WinRTApplicationVariantKind.MingwX64) return@afterEvaluate
        val target = kotlinExtension.targets
            .withType(KotlinNativeTarget::class.java)
            .singleOrNull { it.name.equals(variant.targetName, ignoreCase = true) }
            ?: throw org.gradle.api.GradleException(
                "Selected mingwX64 target '${variant.targetName}' is no longer available.",
            )
        val executable = target.binaries
            .withType(Executable::class.java)
            .singleOrNull { it.name.equals(variant.executableName, ignoreCase = true) }
            ?: throw org.gradle.api.GradleException(
                "Selected mingwX64 executable '${variant.executableName}' is no longer available on target '${target.name}'.",
            )
        val compilation = executable.compilation
        compilation.defaultSourceSet.kotlin.srcDir(generatedSourcesTask)
    }
}

internal fun KotlinNativeTarget.isMingwX64Target(): Boolean =
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
    jvmToolchainVersion: Provider<Int>? = null,
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
        jvmToolchainVersion?.let { version ->
            task.compilerOptions.jvmTarget.set(version.map(::jvmTargetForToolchain))
            task.compilerOptions.freeCompilerArgs.addAll(version.map { value -> listOf("-Xjdk-release=$value") })
        }
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

private fun jvmTargetForToolchain(version: Int): JvmTarget =
    runCatching { JvmTarget.fromTarget(version.toString()) }.getOrElse {
        throw org.gradle.api.GradleException(
            "Kotlin/WinRT application.jvmToolchain($version) is not supported by the installed Kotlin " +
                "Gradle plugin; choose a supported JVM target.",
            it,
        )
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
