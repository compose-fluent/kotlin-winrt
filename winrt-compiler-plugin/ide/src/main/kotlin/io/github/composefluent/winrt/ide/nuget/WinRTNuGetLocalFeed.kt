package io.github.composefluent.winrt.ide.nuget

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** NuGet local feeds use nuspec metadata in flat or hierarchical nupkg folders. */
internal class WinRTNuGetLocalFeed(private val directory: Path) {
    private data class Package(val id: String, val version: String, val details: WinRTNuGetPackageDetails,
        val archive: Path, val readme: String)

    private val packages by lazy {
        require(Files.isDirectory(directory)) { "The local package source does not exist: $directory" }
        Files.walk(directory, 4).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".nupkg", true) }.sorted().toList()
        }.mapNotNull { archive -> ZipFile(archive.toFile()).use { zip ->
            val spec = zip.entries().asSequence().firstOrNull { it.name.endsWith(".nuspec", true) && '/' !in it.name } ?: return@use null
            val metadata = zip.getInputStream(spec).use { parse(it.readNBytes(MAX_SPEC + 1)) }
            Package(metadata.text("id"), metadata.text("version"), details(metadata), archive, metadata.text("readme"))
        } }.filter { Regex("[A-Za-z0-9_.-]+").matches(it.id) }
    }

    fun search(query: String, prerelease: Boolean, skip: Int): WinRTNuGetSearchPage {
        val results = packages.filter { prerelease || '-' !in it.version }.groupBy { it.id.lowercase(Locale.ROOT) }
            .values.mapNotNull { versions ->
                val sorted = versions.sortedWith { a, b -> WinRTNuGetVersion.compare(b.version, a.version) }
                val item = sorted.first()
                if (!query.trim().split(Regex("\\s+")).all { term ->
                    listOf(item.id, item.details.description, item.details.authors).any { it.contains(term, true) }
                }) null else WinRTNuGetSearchResult(item.id, item.version, item.details.description, item.details.authors,
                    sorted.map { it.version }.distinct(), projectUrl = item.details.projectUrl)
            }.sortedBy { it.id.lowercase(Locale.ROOT) }
        return WinRTNuGetSearchPage(results.drop(skip).take(40), results.size)
    }

    fun versions(id: String, prerelease: Boolean): List<String> = packages.filter {
        it.id.equals(id, true) && (prerelease || '-' !in it.version)
    }.map { it.version }.distinct().sortedWith { a, b -> WinRTNuGetVersion.compare(b, a) }

    private fun find(id: String, version: String) = packages.firstOrNull {
        it.id.equals(id, true) && WinRTNuGetVersion.compare(it.version, version) == 0
    } ?: error("Package $id $version was not found in $directory.")

    fun details(id: String, version: String) = find(id, version).details

    fun readme(id: String, version: String): String? {
        val item = find(id, version)
        val name = readmePath(item.readme) ?: return null
        return ZipFile(item.archive.toFile()).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { it.name.replace('\\', '/').equals(name, true) } ?: return@use null
            zip.getInputStream(entry).use { stream -> readText(stream.readNBytes(MAX_README + 1)) }
        }
    }

    companion object {
        private const val MAX_SPEC = 1024 * 1024
        private const val MAX_README = 2 * 1024 * 1024
        private fun parse(bytes: ByteArray): Element {
            require(bytes.size <= MAX_SPEC) { "The package nuspec exceeds 1 MiB." }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            return factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement.children().first { it.localName == "metadata" }
        }
        private fun Element.children() = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
        private fun Element.text(name: String) = children().firstOrNull { it.localName == name }?.textContent?.trim().orEmpty()
        private fun details(metadata: Element): WinRTNuGetPackageDetails {
            val dependencies = metadata.children().firstOrNull { it.localName == "dependencies" }
            fun pairs(node: Element) = node.children().filter { it.localName == "dependency" }
                .map { it.getAttribute("id") to it.getAttribute("version") }
            val groups = dependencies?.let { node ->
                node.children().filter { it.localName == "group" }.map { WinRTNuGetDependencyGroup(it.getAttribute("targetFramework"), pairs(it)) } +
                    pairs(node).takeIf(List<*>::isNotEmpty)?.let { listOf(WinRTNuGetDependencyGroup("", it)) }.orEmpty()
            }.orEmpty()
            return WinRTNuGetPackageDetails(metadata.text("description"), metadata.text("authors"), metadata.text("projectUrl"),
                metadata.text("license").ifEmpty { metadata.text("licenseUrl") }, "", groups)
        }
        private fun readmePath(value: String): String? {
            val name = value.replace('\\', '/').trim()
            if (name.isEmpty()) return null
            require(!name.startsWith('/') && ':' !in name && name.split('/').none { it == ".." }) { "The package README path must stay inside the package." }
            return name.removePrefix("./")
        }
        private fun readText(bytes: ByteArray): String? {
            require(bytes.size <= MAX_README) { "The package README exceeds 2 MiB." }
            return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").takeIf(String::isNotBlank)
        }
        fun readRestoredReadme(root: Path): String? {
            if (!Files.isDirectory(root)) return null
            val spec = Files.list(root).use { files -> files.filter { it.fileName.toString().endsWith(".nuspec", true) }.findFirst().orElse(null) } ?: return null
            val name = Files.newInputStream(spec).use { readmePath(parse(it.readNBytes(MAX_SPEC + 1)).text("readme")) } ?: return null
            val resolved = root.resolve(name).normalize()
            require(resolved.startsWith(root.normalize())) { "The package README path must stay inside the package." }
            if (!Files.isRegularFile(resolved)) return null
            require(resolved.toRealPath().startsWith(root.toRealPath())) { "The package README cannot link outside the package." }
            return Files.newInputStream(resolved).use { readText(it.readNBytes(MAX_README + 1)) }
        }
    }
}
