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
    val qualifiers: String? = null,
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
                val qualifiers = candidate.getAttribute("qualifiers")
                    .trim()
                    .takeIf(String::isNotBlank)
                result += PriResourceMapping(resourceUri, type, value, qualifiers)
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
                    val uriPath = resourcePath(mapping.resourceUri)
                    if (uriPath != null && !pathMatchesCandidate(uriPath, relative, mapping.qualifiers)) {
                        errors += "PRI mapping[$index] Path Value '$rawValue' does not match " +
                            "resource URI path '${uriPath.toString().replace('\\', '/')}'"
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
        return buildSet {
            identity.getAttribute("Name").trim().takeIf(String::isNotBlank)?.let(::add)
            runCatching {
                Files.walk(packageRoot).use { stream ->
                    stream
                        .filter(Files::isRegularFile)
                        .map { path -> path.fileName.toString() }
                        .filter { name ->
                            name.endsWith(".pri", ignoreCase = true) &&
                                !name.equals("resources.pri", ignoreCase = true) &&
                                !name.startsWith("resources.language-", ignoreCase = true)
                        }
                        .map { name -> name.substringBeforeLast('.') }
                        .filter(String::isNotBlank)
                        .forEach(::add)
                }
            }
        }
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

    private fun pathKey(path: Path): String =
        path.toString().replace('\\', '/').trimStart('/').lowercase()

    /**
     * A PRI URI names the logical resource.  Qualified candidates add their qualifier suffix to
     * the physical file name (for example `Logo.scale-200.png`), so an exact path comparison
     * would reject valid scale/target-size assets.  Keep the directory, base name, and extension
     * anchored while allowing the qualifier suffix recorded by makepri.
     */
    private fun pathMatchesCandidate(uriPath: Path, candidatePath: Path, qualifiers: String?): Boolean {
        if (pathKey(uriPath) == pathKey(candidatePath)) return true
        val uriParent = uriPath.parent?.let(::pathKey).orEmpty()
        val candidateParent = candidatePath.parent?.let(::pathKey).orEmpty()
        if (uriParent != candidateParent) return false

        val uriName = uriPath.fileName.toString()
        val candidateName = candidatePath.fileName.toString()
        val uriExtension = uriName.substringAfterLast('.', "").lowercase()
        val candidateExtension = candidateName.substringAfterLast('.', "").lowercase()
        if (uriExtension != candidateExtension) return false
        val uriStem = uriName.substringBeforeLast('.', uriName)
        val candidateStem = candidateName.substringBeforeLast('.', candidateName)
        val qualifierSuffix = candidateStem
            .takeIf { it.length > uriStem.length + 1 && it.startsWith("$uriStem.", ignoreCase = true) }
            ?.substring(uriStem.length + 1)
            ?: return false

        val expectedQualifiers = parseQualifiers(qualifiers)
        if (expectedQualifiers.isEmpty()) return true
        val actualQualifiers = qualifierSuffix
            .split('_')
            .mapNotNull { token ->
                val separator = token.indexOf('-')
                if (separator <= 0 || separator == token.lastIndex) return@mapNotNull null
                token.substring(0, separator).lowercase() to token.substring(separator + 1).lowercase()
            }
            .toMap()
        return expectedQualifiers.all { (name, value) -> actualQualifiers[name] == value }
    }

    private fun parseQualifiers(raw: String?): Map<String, String> =
        raw.orEmpty()
            .split(',')
            .mapNotNull { token ->
                val normalized = token.trim()
                val separator = normalized.indexOf('-')
                if (separator <= 0 || separator == normalized.lastIndex) return@mapNotNull null
                normalized.substring(0, separator).lowercase() to normalized.substring(separator + 1).lowercase()
            }
            .toMap()

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
