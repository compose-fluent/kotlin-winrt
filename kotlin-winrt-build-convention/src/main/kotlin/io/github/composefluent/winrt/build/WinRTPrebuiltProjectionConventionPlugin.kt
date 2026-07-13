package io.github.composefluent.winrt.build

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.provider.Provider
import org.w3c.dom.Element

class WinRTPrebuiltProjectionConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("build-convention")
        project.pluginManager.apply("winrt.publish")
        project.pluginManager.withPlugin(PUBLIC_WINRT_PLUGIN_ID) {
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
            dependsOn("generateWinRTProjections", "compileKotlinJvm")
            generatedSourcesDirectory.set(
                project.layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin"),
            )
            compiledClassesDirectories.from(project.layout.buildDirectory.dir("classes/kotlin/jvm/main"))
            maxTotalClassBytes.set(PREBUILT_MAX_TOTAL_CLASS_BYTES)
            crossArtifactClassOwners.add(project.name)
            crossArtifactClassDirectories.from(project.layout.buildDirectory.dir("classes/kotlin/jvm/main"))
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
                dependsOn("${reference.path}:compileKotlinJvm")
                crossArtifactClassOwners.add(reference.name)
                crossArtifactClassDirectories.from(
                    reference.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
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

    private companion object {
        const val PUBLIC_WINRT_PLUGIN_ID = "io.github.compose-fluent.winrt"
        const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
        const val IDENTITY_CONFIGURATION_NAME = "kotlinWinRTLibraryDependencyIdentity"
        const val COMMON_MAIN_COMPILE_ONLY_CONFIGURATION = "commonMainCompileOnly"
        const val COMMON_MAIN_API_CONFIGURATION = "commonMainApi"
        const val OUTPUT_AUDIT_TASK_NAME = "auditGeneratedWinRTProjectionOutput"
        const val PUBLICATION_VALIDATION_TASK_NAME = "validatePrebuiltProjectionPublication"
        const val PREBUILT_MAX_TOTAL_CLASS_BYTES = 150_000_000L
    }
}
