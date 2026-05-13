package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtMetadataSource
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.File
import org.gradle.jvm.tasks.Jar

class KotlinWinRtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("winRt", WinRtExtension::class.java)
        configureWinRtGeneration(project, extension)
        configureWinRtLibraryModel(project, extension)
        configureWinRtApplicationModel(project, extension)
    }
}

const val KOTLIN_WINRT_IDENTITY_CONFIGURATION: String = "kotlinWinRtIdentity"
const val KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION: String = "kotlinWinRtIdentityElements"
const val KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION: String = "kotlinWinRtCompilerPlugin"
const val KOTLIN_WINRT_IDENTITY_USAGE: String = "kotlin-winrt-identity"
const val KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY: String = "kotlin-winrt-runtime-assets"
private const val KOTLIN_WINRT_COMPILER_PLUGIN_ID: String = "io.github.composefluent.winrt.compiler"

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
            task.onlyIf { !extension.applicationEnabled.get() }
            task.outputFile.set(project.layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt.json"))
            task.metadataInputs.set(extension.metadataInputs)
            task.includeNamespaces.set(extension.includeNamespaces)
            task.includeTypes.set(extension.includeTypes)
            task.projectionRegistrarFile.set(
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
                    extension.nugetPackages.map { pkg ->
                        "${pkg.packageId}@${pkg.version.get()}"
                    }
                },
            )
            task.runtimeAssets.set(extension.runtimeAssets)
            task.authoredMetadataFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/${project.name}.winmd",
                ),
            )
            task.authoredHostManifestFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/${project.name}.host.json",
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
    project.extensions.extraProperties["kotlinWinRtIdentityElements"] = identityElements.name
    extension.whenApplicationConfigured {
        identityElements.isCanBeConsumed = false
    }
    project.plugins.withId("java") {
        identityTask.configure { task ->
            task.authoredTargetArtifactFiles.from(
                project.tasks.named("jar", Jar::class.java).flatMap { it.archiveFile },
            )
        }
        val generatedAuthoringDirectory = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring")
        project.tasks.matching { it.name == "processResources" }.configureEach(Action<Task> { task ->
            task.dependsOn("generateWinRtProjections")
            if (task is Copy) {
                task.from(
                    project.provider {
                        if (extension.applicationEnabled.get()) {
                            project.files()
                        } else {
                            project.fileTree(generatedAuthoringDirectory) { spec ->
                                spec.include("${project.name}.winmd")
                                spec.include("${project.name}.host.json")
                            }
                        }
                    },
                    Action<CopySpec> { spec ->
                        spec.into(KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY)
                    },
                )
            }
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
    configureWinRtIdentityProjectDependencies(project, identityDependencies)
    project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).configure { task ->
        task.dependencyIdentityFiles.from(identityDependencies)
    }
    val applicationIdentityTask = project.tasks.register(
        "generateWinRtApplicationIdentity",
        GenerateWinRtApplicationIdentityTask::class.java,
        Action<GenerateWinRtApplicationIdentityTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Aggregates Kotlin WinRT identity metadata from application dependencies."
            task.onlyIf { extension.applicationEnabled.get() }
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
                    extension.nugetPackages.map { pkg ->
                        "${pkg.packageId}@${pkg.version.get()}"
                    }
                },
            )
            task.runtimeAssets.set(extension.runtimeAssets)
            task.dependencyIdentityFiles.from(identityDependencies)
        },
    )
    val runtimeAssetsDirectory = project.layout.buildDirectory.dir("kotlin-winrt/runtime-assets")
    val buildAuthoringHostTask = project.tasks.register(
        "buildWinRtAuthoringHost",
        BuildWinRtAuthoringHostTask::class.java,
        Action<BuildWinRtAuthoringHostTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Builds cswinrt-style native JVM host DLLs for authored WinRT activation."
            task.onlyIf { extension.applicationEnabled.get() }
            task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/authoring-host/bin"))
            task.generatedSourceDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/authoring-host/src"))
            task.runtimeIdentifier.set(project.provider { currentWindowsRuntimeIdentifier() })
            task.javaHome.set(project.provider { System.getProperty("java.home") })
            task.dependencyIdentityFiles.from(identityDependencies)
            task.authoredHostManifestFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/${project.name}.host.json",
                ),
            )
            task.dependsOn("generateWinRtProjections")
        },
    )
    val stageRuntimeAssetsTask = project.tasks.register(
        "stageWinRtRuntimeAssets",
        StageWinRtRuntimeAssetsTask::class.java,
        Action<StageWinRtRuntimeAssetsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Stages WinRT NuGet runtime and resource assets for application execution."
            task.onlyIf { extension.applicationEnabled.get() }
            task.outputDirectory.set(runtimeAssetsDirectory)
            task.nugetPackages.set(
                project.provider {
                    extension.nugetPackages.map { pkg ->
                        "${pkg.packageId}@${pkg.version.get()}"
                    }
                },
            )
            task.runtimeAssets.set(extension.runtimeAssets)
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
            task.dependencyIdentityFiles.from(identityDependencies)
            task.authoredMetadataFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/${project.name}.winmd",
                ),
            )
            task.authoredHostManifestFiles.from(
                project.layout.buildDirectory.file(
                    "generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/${project.name}.host.json",
                ),
            )
            task.authoredTargetArtifactFiles.from(
                identityDependencies.elements.map { elements ->
                    elements.map { it.asFile }.flatMap(::readAuthoredTargetArtifacts)
                },
            )
            task.authoredHostDllFiles.from(project.fileTree(buildAuthoringHostTask.flatMap { it.outputDirectory }) { spec ->
                spec.include("*.dll")
            })
            task.dependsOn("generateWinRtProjections")
            task.dependsOn(buildAuthoringHostTask)
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

