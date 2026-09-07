package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/**
 * Adds the package-identity pieces that are normally emitted by the Windows App SDK build targets.
 * The Kotlin/Native application layout is assembled without MSBuild, so these pieces have to be
 * derived from the restored package contents before WinApp CLI packages the directory.
 */
internal object AppxManifestPackageSupport {
    private const val APPX_NAMESPACE = "http://schemas.microsoft.com/appx/manifest/foundation/windows10"
    private const val PACKAGE_DEPENDENCY_NAME = "PackageDependency"
    private const val EXTENSION_NAME = "Extension"
    private const val IN_PROCESS_SERVER_NAME = "InProcessServer"
    private const val ACTIVATABLE_CLASS_NAME = "ActivatableClass"
    private const val PACKAGE_DEPENDENCY_CATEGORY = "windows.activatableClass.inProcessServer"

    fun mergeRuntimeDependenciesAndExtensions(
        manifest: Path,
        packageRoot: Path,
        resolvedPackageManifestFiles: Iterable<Path>,
        runtimeIdentifier: String,
        restoredPackageRoots: Iterable<Path> = emptyList(),
        includeFrameworkDependencies: Boolean = true,
    ) {
        val document = readXml(manifest) ?: return
        val packageElement = document.documentElement ?: return
        val dependencies = packageElement.childElements("Dependencies").firstOrNull()
            ?: document.createElementNS(APPX_NAMESPACE, "Dependencies").also(packageElement::appendChild)

        val frameworkDependencies: List<PackageDependency> = if (includeFrameworkDependencies) discoveredFrameworkDependencies(
            resolvedPackageManifestFiles = resolvedPackageManifestFiles,
            restoredPackageRoots = restoredPackageRoots,
            runtimeIdentifier = runtimeIdentifier,
        ) else emptyList()
        frameworkDependencies.forEach { dependency ->
                val alreadyDeclared = dependencies.childElements(PACKAGE_DEPENDENCY_NAME).any { element ->
                    element.getAttribute("Name").equals(dependency.name, ignoreCase = true)
                }
                if (!alreadyDeclared) {
                    dependencies.appendChild(
                        document.createElementNS(APPX_NAMESPACE, PACKAGE_DEPENDENCY_NAME).apply {
                            setAttribute("Name", dependency.name)
                            setAttribute("MinVersion", dependency.version)
                            setAttribute("Publisher", dependency.publisher)
                        },
                    )
                }
            }

        val registrations = discoverRegistrations(packageRoot)
        if (registrations.isNotEmpty()) {
            val extensions = packageElement.childElements("Extensions").firstOrNull()
                ?: document.createElementNS(APPX_NAMESPACE, "Extensions").also(packageElement::appendChild)
            mergeInProcessServerExtensions(document, extensions, packageRoot, registrations)
        }
        writeXml(manifest, document)
    }

