package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/** CsWinRT reference assemblies include the complete public surface, including user overlays. */
internal fun configureJvmProjectionReferencePublication(
    project: Project,
    business: KotlinJvmCompile,
    businessReference: Provider<Directory>,
    projectionReference: FileCollection,
    jarTaskNames: List<String>,
) {
    jarTaskNames.forEach { jarName ->
        val apiName = if (jarName == "jar") "apiElements" else jarName.removeSuffix("Jar") + "ApiElements"
        val api = project.configurations.findByName(apiName) ?: return@forEach
        val javaTaskName = business.name.removePrefix("compileKotlin").let { target ->
            if (target.isEmpty()) "compileJava" else "compile${target}MainJava"
        }
        val java = if (javaTaskName in project.tasks.names)
            project.tasks.named(javaTaskName, org.gradle.api.tasks.compile.JavaCompile::class.java) else null
        val assembled = project.tasks.register("${jarName}WinRTReferenceClasses", Sync::class.java) { task ->
            task.into(project.layout.buildDirectory.dir("classes/kotlin-winrt/published-reference/$jarName"))
            task.from(project.files(businessReference).builtBy(business))
            task.from(projectionReference)
            // Retaining Java bodies is conservative; Java has no Kotlin inline contract.
            java?.let { task.from(it.flatMap { compilation -> compilation.destinationDirectory }) }
            task.include("**/*.class", "META-INF/*.kotlin_module")
            task.duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.FAIL
        }
        val implementationJar = project.tasks.named(jarName, Jar::class.java)
        val contract = project.tasks.register("${jarName}WinRTBinaryContract", GenerateWinRTJvmContractTask::class.java) { task ->
            task.referenceClasses.set(project.layout.dir(assembled.map { it.destinationDir }))
            task.kotlinCompilerVersion.set(project.getKotlinPluginVersion())
            task.owner.set(projectionOwnerProvider(project))
            task.producerVersion.set(project.provider { project.version.toString() })
            task.outputFile.set(project.layout.buildDirectory.file("kotlin-winrt/contracts/$jarName.json"))
        }
        val referenceJar = project.tasks.register("${jarName}WinRTReference", Jar::class.java) { task ->
            task.group = "build"
            task.description = "Packages complete Kotlin/WinRT compile-only declarations."
            task.archiveBaseName.set(implementationJar.flatMap { it.archiveBaseName })
            task.archiveVersion.set(implementationJar.flatMap { it.archiveVersion })
            task.archiveClassifier.set("winrt-reference")
            task.from(assembled)
            task.from(contract.flatMap { it.outputFile }) { it.into("META-INF/kotlin-winrt"); it.rename { "binary-contract.json" } }
            task.isPreserveFileTimestamps = false
            task.isReproducibleFileOrder = true
        }
        api.outgoing.artifacts.clear()
        api.outgoing.artifact(referenceJar)
        // A Java/Kotlin project consumer can prefer the secondary classes variant over JARs.
        // Route that path to the same complete reference inventory as published consumption.
        api.outgoing.variants.matching { it.name == "classes" }.configureEach { variant ->
            variant.artifacts.clear()
            variant.artifact(assembled.map { it.destinationDir }) {
                it.type = org.gradle.api.artifacts.type.ArtifactTypeDefinition.JVM_CLASS_DIRECTORY
                it.builtBy(assembled)
            }
        }
    }
}