private fun configureWinRtGeneration(
    project: Project,
    extension: BaseWinRtExtension,
) {
    val generatedSources = project.layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")
    val compilerPluginClasspath = kotlinWinRtCompilerPluginClasspath(project)
    val generateTask = project.tasks.register(
        "generateWinRtProjections",
        GenerateWinRtProjectionsTask::class.java,
        Action<GenerateWinRtProjectionsTask> { task ->
            task.group = "kotlin-winrt"
            task.description = "Generates Kotlin WinRT projections from Windows SDK and NuGet WinMD metadata."
            task.outputDirectory.set(generatedSources)
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
            task.projectModel.set(
                project.provider {
                    if ((extension as? WinRtExtension)?.applicationEnabled?.get() == true) "application" else "library"
                },
            )
            task.authoringAssemblyName.set(project.name)
            task.authoringTargetArtifactName.set(
                project.provider {
                    (project.tasks.findByName("jar") as? Jar)?.archiveFileName?.get() ?: "${project.name}.jar"
                },
            )
            task.authoringScannerJvmArgs.convention(
                listOf(
                    "-Xmx128m",
                    "-Xss512k",
                    "-XX:+UseSerialGC",
                    "-XX:ReservedCodeCacheSize=32m",
                ),
            )
            task.authoringScannerClasspath.from(compilerPluginClasspath)
            task.sourceRoots.from(
                project.provider {
                    val generatedSourcesPath = generatedSources.get().asFile.toPath().toAbsolutePath().normalize()
                    kotlinMainSourceDirs(project).filterNot { sourceDir ->
                        sourceDir.toPath().toAbsolutePath().normalize().startsWith(generatedSourcesPath)
                    }
                },
            )
        },
    )

    project.plugins.withId("org.jetbrains.kotlin.jvm") {
        project.configurations.findByName("kotlinCompilerPluginClasspath")?.let { compilerPluginConfiguration ->
            project.dependencies.add(compilerPluginConfiguration.name, kotlinWinRtCompilerPluginDependency(project))
        }
        addGeneratedSourcesToKotlinMain(project, generatedSources)
        configureKotlinWinRtCompilerPluginOptions(
            project = project,
            metadataIndex = generatedSources.map { directory ->
                directory.file("kotlin-winrt-authoring/metadata-index.tsv")
            },
            compilerSupportManifest = generatedSources.map { directory ->
                directory.file("kotlin-winrt-support/compiler-support.tsv")
            },
            compilerSupportClassOutputDirectory = project.layout.buildDirectory.dir("classes/kotlin/main"),
            typeIndexOutput = project.layout.buildDirectory.file("classes/kotlin/main/kotlin-winrt/type-index.tsv"),
        )
        project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
            task.dependsOn(generateTask)
        })
    }

    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        project.configurations.findByName("kotlinCompilerPluginClasspath")?.let { compilerPluginConfiguration ->
            project.dependencies.add(compilerPluginConfiguration.name, kotlinWinRtCompilerPluginDependency(project))
        }
        addGeneratedSourcesToKotlinMultiplatformCommonMain(project, generatedSources)
        configureKotlinWinRtCompilerPluginOptions(
            project = project,
            metadataIndex = generatedSources.map { directory ->
                directory.file("kotlin-winrt-authoring/metadata-index.tsv")
            },
            compilerSupportManifest = generatedSources.map { directory ->
                directory.file("kotlin-winrt-support/compiler-support.tsv")
            },
            compilerSupportClassOutputDirectory = project.layout.buildDirectory.dir("classes/kotlin/main"),
            typeIndexOutput = project.layout.buildDirectory.file("classes/kotlin/main/kotlin-winrt/type-index.tsv"),
        )
        project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
            task.dependsOn(generateTask)
        })
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

