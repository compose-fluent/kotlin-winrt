package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.w3c.dom.Element
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isRegularFile

/** A compact representation of the file-bearing part of a makepri dump. */
internal data class PriResourceMapping(
    val resourceUri: String,
    val candidateType: String,
    val value: String?,
)

/**
 * Reads the structured XML emitted by `makepri dump` and checks that file candidates still point
 * at the package that is being verified. Framework resource maps are intentionally ignored when
 * their files are not in the application package; those files are supplied by the framework.
 */
internal object PriResourceMapValidator {
    fun readDump(path: Path): List<PriResourceMapping> {
        if (!Files.isRegularFile(path)) {
            throw GradleException("makepri dump does not exist: $path")
        }
        return parseDump(Files.readString(path))
    }

    fun parseDump(xml: String): List<PriResourceMapping> {
        val document = runCatching {
            secureDocumentBuilderFactory().newDocumentBuilder().parse(xml.byteInputStream())
        }.getOrElse { error ->
            throw GradleException("Could not parse makepri dump: ${error.message}", error)
        }
        val result = mutableListOf<PriResourceMapping>()
        val namedResources = document.getElementsByTagName("NamedResource")
        for (resourceIndex in 0 until namedResources.length) {
            val resource = namedResources.item(resourceIndex) as? Element ?: continue
            val resourceUri = resource.getAttribute("uri").trim()
            val candidates = resource.getElementsByTagName("Candidate")
            for (candidateIndex in 0 until candidates.length) {
                val candidate = candidates.item(candidateIndex) as? Element ?: continue
                val type = candidate.getAttribute("type").trim()
                if (type.isBlank()) continue
                val value = candidate.getElementsByTagName("Value")
                    .item(0)
                    ?.textContent
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                result += PriResourceMapping(resourceUri, type, value)
            }
        }
        return result
    }

    fun validate(
        mappings: Collection<PriResourceMapping>,
        packageRoot: Path,
    ): List<String> {
        val normalizedRoot = packageRoot.toAbsolutePath().normalize()
        val applicationResourceMapNames = applicationResourceMapNames(normalizedRoot)
        val errors = mutableListOf<String>()
        mappings.forEachIndexed { index, mapping ->
            if (mapping.resourceUri.isBlank()) {
                errors += "PRI mapping[$index] is missing resourceUri"
                return@forEachIndexed
            }
            val applicationOwned = resourceMapName(mapping.resourceUri)
                ?.let { name -> applicationResourceMapNames.isEmpty() || applicationResourceMapNames.any { it.equals(name, ignoreCase = true) } }
                ?: applicationResourceMapNames.isEmpty()
            if (!applicationOwned) return@forEachIndexed

            when {
                mapping.candidateType.equals("Path", ignoreCase = true) -> {
                    val rawValue = mapping.value.orEmpty()
                    if (rawValue.isBlank()) {
                        errors += "PRI mapping[$index] Path candidate is missing Value: ${mapping.resourceUri}"
                        return@forEachIndexed
                    }
                    val relative = runCatching { rawValue.toSafeRelativePath("PRI candidate path") }
                        .getOrElse { error ->
                            errors += "PRI mapping[$index] has an invalid Path Value '$rawValue': ${error.message}"
                            return@forEachIndexed
                        }
                    val resolved = normalizedRoot.resolve(relative).normalize()
                    if (!resolved.startsWith(normalizedRoot)) {
                        errors += "PRI mapping[$index] Path Value escapes the package root: $rawValue"
                    } else if (!resolved.isRegularFile()) {
                        errors += "PRI mapping[$index] Path Value is missing from the package: $rawValue"
                    }
                }

                // EmbeddedData is stored inside the PRI itself. Its URI commonly ends in
                // `.xbf`, but that suffix names the embedded resource and does not require a
                // separate XBF payload file in the package.
            }
        }
        return errors
    }

    private fun applicationResourceMapNames(packageRoot: Path): Set<String> {
        val manifest = packageRoot.resolve("AppxManifest.xml")
        if (!manifest.isRegularFile()) return emptySet()
        val document = runCatching { secureDocumentBuilderFactory().newDocumentBuilder().parse(manifest.toFile()) }
            .getOrNull() ?: return emptySet()
        val identities = document.getElementsByTagName("Identity")
        if (identities.length == 0) return emptySet()
        val identity = identities.item(0) as? Element ?: return emptySet()
        return setOf(identity.getAttribute("Name").trim()).filter(String::isNotBlank).toSet()
    }

    private fun resourceMapName(resourceUri: String): String? =
        runCatching { URI(resourceUri).host?.takeIf(String::isNotBlank) }.getOrNull()

    private fun resourcePath(resourceUri: String): Path? {
        val path = runCatching { URI(resourceUri).path }.getOrNull() ?: return null
        val marker = "/Files/"
        val start = path.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        return runCatching { path.substring(start + marker.length).toSafeRelativePath("PRI resource URI") }.getOrNull()
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
}
