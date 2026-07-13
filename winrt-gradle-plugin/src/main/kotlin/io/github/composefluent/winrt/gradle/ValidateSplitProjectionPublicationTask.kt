package io.github.composefluent.winrt.gradle

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

@DisableCachingByDefault(because = "Publication validation has no outputs.")
abstract class ValidateSplitProjectionPublicationTask : DefaultTask() {
    @get:Input
    abstract val requiredApiDependencies: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pomFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleMetadataFiles: ConfigurableFileCollection

    @TaskAction
    fun validate() {
        pomFiles.files.sortedBy(File::getPath).forEach(::validatePom)
        moduleMetadataFiles.files.sortedBy(File::getPath).forEach(::validateModuleMetadata)
    }

    private fun validatePom(pom: File) {
        check(pom.isFile) { "Missing split projection POM: ${pom.absolutePath}" }
        val publicationName = pom.parentFile.name
        val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = documentBuilderFactory.newDocumentBuilder().parse(pom)
        val publishedArtifactId = document.documentElement.childText("artifactId")
            ?: error("${pom.absolutePath} does not declare its artifactId.")
        val publishedModule = publishedArtifactId.toRootModuleName(publicationName)
        val expectedModules = requiredDependenciesFor(publishedModule, pom)
        val dependencies = document.getElementsByTagName("dependency")
            .let { nodes -> (0 until nodes.length).map { index -> nodes.item(index) as Element } }
        expectedModules.forEach { expectedModule ->
            val expectedArtifactId = expectedModule.toPublicationArtifactId(publicationName)
            val dependency = dependencies.firstOrNull { element -> element.childText("artifactId") == expectedArtifactId }
                ?: error("${pom.absolutePath} does not expose $expectedArtifactId.")
            check(dependency.childText("scope") == "compile") {
                "${pom.absolutePath} publishes $expectedArtifactId outside the compile/API surface."
            }
            check(dependency.childText("optional") != "true") {
                "${pom.absolutePath} publishes $expectedArtifactId as optional."
            }
        }
    }

    private fun validateModuleMetadata(moduleMetadata: File) {
        check(moduleMetadata.isFile) {
            "Missing split projection Gradle module metadata: ${moduleMetadata.absolutePath}"
        }
        val parsed = JsonSlurper().parse(moduleMetadata)
        val parsedRoot = parsed as? Map<*, *>
            ?: error("${moduleMetadata.absolutePath} is not a Gradle module metadata object.")
        val publishedModule = (parsedRoot["component"] as? Map<*, *>)?.get("module") as? String
            ?: error("${moduleMetadata.absolutePath} does not declare component.module.")
        val expectedModules = requiredDependenciesFor(publishedModule, moduleMetadata)
        val metadataApiVariant = parsedRoot
            .get("variants")
            ?.let { variants -> variants as? Iterable<*> }
            ?.filterIsInstance<Map<*, *>>()
            ?.firstOrNull { variant ->
                variant["name"] == METADATA_API_VARIANT_NAME &&
                    variant.usage() in API_USAGES
            }
            ?: error(
                "${moduleMetadata.absolutePath} does not publish a $METADATA_API_VARIANT_NAME/API usage variant.",
            )
        expectedModules.forEach { expectedModule ->
            check(metadataApiVariant.dependencies().any { dependency -> dependency["module"] == expectedModule }) {
                "${moduleMetadata.absolutePath} does not expose $expectedModule from $METADATA_API_VARIANT_NAME."
            }
        }
    }

    private fun requiredDependenciesFor(publishedModule: String, metadataFile: File): List<String> =
        requiredApiDependencies.get()[publishedModule]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: error("No API dependency contract configured for $publishedModule: ${metadataFile.absolutePath}")

    private fun String.toRootModuleName(publicationName: String): String =
        when (publicationName) {
            "jvm" -> removeSuffix("-jvm")
            "mingwX64" -> removeSuffix("-mingwx64")
            "kotlinMultiplatform" -> this
            else -> error("Unsupported split projection publication '$publicationName'.")
        }

    private fun String.toPublicationArtifactId(publicationName: String): String =
        when (publicationName) {
            "jvm" -> "$this-jvm"
            "mingwX64" -> "$this-mingwx64"
            "kotlinMultiplatform" -> this
            else -> error("Unsupported split projection publication '$publicationName'.")
        }

    private fun Map<*, *>.usage(): String? =
        (get("attributes") as? Map<*, *>)?.get("org.gradle.usage") as? String

    private fun Map<*, *>.dependencies(): List<Map<*, *>> =
        (get("dependencies") as? Iterable<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()

    private fun Element.childText(name: String): String? =
        getElementsByTagName(name).item(0)?.textContent?.trim()

    private companion object {
        const val METADATA_API_VARIANT_NAME = "metadataApiElements"
        val API_USAGES = setOf("kotlin-metadata", "kotlin-api", "java-api")
    }
}
