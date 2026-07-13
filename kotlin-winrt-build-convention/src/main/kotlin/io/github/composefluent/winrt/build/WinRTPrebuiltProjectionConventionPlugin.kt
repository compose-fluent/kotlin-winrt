package io.github.composefluent.winrt.build

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
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

        fun directProjectionReferences(): List<Project> =
            listOf(compileOnlyConfiguration.get(), apiConfiguration.get())
                .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java).toList() }
                .mapNotNull { project.findProject(it.path) }
                .distinctBy(Project::getPath)

        val audit = project.tasks.register(
            OUTPUT_AUDIT_TASK_NAME,
            ValidatePrebuiltProjectionOutputTask::class.java,
            Action<ValidatePrebuiltProjectionOutputTask> {
            group = "verification"
            description = "Audits generated prebuilt projection output and direct projection-reference class ownership."
            dependsOn("generateWinRTProjections", "compileKotlinJvm")
            dependsOn(project.provider { directProjectionReferences().map { "${it.path}:compileKotlinJvm" } })
            generatedSourcesDirectory.set(
                project.layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin"),
            )
            compiledClassesDirectories.from(project.layout.buildDirectory.dir("classes/kotlin/jvm/main"))
            crossArtifactClassOwners.set(project.provider {
                listOf(project.name) + directProjectionReferences().map(Project::getName)
            })
            crossArtifactClassDirectories.from(project.layout.buildDirectory.dir("classes/kotlin/jvm/main"))
            crossArtifactClassDirectories.from(project.provider {
                directProjectionReferences().map { reference ->
                    reference.layout.buildDirectory.dir("classes/kotlin/jvm/main")
                }
            })
        })
        project.tasks.named("check").configure(Action<Task> { dependsOn(audit) })

        project.tasks.register(
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
            requiredApiDependencies.set(project.provider {
                mapOf(
                    project.name to apiConfiguration.get().dependencies
                        .withType(ProjectDependency::class.java)
                        .joinToString(",") { dependency ->
                            project.findProject(dependency.path)?.name ?: dependency.name
                        },
                )
            })
            pomFiles.from(
                project.layout.buildDirectory.file("publications/jvm/pom-default.xml"),
                project.layout.buildDirectory.file("publications/mingwX64/pom-default.xml"),
                project.layout.buildDirectory.file("publications/kotlinMultiplatform/pom-default.xml"),
            )
            moduleMetadataFiles.from(
                project.layout.buildDirectory.file("publications/kotlinMultiplatform/module.json"),
            )
        })

        project.extensions.configure(PublishingExtension::class.java, Action<PublishingExtension> {
            publications.withType(MavenPublication::class.java)
                .matching { publication -> publication.name == "kotlinMultiplatform" }
                .configureEach(Action<MavenPublication> {
                    pom.withXml(Action<XmlProvider> {
                        val apiArtifactNames = apiConfiguration.get().dependencies
                            .withType(ProjectDependency::class.java)
                            .map { dependency -> project.findProject(dependency.path)?.name ?: dependency.name }
                            .toSet()
                        val dependencies = asElement().getElementsByTagName("dependency")
                        (0 until dependencies.length)
                            .map { dependencies.item(it) as Element }
                            .filter { dependency ->
                                dependency.getElementsByTagName("artifactId").item(0)?.textContent in apiArtifactNames
                            }
                            .forEach { dependency ->
                                dependency.getElementsByTagName("scope").item(0)?.textContent = "compile"
                            }
                    })
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
    }
}
