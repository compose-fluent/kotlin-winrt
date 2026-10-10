package io.github.composefluent.winrt.build

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.w3c.dom.Element

class WinRTPrebuiltProjectionConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("build-convention")
        project.pluginManager.apply("winrt.publish")
        project.pluginManager.withPlugin(PUBLIC_WINDOWS_TOOLKIT_PLUGIN_ID) {
            project.pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
                configurePrebuiltProjection(project)
            }
        }
    }

    private fun configurePrebuiltProjection(project: Project) {
        val identityConfiguration = project.configurations.named(IDENTITY_CONFIGURATION_NAME)
        val compileOnlyConfiguration = project.configurations.named(COMMON_MAIN_COMPILE_ONLY_CONFIGURATION)
        val apiConfiguration = project.configurations.named(COMMON_MAIN_API_CONFIGURATION)
        val publishedArtifactName = project.extensions.getByType(BasePluginExtension::class.java).archivesName
        val compiledJvmProjectionClasses = project.layout.buildDirectory.dir(
            "classes/kotlin-winrt/projection/compileKotlinJvm",
        )
        val compileJvmProjectionTaskName = "compileKotlinWinRTProjectionJvm"

        project.tasks.withType(KotlinJvmCompile::class.java).configureEach(
            Action<KotlinJvmCompile> {
                compilerOptions.freeCompilerArgs.add(NO_SOURCE_DEBUG_EXTENSION_ARGUMENT)
            },
        )

        val verifyJvmCallSiteLowering = project.tasks.register(
            JVM_CALL_SITE_VERIFICATION_TASK_NAME,
            VerifyBinaryMarkerAbsentTask::class.java,
            Action<VerifyBinaryMarkerAbsentTask> {
                group = "verification"
                description = "Verifies that no generated WinRT call-site placeholder reaches JVM bytecode."
                dependsOn(compileJvmProjectionTaskName)
                binaryArtifacts.from(compiledJvmProjectionClasses)
                markers.set(CALL_SITE_PLACEHOLDERS)
                artifactDescription.set("compiled JVM projection classes")
            },
        )
        val nativeCompilationTasks = project.tasks.withType(KotlinNativeCompile::class.java)
            .matching { task ->
                task.name == "compileKotlinMingwX64" || task.name == "compileWinRTProjectionKotlinMingwX64"
            }
        val compiledNativeKlibs = project.objects.fileCollection()
        // Projection declarations live in their own compilation. The main
        // compilation may have no sources, so use the producers' actual outputs.
        nativeCompilationTasks.configureEach(Action<KotlinNativeCompile> {
            compiledNativeKlibs.from(outputFile)
        })
        val verifyMingwX64CallSiteLowering = project.tasks.register(
            MINGW_CALL_SITE_VERIFICATION_TASK_NAME,
            VerifyBinaryMarkerAbsentTask::class.java,
            Action<VerifyBinaryMarkerAbsentTask> {
                group = "verification"
                description = "Verifies that no generated WinRT call-site placeholder reaches the mingwX64 klib."
                dependsOn(nativeCompilationTasks)
                binaryArtifacts.from(compiledNativeKlibs)
                markers.set(CALL_SITE_PLACEHOLDERS)
                artifactDescription.set("compiled mingwX64 projection klib")
            },
        )
        configureNativeProjectionPublication(project, verifyMingwX64CallSiteLowering)
        val verifyJvmDirectCallSiteLowering = project.tasks.register(
            JVM_DIRECT_CALL_SITE_VERIFICATION_TASK_NAME,
            VerifyBinaryMarkerAbsentTask::class.java,
            Action<VerifyBinaryMarkerAbsentTask> {
                group = "verification"
                description = "Verifies that generated JVM call-site owners contain only direct fixed-shape lowering."
                dependsOn(compileJvmProjectionTaskName)
                binaryArtifacts.from(
                    compiledJvmProjectionClasses.map { classesDirectory ->
                        classesDirectory.asFileTree.matching {
                            include("io/github/composefluent/winrt/projections/support/WinRTModulePlatformAbiCall_*.class")
                        }
                    },
                )
                markers.set(DIRECT_CALL_SITE_FORBIDDEN_MARKERS)
                methodNamePrefixes.set(setOf("callSite_", "abiCall_"))
                artifactDescription.set("compiled JVM module call-site owners")
            },
        )
        project.tasks.matching { task -> task.name == "jvmJar" }.configureEach(
            Action<Task> { dependsOn(verifyJvmCallSiteLowering, verifyJvmDirectCallSiteLowering) },
        )
        project.tasks.matching { task ->
            task.name == "mingwX64MainKlibrary" || task.name == "mingwX64Klib"
        }.configureEach(
            Action<Task> { dependsOn(verifyMingwX64CallSiteLowering) },
        )

        compileOnlyConfiguration.configure(Action<Configuration> {
            dependencies.withType(ProjectDependency::class.java).all(Action<ProjectDependency> {
                val dependency = this
                identityConfiguration.configure(Action<Configuration> {
                    if (state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED &&
                        dependencies.withType(ProjectDependency::class.java).none { it.path == dependency.path }
                    ) {
                        dependencies.add(dependency.copy())
                    }
                })
            })
        })

        val apiArtifactNames = linkedMapOf<String, Provider<String>>()
        val compileOnlyArtifactNames = linkedMapOf<String, Provider<String>>()

        val audit = project.tasks.register(
            OUTPUT_AUDIT_TASK_NAME,
            ValidatePrebuiltProjectionOutputTask::class.java,
            Action<ValidatePrebuiltProjectionOutputTask> {
            group = "verification"
            description = "Audits generated prebuilt projection output and direct projection-reference class ownership."
            dependsOn("generateWinRTProjections", compileJvmProjectionTaskName)
            dependsOn(verifyJvmCallSiteLowering, verifyJvmDirectCallSiteLowering, verifyMingwX64CallSiteLowering)
            generatedSourcesDirectory.set(
                project.layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin"),
            )
            compiledClassesDirectories.from(compiledJvmProjectionClasses)
            maxTotalClassBytes.set(PREBUILT_MAX_TOTAL_CLASS_BYTES)
            crossArtifactClassOwners.add(project.name)
            crossArtifactClassDirectories.from(compiledJvmProjectionClasses)
        })
        project.tasks.named("check").configure(Action<Task> { dependsOn(audit) })

        val publicationValidation = project.tasks.register(
            PUBLICATION_VALIDATION_TASK_NAME,
            ValidatePrebuiltProjectionPublicationTask::class.java,
            Action<ValidatePrebuiltProjectionPublicationTask> {
            group = "verification"
            description = "Validates prebuilt projection POM and Gradle metadata API dependencies."
            dependsOn(
                "generatePomFileForJvmPublication",
                "generatePomFileForMingwX64Publication",
                "generatePomFileForKotlinMultiplatformPublication",
                "generateMetadataFileForKotlinMultiplatformPublication",
            )
            requiredApiDependencies.set(
                publishedArtifactName.map { artifactName -> mapOf(artifactName to "") },
            )
            forbiddenPublishedDependencies.set(
                publishedArtifactName.map { artifactName -> mapOf(artifactName to "") },
            )
            pomFiles.from(
                project.layout.buildDirectory.file("publications/jvm/pom-default.xml"),
                project.layout.buildDirectory.file("publications/mingwX64/pom-default.xml"),
                project.layout.buildDirectory.file("publications/kotlinMultiplatform/pom-default.xml"),
            )
            moduleMetadataFiles.from(
                project.layout.buildDirectory.file("publications/kotlinMultiplatform/module.json"),
            )
        })

        val configuredReferencePaths = mutableSetOf<String>()
        fun dependencyArtifactName(dependency: ProjectDependency): Provider<String> {
            val artifactName = project.objects.property(String::class.java)
            artifactName.convention(dependency.name)
            val reference = project.findProject(dependency.path)
            reference?.pluginManager?.withPlugin("base") {
                artifactName.set(
                    reference.extensions
                        .getByType(BasePluginExtension::class.java)
                        .archivesName,
                )
            }
            return artifactName
        }

        fun configureProjectionReference(dependency: ProjectDependency) {
            val reference = project.findProject(dependency.path) ?: return
            if (!configuredReferencePaths.add(reference.path)) return
            audit.configure(Action<ValidatePrebuiltProjectionOutputTask> {
                dependsOn("${reference.path}:$compileJvmProjectionTaskName")
                crossArtifactClassOwners.add(reference.name)
                crossArtifactClassDirectories.from(
                    reference.layout.buildDirectory.dir("classes/kotlin-winrt/projection/compileKotlinJvm"),
                )
            })
        }
        compileOnlyConfiguration.configure(Action<Configuration> {
            dependencies.withType(ProjectDependency::class.java).all(Action<ProjectDependency> {
                val dependency = this
                compileOnlyArtifactNames[dependency.path] = dependencyArtifactName(dependency)
                val artifactNames = compileOnlyArtifactNames.values.toList()
                val forbiddenDependencies = project.providers.provider {
                    artifactNames.map(Provider<String>::get).distinct().sorted().joinToString(",")
                }
                publicationValidation.configure(Action<ValidatePrebuiltProjectionPublicationTask> {
                    forbiddenPublishedDependencies.set(
                        publishedArtifactName.zip(forbiddenDependencies) { artifactName, dependencies ->
                            mapOf(artifactName to dependencies)
                        },
                    )
                })
                configureProjectionReference(dependency)
            })
        })
        apiConfiguration.configure(Action<Configuration> {
            dependencies.withType(ProjectDependency::class.java).all(Action<ProjectDependency> {
                val dependency = this
                val dependencyArtifactName = dependencyArtifactName(dependency)
                apiArtifactNames[dependency.path] = dependencyArtifactName
                val artifactNames = apiArtifactNames.values.toList()
                val requiredDependencies = project.providers.provider {
                    artifactNames.map(Provider<String>::get).distinct().sorted().joinToString(",")
                }
                publicationValidation.configure(Action<ValidatePrebuiltProjectionPublicationTask> {
                    requiredApiDependencies.set(
                        publishedArtifactName.zip(requiredDependencies) { artifactName, dependencies ->
                            mapOf(artifactName to dependencies)
                        },
                    )
                })
                project.extensions.configure(PublishingExtension::class.java, Action<PublishingExtension> {
                    publications.withType(MavenPublication::class.java)
                        .matching { publication -> publication.name == "kotlinMultiplatform" }
                        .configureEach(Action<MavenPublication> {
                            pom.withXml(Action<XmlProvider> {
                                val apiArtifactName = dependencyArtifactName.get()
                                val dependencies = asElement().getElementsByTagName("dependency")
                                (0 until dependencies.length)
                                    .map { dependencies.item(it) as Element }
                                    .filter { pomDependency ->
                                        pomDependency.getElementsByTagName("artifactId").item(0)?.textContent ==
                                            apiArtifactName
                                    }
                                    .forEach { pomDependency ->
                                        pomDependency.getElementsByTagName("scope").item(0)?.textContent = "compile"
                                    }
                            })
                        })
                })
                configureProjectionReference(dependency)
            })
        })
    }

    private fun configureNativeProjectionPublication(
        project: Project,
        verification: TaskProvider<VerifyBinaryMarkerAbsentTask>,
    ) {
        // The toolkit registers this artifact after excluding generated sources
        // from main. Promote it immediately, before KGP copies the variant for
        // publication or another project observes either artifact set.
        project.configurations.matching { it.name == "mingwX64ApiElements" }
            .configureEach(Action<Configuration> {
                val apiElements = this
                var promotedProjection = false
                outgoing.artifacts.all(Action<PublishArtifact> artifactAdded@{
                    if (promotedProjection) return@artifactAdded
                    val projection = project.tasks.findByName("compileWinRTProjectionKotlinMingwX64")
                        as? KotlinNativeCompile ?: return@artifactAdded
                    // Gradle notifies this set before applying the artifact's
                    // classifier action, so identify the producer by its file.
                    if (file != projection.outputFile.get()) return@artifactAdded
                    val main = project.tasks.findByName("compileKotlinMingwX64")
                        as? KotlinNativeCompile ?: return@artifactAdded
                    // SDK projections generate interop overlays such as
                    // WindowNative and Win32Interop in main. Their files are
                    // absent during clean configuration, but their main KLIBs
                    // remain part of the published API.
                    if (project.name in NATIVE_OVERLAY_PROJECTION_MODULES) return@artifactAdded
                    if (!main.sources.isEmpty) return@artifactAdded

                    promotedProjection = true
                    apiElements.outgoing.artifacts.remove(this)
                    apiElements.outgoing.artifacts.removeAll { it.extension == "klib" }
                    apiElements.outgoing.artifact(projection.outputFile, Action<ConfigurablePublishArtifact> {
                        extension = "klib"
                        type = "klib"
                        builtBy(projection, verification)
                    })
                })
            })
    }

    private companion object {
        const val PUBLIC_WINDOWS_TOOLKIT_PLUGIN_ID = "io.github.compose-fluent.windows-toolkit"
        const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
        const val IDENTITY_CONFIGURATION_NAME = "kotlinWinRTLibraryDependencyIdentity"
        const val COMMON_MAIN_COMPILE_ONLY_CONFIGURATION = "commonMainCompileOnly"
        const val COMMON_MAIN_API_CONFIGURATION = "commonMainApi"
        const val OUTPUT_AUDIT_TASK_NAME = "auditGeneratedWinRTProjectionOutput"
        const val PUBLICATION_VALIDATION_TASK_NAME = "validatePrebuiltProjectionPublication"
        val NATIVE_OVERLAY_PROJECTION_MODULES = setOf("windows-sdk", "windows-app-sdk")
        const val PREBUILT_MAX_TOTAL_CLASS_BYTES = 150_000_000L
        const val JVM_CALL_SITE_VERIFICATION_TASK_NAME = "verifyJvmProjectionCallSiteLowering"
        const val JVM_DIRECT_CALL_SITE_VERIFICATION_TASK_NAME = "verifyJvmProjectionCallSiteDirectLowering"
        const val MINGW_CALL_SITE_VERIFICATION_TASK_NAME = "verifyMingwX64ProjectionCallSiteLowering"
        const val MODULE_CALL_SITE_PLACEHOLDER = "Lowered while compiling the generated WinRT module"
        val CALL_SITE_PLACEHOLDERS = setOf(
            MODULE_CALL_SITE_PLACEHOLDER,
            "Fixed WinRT ABI call",
            "winRTProjectionCallSiteArguments",
        )
        const val NO_SOURCE_DEBUG_EXTENSION_ARGUMENT = "-Xno-source-debug-extension"
        val DIRECT_CALL_SITE_FORBIDDEN_MARKERS = setOf(
            "confinedScope",
            "allocateBytes",
            "getLayout",
            "WinRTProjectionIntrinsic",
            "kotlin/TODO",
        )
    }
}
