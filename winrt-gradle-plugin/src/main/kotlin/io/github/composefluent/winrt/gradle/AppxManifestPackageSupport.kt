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

    fun useDevelopmentIdentity(manifest: Path): String {
        val document = requireNotNull(readXml(manifest)) { "Cannot read development package manifest: $manifest" }
        val identity = requireNotNull(document.documentElement.childElements("Identity").firstOrNull()) {
            "Development package manifest must declare Identity: $manifest"
        }
        val originalName = identity.getAttribute("Name")
        val developmentName = "$originalName.dev"
        require(originalName.isNotBlank() && developmentName.length <= 50) {
            "Package Identity Name must contain 1 to 46 characters to append the development suffix '.dev': $originalName"
        }
        identity.setAttribute("Name", developmentName)
        // Absolute references into the application's resource map must follow the new identity.
        fun rewriteResourceReference(value: String): String {
            val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return value
            return if (uri.scheme.equals("ms-resource", true) && uri.authority.equals(originalName, true)) {
                java.net.URI(uri.scheme, developmentName, uri.path, uri.query, uri.fragment).toString()
            } else value
        }
        val elements = document.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index)
            val attributes = element.attributes
            for (attributeIndex in 0 until attributes.length) {
                val attribute = attributes.item(attributeIndex)
                attribute.nodeValue = rewriteResourceReference(attribute.nodeValue)
            }
            for (childIndex in 0 until element.childNodes.length) {
                val child = element.childNodes.item(childIndex)
                if (child.nodeType == Node.TEXT_NODE) child.nodeValue = rewriteResourceReference(child.nodeValue)
            }
        }
        writeXml(manifest, document)
        return developmentName
    }

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
        deduplicatePackageDependencies(dependencies)

        val frameworkDependencies: List<PackageDependency> = if (includeFrameworkDependencies) discoveredFrameworkDependencies(
            resolvedPackageManifestFiles = resolvedPackageManifestFiles,
            restoredPackageRoots = restoredPackageRoots,
            runtimeIdentifier = runtimeIdentifier,
        ) else emptyList()
        frameworkDependencies.forEach { dependency ->
                val existing = dependencies.childElements(PACKAGE_DEPENDENCY_NAME).firstOrNull { element ->
                    element.getAttribute("Name").equals(dependency.name, ignoreCase = true)
                }
                if (existing == null) {
                    dependencies.appendChild(
                        document.createElementNS(APPX_NAMESPACE, PACKAGE_DEPENDENCY_NAME).apply {
                            setAttribute("Name", dependency.name)
                            setAttribute("MinVersion", dependency.version)
                            setAttribute("Publisher", dependency.publisher)
                        },
                    )
                } else {
                    val publisher = existing.getAttribute("Publisher").trim()
                    val minVersion = normalizeVersion(existing.getAttribute("MinVersion"))
                    if (!publisher.equals(dependency.publisher, ignoreCase = true)) {
                        throw IllegalArgumentException(
                            "AppX manifest dependency '${dependency.name}' declares publisher '$publisher', " +
                                "but the restored framework uses '${dependency.publisher}'.",
                        )
                    }
                    if (minVersion == null || compareVersions(minVersion, dependency.version) > 0) {
                        throw IllegalArgumentException(
                            "AppX manifest dependency '${dependency.name}' declares MinVersion " +
                                "'${existing.getAttribute("MinVersion")}', but the restored framework package " +
                                "only provides version '${dependency.version}'.",
                        )
                    }
                    val existingArchitecture = existing.getAttribute("ProcessorArchitecture").trim()
                    if (existingArchitecture.isNotBlank() &&
                        !architectureMatchesRuntime(existingArchitecture, runtimeIdentifier)
                    ) {
                        throw IllegalArgumentException(
                            "AppX manifest dependency '${dependency.name}' declares processor architecture " +
                                "'$existingArchitecture', but the selected runtime is '$runtimeIdentifier'.",
                        )
                    }
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
        return restoredPackageRoots
            .filter(Path::isDirectory)
            .flatMap { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter(Path::isRegularFile)
                        .filter { path ->
                            path.fileName.toString().endsWith(".msix", ignoreCase = true) ||
                                path.fileName.toString().endsWith(".appx", ignoreCase = true)
                        }
                        .sorted()
                        .asSequence()
                        .filter { archive ->
                            readFrameworkManifest(archive)?.let { dependency ->
                                architectureMatchesRuntime(dependency.processorArchitecture, runtimeIdentifier)
                            } == true
                        }
                        .toList()
                }
            }
            .distinctBy { path -> path.toAbsolutePath().normalize().toString().lowercase() }
            .sortedBy { path -> path.toAbsolutePath().normalize().toString().lowercase() }
    }

    /**
     * Selects only framework archives required by the final application manifest. Candidate
     * discovery is intentionally broader because a restore root can contain several packages and
     * versions; installation must not pass all of them to Add-AppxPackage.
     */
    internal fun selectFrameworkPackageArchives(
        applicationPackage: Path,
        candidateArchives: Iterable<Path>,
        runtimeIdentifier: String,
    ): List<Path> {
        val requirements = readPackageDependencies(applicationPackage)
        if (requirements.isEmpty()) return emptyList()

        val candidates = candidateArchives
            .map { archive ->
                archive.toAbsolutePath().normalize() to
                    validateFrameworkPackageArchive(archive, runtimeIdentifier)
            }
            .distinctBy { (archive, _) -> archive.toString().lowercase() }
        val selected = linkedMapOf<String, Path>()
        requirements.forEach { requirement ->
            val match = candidates
                .asSequence()
                .filter { (_, dependency) ->
                    dependency.name.equals(requirement.name, ignoreCase = true) &&
                        dependency.publisher.equals(requirement.publisher, ignoreCase = true) &&
                        compareVersions(dependency.version, requirement.minVersion) >= 0 &&
                        (requirement.processorArchitecture == null ||
                            architectureMatchesRuntime(requirement.processorArchitecture, runtimeIdentifier))
                }
                .maxWithOrNull { left, right ->
                    compareVersions(left.second.version, right.second.version).takeIf { it != 0 }
                        ?: left.second.processorArchitecture.compareTo(right.second.processorArchitecture, ignoreCase = true)
                }
                ?: throw IllegalArgumentException(
                    "No restored framework package satisfies application dependency " +
                        "${requirement.name}@${requirement.minVersion} (${requirement.publisher}) " +
                        "for '$runtimeIdentifier'.",
                )
            selected[requirement.name.lowercase()] = match.first
        }
        return selected.values.toList()
    }

    private fun discoveredFrameworkDependencies(
        resolvedPackageManifestFiles: Iterable<Path>,
        restoredPackageRoots: Iterable<Path>,
        runtimeIdentifier: String,
    ): List<PackageDependency> {
        return resolvedPackageManifestFiles
            .filter { it.isRegularFile() }
            .flatMap { file ->
                readJsonStringArrayField(Files.readString(file), "packageRoots")
                    .map(Path::of)
            }
            .plus(restoredPackageRoots)
            .filter { root -> root.isDirectory() }
            .distinctBy { root -> root.toAbsolutePath().normalize().toString().lowercase() }
            .flatMap { root -> discoverFrameworkPackageManifests(root, runtimeIdentifier) }
            .groupBy { dependency -> dependency.name.lowercase() }
            .values
            .map { dependencies ->
                val publishers = dependencies.map { dependency -> dependency.publisher.lowercase() }.distinct()
                if (publishers.size > 1) {
                    throw IllegalArgumentException(
                        "Restored framework package '${dependencies.first().name}' has conflicting publishers: " +
                            dependencies.joinToString { dependency -> "${dependency.version}=${dependency.publisher}" },
                    )
                }
                dependencies.maxWithOrNull { left, right ->
                    compareVersions(left.version, right.version).takeIf { it != 0 }
                        ?: left.processorArchitecture.compareTo(right.processorArchitecture, ignoreCase = true)
                } ?: error("Framework dependency group is empty")
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun deduplicatePackageDependencies(dependencies: Element) {
        val existingByName = LinkedHashMap<String, Element>()
        dependencies.childElements(PACKAGE_DEPENDENCY_NAME).forEach { dependency ->
            val name = dependency.getAttribute("Name").trim()
            if (name.isBlank()) return@forEach
            val key = name.lowercase()
            val existing = existingByName[key]
            if (existing == null) {
                existingByName[key] = dependency
                return@forEach
            }
            val differingAttributes = listOf("Publisher", "MinVersion", "ProcessorArchitecture")
                .filter { attribute ->
                    existing.getAttribute(attribute).trim() != dependency.getAttribute(attribute).trim()
                }
            if (differingAttributes.isNotEmpty()) {
                throw IllegalArgumentException(
                    "AppX manifest contains duplicate dependency '$name' with conflicting " +
                        "${differingAttributes.joinToString()}.",
                )
            }
            dependencies.removeChild(dependency)
        }
    }

    private fun discoverFrameworkPackageManifests(root: Path, runtimeIdentifier: String): List<PackageDependency> {
        if (!root.isDirectory()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { path ->
                    path.isRegularFile() &&
                        (path.fileName.toString().endsWith(".msix", ignoreCase = true) ||
                            path.fileName.toString().endsWith(".appx", ignoreCase = true))
                }
                .sorted()
                .asSequence()
                .mapNotNull { archive ->
                    readFrameworkManifest(archive)?.takeIf { dependency ->
                        architectureMatchesRuntime(dependency.processorArchitecture, runtimeIdentifier)
                    }
                }
                .toList()
        }
    }

    /**
     * Validates an explicitly supplied dependency package before handing it to Add-AppxPackage.
     * Discovery is allowed to skip packages for another architecture; explicit inputs are user
     * intent and therefore receive a diagnostic instead of being silently ignored.
     */
    internal fun validateFrameworkPackageArchive(archive: Path, runtimeIdentifier: String): PackageDependency {
        if (!archive.isRegularFile()) {
            throw IllegalArgumentException("Configured AppX dependency package does not exist: $archive")
        }
        val dependency = readFrameworkManifest(archive)
            ?: throw IllegalArgumentException(
                "Configured AppX dependency package $archive does not contain a valid framework AppxManifest.xml.",
            )
        if (!architectureMatchesRuntime(dependency.processorArchitecture, runtimeIdentifier)) {
            throw IllegalArgumentException(
                "Configured AppX dependency package $archive targets processor architecture " +
                    "'${dependency.processorArchitecture}', but the selected runtime is '$runtimeIdentifier'.",
            )
        }
        return dependency
    }

    private fun readPackageDependencies(applicationPackage: Path): List<RequiredPackageDependency> {
        if (!applicationPackage.isRegularFile()) {
            throw IllegalArgumentException("Application package does not exist: $applicationPackage")
        }
        val document = runCatching {
            ZipFile(applicationPackage.toFile()).use { zip ->
                val entry = zip.getEntry("AppxManifest.xml")
                    ?: throw IllegalArgumentException(
                        "Application package $applicationPackage does not contain AppxManifest.xml.",
                    )
                zip.getInputStream(entry).use { input ->
                    secureDocumentBuilderFactory().newDocumentBuilder().parse(input)
                }
            }
        }.getOrElse { error ->
            if (error is IllegalArgumentException) throw error
            throw IllegalArgumentException("Cannot read application manifest from $applicationPackage: ${error.message}", error)
        }
        val dependencyElements = document.documentElement
            ?.childElements("Dependencies")
            ?.flatMap { dependencies -> dependencies.childElements(PACKAGE_DEPENDENCY_NAME) }
            .orEmpty()
        return dependencyElements.map { dependency ->
            val name = dependency.getAttribute("Name").trim()
            val minVersion = normalizeVersion(dependency.getAttribute("MinVersion"))
            val publisher = dependency.getAttribute("Publisher").trim()
            if (name.isBlank() || minVersion == null || publisher.isBlank()) {
                throw IllegalArgumentException(
                    "Application package $applicationPackage contains an invalid PackageDependency.",
                )
            }
            RequiredPackageDependency(
                name = name,
                minVersion = minVersion,
                publisher = publisher,
                processorArchitecture = dependency.getAttribute("ProcessorArchitecture")
                    .trim()
                    .takeIf(String::isNotBlank),
            )
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
                val processorArchitecture = normalizePackageArchitecture(identity.getAttribute("ProcessorArchitecture"))
                if (name.isBlank() || version == null || publisher.isBlank() || processorArchitecture == null) null
                else PackageDependency(name, version, publisher, processorArchitecture)
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

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun versionParts(value: String): List<Int> =
        value.split('.').map { part -> part.toIntOrNull() ?: -1 }

    private fun normalizePackageArchitecture(value: String): String? = when (value.trim().lowercase()) {
        "x86" -> "x86"
        "x64", "amd64" -> "x64"
        "arm64" -> "arm64"
        "neutral" -> "neutral"
        else -> null
    }

    private fun architectureMatchesRuntime(packageArchitecture: String, runtimeIdentifier: String): Boolean {
        val actual = normalizePackageArchitecture(packageArchitecture) ?: return false
        val expected = normalizePackageArchitecture(winAppRuntimeArchitecture(runtimeIdentifier)) ?: return false
        return actual == expected || actual == "neutral"
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

    internal data class PackageDependency(
        val name: String,
        val version: String,
        val publisher: String,
        val processorArchitecture: String,
    )

    private data class RequiredPackageDependency(
        val name: String,
        val minVersion: String,
        val publisher: String,
        val processorArchitecture: String?,
    )
}
