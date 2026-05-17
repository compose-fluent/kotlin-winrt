package io.github.composefluent.winrt.gradle

import java.io.File
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isRegularFile

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
}