    /** Returns framework dependency packages that can be passed to Add-AppxPackage. */
    internal fun discoverFrameworkPackageArchives(
        restoredPackageRoots: Iterable<Path>,
        runtimeIdentifier: String,
    ): List<Path> {
        val architecture = winAppRuntimeArchitecture(runtimeIdentifier)
        return restoredPackageRoots
            .filter(Path::isDirectory)
            .flatMap { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter(Path::isRegularFile)
                        .filter { path ->
                            (path.toString().contains("win10-$architecture", ignoreCase = true) ||
                                path.parent?.fileName?.toString()?.equals("MSIX", ignoreCase = true) == true) &&
                                (path.fileName.toString().endsWith(".msix", ignoreCase = true) ||
                                    path.fileName.toString().endsWith(".appx", ignoreCase = true))
                        }
                        .sorted()
                        .asSequence()
                        .filter { archive -> readFrameworkManifest(archive) != null }
                        .toList()
                }
            }
            .distinctBy { path -> path.toAbsolutePath().normalize().toString().lowercase() }
            .sortedBy { path -> path.toAbsolutePath().normalize().toString().lowercase() }
    }

    private fun discoveredFrameworkDependencies(
        resolvedPackageManifestFiles: Iterable<Path>,
        restoredPackageRoots: Iterable<Path>,
        runtimeIdentifier: String,
    ): List<PackageDependency> {
        val architecture = winAppRuntimeArchitecture(runtimeIdentifier)
        return resolvedPackageManifestFiles
            .filter { it.isRegularFile() }
            .flatMap { file ->
                readJsonStringArrayField(Files.readString(file), "packageRoots")
                    .map(Path::of)
            }
            .plus(restoredPackageRoots)
            .filter { root -> root.isDirectory() }
            .filter { root ->
                root.parent?.fileName?.toString()?.contains("microsoft.windowsappsdk.runtime", ignoreCase = true) == true
            }
            .flatMap { root -> discoverFrameworkPackageManifests(root, architecture) }
            .distinctBy { dependency -> dependency.name.lowercase() to dependency.version }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun discoverFrameworkPackageManifests(root: Path, architecture: String): List<PackageDependency> {
        if (!root.isDirectory()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { path ->
                    path.isRegularFile() &&
                        (path.fileName.toString().endsWith(".msix", ignoreCase = true) ||
                            path.fileName.toString().endsWith(".appx", ignoreCase = true)) &&
                        path.toString().contains("win10-$architecture", ignoreCase = true)
                }
                .sorted()
                .asSequence()
                .mapNotNull(::readFrameworkManifest)
                .toList()
        }
    }

    private fun readFrameworkManifest(archive: Path): PackageDependency? = runCatching {
        ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry("AppxManifest.xml") ?: return@use null
            zip.getInputStream(entry).use { input ->
                val document = secureDocumentBuilderFactory().newDocumentBuilder().parse(input)
                val properties = document.getElementsByTagNameNS("*", "Framework")
                val isFramework = (0 until properties.length).any { index ->
                    properties.item(index).textContent.trim().equals("true", ignoreCase = true)
                }
                if (!isFramework) return@use null
                val identity = document.getElementsByTagNameNS("*", "Identity").item(0) as? Element
                    ?: return@use null
                val name = identity.getAttribute("Name").trim()
                val version = normalizeVersion(identity.getAttribute("Version"))
                val publisher = identity.getAttribute("Publisher").trim()
                if (name.isBlank() || version == null || publisher.isBlank()) null
                else PackageDependency(name, version, publisher)
            }
        }
    }.getOrNull()

    private fun discoverRegistrations(packageRoot: Path): Map<String, Set<String>> {
        val registrations = LinkedHashMap<String, LinkedHashSet<String>>()
        if (!packageRoot.isDirectory()) return registrations
        Files.walk(packageRoot).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().equals("LiftedWinRTClassRegistrations.xml", ignoreCase = true) }
                .sorted()
                .forEach { registration ->
                    readXml(registration)?.let { document ->
                        document.getElementsByTagNameNS("*", IN_PROCESS_SERVER_NAME).forEachElement { server ->
                            val path = server.childElements("Path").firstOrNull()?.textContent?.trim().orEmpty()
                            if (path.isBlank()) return@forEachElement
                            val normalizedPath = path.replace('/', '\\').trimStart('\\')
                            val target = packageRoot.resolve(normalizedPath.replace('\\', java.io.File.separatorChar)).normalize()
                            if (!target.startsWith(packageRoot) || !target.isRegularFile()) return@forEachElement
                            val classes = registrations.getOrPut(normalizedPath) { linkedSetOf() }
                            server.childElements(ACTIVATABLE_CLASS_NAME).forEach { activatableClass ->
                                val className = activatableClass.getAttribute("ActivatableClassId").ifBlank {
                                    activatableClass.getAttribute("Name")
                                }.trim()
                                if (className.isNotBlank()) classes += className
                            }
                        }
                    }
                }
        }
        return registrations
    }

    private fun mergeInProcessServerExtensions(
        document: Document,
        extensions: Element,
        packageRoot: Path,
        registrations: Map<String, Set<String>>,
    ) {
        val existing = linkedMapOf<String, Element>()
        extensions.childElements(EXTENSION_NAME)
            .filter { it.getAttribute("Category").equals(PACKAGE_DEPENDENCY_CATEGORY, ignoreCase = true) }
            .forEach { extension ->
                extension.childElements(IN_PROCESS_SERVER_NAME).firstOrNull()
                    ?.childElements("Path")
                    ?.firstOrNull()
                    ?.textContent
                    ?.trim()
                    ?.let { path -> existing[manifestPathKey(path)] = extension }
            }

        registrations.forEach { (path, classes) ->
            if (classes.isEmpty()) return@forEach
            val extension = existing[manifestPathKey(path)] ?: document.createElementNS(APPX_NAMESPACE, EXTENSION_NAME).also { created ->
                created.setAttribute("Category", PACKAGE_DEPENDENCY_CATEGORY)
                val server = document.createElementNS(APPX_NAMESPACE, IN_PROCESS_SERVER_NAME)
                server.appendChild(document.createElementNS(APPX_NAMESPACE, "Path").apply { textContent = path })
                created.appendChild(server)
                extensions.appendChild(created)
                existing[manifestPathKey(path)] = created
            }
            val server = extension.childElements(IN_PROCESS_SERVER_NAME).firstOrNull() ?: return@forEach
            val existingClasses = server.childElements(ACTIVATABLE_CLASS_NAME)
                .mapNotNull { it.getAttribute("ActivatableClassId").ifBlank { it.getAttribute("Name") }.trim().takeIf(String::isNotBlank) }
                .toMutableSet()
            classes.sorted().forEach { className ->
                if (existingClasses.add(className)) {
                    server.appendChild(document.createElementNS(APPX_NAMESPACE, ACTIVATABLE_CLASS_NAME).apply {
                        setAttribute("ActivatableClassId", className)
                        setAttribute("ThreadingModel", "both")
                    })
                }
            }
        }
    }

    private fun readXml(path: Path): Document? = runCatching {
        secureDocumentBuilderFactory().newDocumentBuilder().parse(path.toFile())
    }.getOrNull()

    private fun writeXml(path: Path, document: Document) {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        transformer.transform(DOMSource(document), StreamResult(path.toFile()))
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }

    private fun normalizeVersion(value: String): String? {
        val parts = value.trim().split('.')
        if (parts.size != 4 || parts.any { it.isBlank() || it.toIntOrNull() == null }) return null
        return parts.joinToString(".")
    }

    private fun manifestPathKey(value: String): String =
        value.replace('/', '\\').trimStart('\\').lowercase()

    private fun org.w3c.dom.Node.childElements(localName: String): List<Element> =
        (0 until childNodes.length)
            .asSequence()
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.localName.equals(localName, ignoreCase = true) }
            .toList()

    private inline fun org.w3c.dom.NodeList.forEachElement(action: (Element) -> Unit) {
        for (index in 0 until length) {
            (item(index) as? Element)?.let(action)
        }
    }

    private data class PackageDependency(
        val name: String,
        val version: String,
        val publisher: String,
    )
}
