package io.github.composefluent.winrt.build

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
abstract class ValidatePrebuiltProjectionPublicationTask : DefaultTask() {
    @get:Input
    abstract val requiredApiDependencies: MapProperty<String, String>

    @get:Input
    abstract val forbiddenPublishedDependencies: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pomFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleMetadataFiles: ConfigurableFileCollection

    init {
        forbiddenPublishedDependencies.convention(emptyMap())
    }

    @TaskAction
    fun validate() {
        pomFiles.files.sortedBy(File::getPath).forEach(::validatePom)
        moduleMetadataFiles.files.sortedBy(File::getPath).forEach(::validateModuleMetadata)
    }

    private fun validatePom(pom: File) {
        check(pom.isFile) { "Missing prebuilt projection POM: ${pom.absolutePath}" }
        val publicationName = pom.parentFile.name
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }.newDocumentBuilder().parse(pom)
        val publishedArtifactId = document.documentElement.childText("artifactId")
            ?: error("${pom.absolutePath} does not declare its artifactId.")
        val publishedModule = publishedArtifactId.toRootModuleName(publicationName)
        val dependencies = document.getElementsByTagName("dependency")
            .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
        requiredDependenciesFor(publishedModule, pom).forEach { expectedModule ->
            val expectedArtifactIds = expectedModule.toPublicationArtifactIds(publicationName)
            val matches = dependencies.filter { it.childText("artifactId") in expectedArtifactIds }
            check(matches.isNotEmpty()) {
                "${pom.absolutePath} does not expose ${expectedArtifactIds.joinToString(" or ")}."
            }
            matches.forEach { dependency ->
                val artifactId = dependency.childText("artifactId")
                check(dependency.childText("scope") == "compile") {
                    "${pom.absolutePath} publishes $artifactId outside the compile/API surface."
                }
                check(dependency.childText("optional") != "true") {
                    "${pom.absolutePath} publishes $artifactId as optional."
                }
            }
        }
        forbiddenDependenciesFor(publishedModule).forEach { forbiddenModule ->
            val forbiddenArtifactIds = forbiddenModule.toPublicationArtifactIds(publicationName)
            check(dependencies.none { it.childText("artifactId") in forbiddenArtifactIds }) {
                "${pom.absolutePath} publishes forbidden compile-only dependency $forbiddenModule."
            }
        }
    }

    private fun validateModuleMetadata(moduleMetadata: File) {
        check(moduleMetadata.isFile) { "Missing prebuilt projection Gradle module metadata: ${moduleMetadata.absolutePath}" }
        val parsedRoot = JsonSlurper().parse(moduleMetadata) as? Map<*, *>
            ?: error("${moduleMetadata.absolutePath} is not a Gradle module metadata object.")
        val publishedModule = (parsedRoot["component"] as? Map<*, *>)?.get("module") as? String
            ?: error("${moduleMetadata.absolutePath} does not declare component.module.")
        val metadataApiVariant = parsedRoot["variants"]
            ?.let { it as? Iterable<*> }
            ?.filterIsInstance<Map<*, *>>()
            ?.firstOrNull { it["name"] == METADATA_API_VARIANT_NAME && it.usage() in API_USAGES }
            ?: error("${moduleMetadata.absolutePath} does not publish a $METADATA_API_VARIANT_NAME/API usage variant.")
        requiredDependenciesFor(publishedModule, moduleMetadata).forEach { expectedModule ->
            check(metadataApiVariant.dependencies().any { it["module"] == expectedModule }) {
                "${moduleMetadata.absolutePath} does not expose $expectedModule from $METADATA_API_VARIANT_NAME."
            }
        }
        val variants = parsedRoot["variants"]
            ?.let { it as? Iterable<*> }
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        forbiddenDependenciesFor(publishedModule).forEach { forbiddenModule ->
            check(variants.none { variant -> variant.dependencies().any { it["module"] == forbiddenModule } }) {
                "${moduleMetadata.absolutePath} publishes forbidden compile-only dependency $forbiddenModule."
            }
        }
    }

    private fun requiredDependenciesFor(publishedModule: String, metadataFile: File): List<String> =
        requiredApiDependencies.get()[publishedModule]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: error("No API dependency contract configured for $publishedModule: ${metadataFile.absolutePath}")

    private fun forbiddenDependenciesFor(publishedModule: String): List<String> =
        forbiddenPublishedDependencies.get()[publishedModule]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    private fun String.toRootModuleName(publicationName: String): String = when (publicationName) {
        "jvm" -> removeSuffix("-jvm")
        "mingwX64" -> removeSuffix("-mingwx64")
        "kotlinMultiplatform" -> this
        else -> error("Unsupported prebuilt projection publication '$publicationName'.")
    }

    private fun String.toPublicationArtifactIds(publicationName: String): Set<String> = when (publicationName) {
        "jvm" -> setOf("$this-jvm")
        // KGP can retain the KMP root coordinate when selecting our Native
        // reference variant. Its Gradle module metadata redirects to mingwX64.
        "mingwX64" -> setOf(this, "$this-mingwx64")
        "kotlinMultiplatform" -> setOf(this)
        else -> error("Unsupported prebuilt projection publication '$publicationName'.")
    }

    private fun Map<*, *>.usage(): String? = (get("attributes") as? Map<*, *>)?.get("org.gradle.usage") as? String

    private fun Map<*, *>.dependencies(): List<Map<*, *>> =
        (get("dependencies") as? Iterable<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

    private fun Element.childText(name: String): String? = getElementsByTagName(name).item(0)?.textContent?.trim()

    private companion object {
        const val METADATA_API_VARIANT_NAME = "metadataApiElements"
        val API_USAGES = setOf("kotlin-metadata", "kotlin-api", "java-api")
    }
}