private fun kotlinWinRtCompilerPluginClasspath(project: Project) =
    project.configurations.findByName(KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION)
        ?: project.configurations.create(KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION).also { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            project.dependencies.add(configuration.name, kotlinWinRtCompilerPluginDependency(project))
        }

private fun kotlinWinRtCompilerPluginDependency(project: Project): Any {
    val localCompilerPlugin = project.rootProject.findProject(":winrt-compiler-plugin")
    return if (localCompilerPlugin != null) {
        project.dependencies.project(mapOf("path" to localCompilerPlugin.path))
    } else {
        kotlinWinRtCompilerPluginClasspathJar(project)
            ?: "io.github.composefluent.winrt:winrt-compiler-plugin:${kotlinWinRtPluginVersion()}"
    }
}

private fun kotlinWinRtCompilerPluginClasspathJar(project: Project): Any? {
    val compilerPluginClass = runCatching {
        Class.forName("io.github.composefluent.winrt.compiler.KotlinWinRtCommandLineProcessor")
    }.getOrNull() ?: return null
    val location = compilerPluginClass.protectionDomain?.codeSource?.location ?: return null
    return runCatching {
        project.files(File(location.toURI()))
    }.getOrNull()
}

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

private fun configureWinRtIdentityProjectDependencies(
    project: Project,
    identityDependencies: org.gradle.api.artifacts.Configuration,
) {
    val registeredProjectPaths = linkedSetOf<String>()
    fun registerDependency(dependency: ProjectDependency) {
        val dependencyProject = project.findProject(dependency.path)
        if (dependencyProject?.hasKotlinWinRtIdentityMetadata() == true &&
            registeredProjectPaths.add(dependency.path)
        ) {
            identityDependencies.dependencies.add(dependency.copy())
        }
    }
    fun observeConfiguration(configurationName: String) {
        project.configurations.matching { it.name == configurationName }.configureEach { configuration ->
            configuration.allDependencies.configureEach { dependency ->
                if (dependency is ProjectDependency) {
                    registerDependency(dependency)
                }
            }
        }
    }
    fun scanConfiguration(configurationName: String) {
        project.configurations.findByName(configurationName)?.allDependencies?.forEach { dependency ->
            if (dependency is ProjectDependency) {
                registerDependency(dependency)
            }
        }
    }
    fun kotlinSourceSetConfigurationNames(sourceSet: KotlinSourceSet): List<String> =
        buildList {
            add(sourceSet.apiConfigurationName)
            add(sourceSet.implementationConfigurationName)
            (sourceSet.callNoArg("getImplementationMetadataConfigurationName") as? String)?.let(::add)
        }

    observeConfiguration("api")
    observeConfiguration("implementation")
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return@withId
        kotlinExtension.sourceSets.configureEach { sourceSet: KotlinSourceSet ->
            kotlinSourceSetConfigurationNames(sourceSet).forEach(::observeConfiguration)
        }
    }
    project.gradle.projectsEvaluated {
        scanConfiguration("api")
        scanConfiguration("implementation")
        project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.sourceSets?.forEach { sourceSet ->
            kotlinSourceSetConfigurationNames(sourceSet).forEach(::scanConfiguration)
        }
    }
}

