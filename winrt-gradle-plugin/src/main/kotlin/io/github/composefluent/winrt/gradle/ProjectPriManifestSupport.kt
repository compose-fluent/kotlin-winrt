package io.github.composefluent.winrt.gradle

import java.io.File
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isRegularFile
import org.w3c.dom.Element

internal object ProjectPriManifestSupport {
    fun defaultLanguage(explicitLanguage: String, manifests: Iterable<File>): String =
        explicitLanguage.ifBlank {
            manifests.asSequence()
                .mapNotNull { file -> readManifestDefaultLanguage(file.toPath()) }
                .firstOrNull()
                ?: "en-US"
        }

    fun indexName(explicitIndexName: String, fallbackIndexName: String, manifests: Iterable<File>): String =
        explicitIndexName.ifBlank {
            manifests.asSequence()
                .mapNotNull { file -> readManifestIdentityName(file.toPath()) }
                .firstOrNull()
                ?: fallbackIndexName.ifBlank { "Application" }
        }

    fun makePriDefaultQualifier(language: String, qualifiers: Iterable<String>): String =
        (listOf("lang-$language") + qualifiers.filter(String::isNotBlank)).joinToString("_")

    fun fullIndexDefaultQualifiers(language: String, qualifiers: Iterable<String>): List<Pair<String, String>> {
        val qualifierValues = qualifiers
            .mapNotNull { qualifier ->
                val normalized = qualifier.trim()
                val separator = normalized.indexOf('-')
                if (separator <= 0) null else normalized.substring(0, separator).lowercase() to normalized.substring(separator + 1)
            }
            .toMap()
        return listOf(
            "Language" to language,
            "Contrast" to (qualifierValues["contrast"] ?: "standard"),
            "Scale" to (qualifierValues["scale"] ?: "100"),
            "HomeRegion" to (qualifierValues["homeregion"] ?: "001"),
            "TargetSize" to (qualifierValues["targetsize"] ?: "256"),
            "LayoutDirection" to (qualifierValues["layoutdirection"] ?: "LTR"),
            "DXFeatureLevel" to (qualifierValues["dxfeaturelevel"] ?: "DX9"),
            "Configuration" to (qualifierValues["configuration"] ?: ""),
            "AlternateForm" to (qualifierValues["alternateform"] ?: ""),
            "Platform" to (qualifierValues["platform"] ?: "UAP"),
        )
    }

    fun validatePackageManifest(manifest: Path): List<String> {
        if (!manifest.isRegularFile()) {
            return listOf("manifest file does not exist: $manifest")
        }
        val document = readXmlDocument(manifest) ?: return listOf("manifest XML could not be parsed: $manifest")
        val errors = mutableListOf<String>()
        val root = document.documentElement
        if (root == null || !root.localName.equals("Package", ignoreCase = true)) {
            errors += "manifest root element must be Package"
        }
        val identity = document.getElementsByTagNameNS("*", "Identity").item(0)
        if (identity == null) {
            errors += "manifest must contain an Identity element"
        } else {
            val attributes = identity.attributes
            val name = attributes?.getNamedItem("Name")?.nodeValue?.trim().orEmpty()
            val publisher = attributes?.getNamedItem("Publisher")?.nodeValue?.trim().orEmpty()
            val version = attributes?.getNamedItem("Version")?.nodeValue?.trim().orEmpty()
            if (name.isBlank()) {
                errors += "manifest Identity must declare Name"
            }
            if (publisher.isBlank()) {
                errors += "manifest Identity must declare Publisher"
            }
            if (version.isBlank()) {
                errors += "manifest Identity must declare Version"
            } else if (!APPX_VERSION.matches(version)) {
                errors += "manifest Identity Version must use four numeric components"
            }
        }
        val applications = document.getElementsByTagNameNS("*", "Applications").item(0)
        if (applications == null) {
            errors += "manifest must contain an Applications element"
        } else {
            val applicationElements = applications.childElements("Application")
            if (applicationElements.isEmpty()) {
                errors += "manifest Applications must contain at least one Application element"
            }
            applicationElements.forEachIndexed { index, application ->
                val prefix = "manifest Application[$index]"
                val executable = application.getAttribute("Executable").trim()
                val entryPoint = application.getAttribute("EntryPoint").trim()
                if (executable.isBlank()) {
                    errors += "$prefix must declare Executable"
                }
                if (entryPoint.isBlank()) {
                    errors += "$prefix must declare EntryPoint"
                }
                application.childElements("VisualElements").forEach { visualElements ->
                    listOf("DisplayName", "Description", "BackgroundColor", "Square150x150Logo", "Square44x44Logo")
                        .forEach { attribute ->
                            if (visualElements.getAttribute(attribute).trim().isBlank()) {
                                errors += "$prefix VisualElements must declare $attribute"
                            }
                        }
                }
            }
        }
        return errors
    }

    private fun readManifestDefaultLanguage(manifest: Path): String? {
        if (!manifest.isRegularFile()) return null
        val document = readXmlDocument(manifest) ?: return null
        val resources = document.getElementsByTagNameNS("*", "Resource")
        return (0 until resources.length).asSequence()
            .mapNotNull { index -> resources.item(index)?.attributes?.getNamedItem("Language")?.nodeValue?.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("x-generate", ignoreCase = true) }
    }

    private fun readManifestIdentityName(manifest: Path): String? {
        if (!manifest.isRegularFile()) return null
        val document = readXmlDocument(manifest) ?: return null
        val identities = document.getElementsByTagNameNS("*", "Identity")
        return (0 until identities.length).asSequence()
            .mapNotNull { index -> identities.item(index)?.attributes?.getNamedItem("Name")?.nodeValue?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun readXmlDocument(path: Path) = runCatching {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }.newDocumentBuilder().parse(path.toFile())
    }.getOrNull()

    private fun org.w3c.dom.Node.childElements(localName: String): List<Element> =
        (0 until childNodes.length)
            .asSequence()
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.localName.equals(localName, ignoreCase = true) }
            .toList()
}

private val APPX_VERSION = Regex("""\d+\.\d+\.\d+\.\d+""")