private fun addGeneratedSourcesToKotlinMultiplatformCommonMain(
    project: Project,
    generatedSources: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlinExtension.sourceSets.named("commonMain").configure { sourceSet ->
        sourceSet.kotlin.srcDir(generatedSources)
    }
}

private fun configureKotlinWinRtCompilerPluginOptions(
    project: Project,
    metadataIndex: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
    compilerSupportManifest: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
    compilerSupportClassOutputDirectory: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    typeIndexOutput: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
) {
    project.tasks.withType(KotlinJvmCompile::class.java).configureEach(Action<KotlinJvmCompile> { task ->
        val freeCompilerArgs = task.compilerOptions.freeCompilerArgs
        val metadataIndexPath = metadataIndex.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:metadataIndex=$metadataIndexPath",
        )
        val typeIndexOutputPath = typeIndexOutput.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:typeIndexOutput=$typeIndexOutputPath",
        )
        val compilerSupportManifestPath = compilerSupportManifest.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:compilerSupportManifest=$compilerSupportManifestPath",
        )
        val compilerSupportClassOutputDirectoryPath = compilerSupportClassOutputDirectory.get().asFile.absolutePath
        freeCompilerArgs.add("-P")
        freeCompilerArgs.add(
            "plugin:$KOTLIN_WINRT_COMPILER_PLUGIN_ID:compilerSupportClassOutputDirectory=$compilerSupportClassOutputDirectoryPath",
        )
    })
}

private fun kotlinMainSourceDirs(project: Project): List<File> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.let { kotlinExtension ->
        return kotlinExtension.sourceSets.flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
    }
    val kotlinExtension = project.extensions.findByName("kotlin") ?: return emptyList()
    val sourceSets = kotlinExtension.callNoArg("getSourceSets") as? org.gradle.api.NamedDomainObjectContainer<*> ?: return emptyList()
    val mainSourceSet = sourceSets.findByName("main") ?: return emptyList()
    val kotlinSourceDirectorySet = mainSourceSet.callNoArg("getKotlin") ?: return emptyList()
    return (kotlinSourceDirectorySet.callNoArg("getSrcDirs") as? Set<File>).orEmpty().toList()
}

private fun Any.callNoArg(name: String): Any? =
    javaClass.methods.firstOrNull { method ->
        method.name == name && method.parameterCount == 0
    }?.invoke(this)

private fun Any.callOneArg(name: String, argument: Any): Any? =
    javaClass.methods
        .filter { method -> method.name == name && method.parameterCount == 1 }
        .sortedBy { method -> if (method.parameterTypes[0].isInstance(argument)) 0 else 1 }
        .firstNotNullOfOrNull { method ->
            runCatching { method.invoke(this, argument) }.getOrNull()
        }

private fun Project.hasKotlinWinRtIdentityMetadata(): Boolean =
    configurations.findByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)?.isCanBeConsumed == true
