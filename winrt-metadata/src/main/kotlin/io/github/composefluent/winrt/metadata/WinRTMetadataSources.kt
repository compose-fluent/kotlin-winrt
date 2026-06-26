package io.github.composefluent.winrt.metadata

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.streams.asSequence

enum class WinRTMetadataTarget {
    Net8,
    NetStandard20,
}

enum class WinRTWindowsSdkDiscoveryMode {
    EnvironmentOrDefaultRoot,
    WindowsHostRegistry,
}

data class WinRTMetadataFilter(
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
) {
    fun includes(name: String): Boolean {
        val included = include.isEmpty() || include.any { prefix -> name.startsWith(prefix) }
        val excluded = exclude.any { prefix -> name.startsWith(prefix) }
        return included && !excluded
    }

    fun includes(type: WinRTTypeDefinition): Boolean = includes(type.qualifiedName)

    fun normalized(): WinRTMetadataFilter =
        copy(
            include = include.map(String::trim).filter(String::isNotEmpty).toSortedSet(),
            exclude = exclude.map(String::trim).filter(String::isNotEmpty).toSortedSet(),
        )
}

data class WinRTMetadataProjectionContext(
    val sources: List<WinRTMetadataSource>,
    val outputFolder: Path? = null,
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
    val excludedTypes: Set<String> = emptySet(),
    val additionExclude: Set<String> = emptySet(),
    val target: WinRTMetadataTarget = WinRTMetadataTarget.Net8,
    val component: Boolean = false,
    val internal: Boolean = false,
    val embedded: Boolean = false,
    val publicEnums: Boolean = false,
    val publicExclusiveTo: Boolean = false,
    val idicExclusiveTo: Boolean = false,
    val partialFactory: Boolean = false,
    val verbose: Boolean = false,
) {
    val filter: WinRTMetadataFilter
        get() = WinRTMetadataFilter(include, exclude).normalized()

    val additionFilter: WinRTMetadataFilter
        get() = WinRTMetadataFilter(include, additionExclude).normalized()

    fun resolveCache(): WinRTMetadataCache = WinRTMetadataSourceResolver.resolve(sources)

    fun load(): WinRTMetadataModel = resolveCache().load()
}

sealed interface WinRTMetadataSource {
    data class PathSource(val path: Path) : WinRTMetadataSource
    data object LocalMachine : WinRTMetadataSource
    data class WindowsSdk(
        val version: String? = null,
        val includeExtensions: Boolean = false,
        val sdkRoot: Path? = null,
        val discoveryMode: WinRTWindowsSdkDiscoveryMode = WinRTWindowsSdkDiscoveryMode.EnvironmentOrDefaultRoot,
    ) : WinRTMetadataSource
    data class NuGetPackage(
        val packagePath: Path,
    ) : WinRTMetadataSource
    data class NuGetPackageReference(
        val packageId: String,
        val version: String,
        val globalPackagesRoots: List<Path> = emptyList(),
    ) : WinRTMetadataSource

    companion object {
        fun path(path: Path): WinRTMetadataSource = PathSource(path)
        fun local(): WinRTMetadataSource = LocalMachine
        fun nugetPackage(packagePath: Path): WinRTMetadataSource = NuGetPackage(packagePath)
        fun nugetPackage(
            packageId: String,
            version: String,
            globalPackagesRoots: List<Path> = emptyList(),
        ): WinRTMetadataSource = NuGetPackageReference(packageId, version, globalPackagesRoots)
        fun windowsSdk(
            version: String? = null,
            includeExtensions: Boolean = false,
            sdkRoot: Path? = null,
            discoveryMode: WinRTWindowsSdkDiscoveryMode = WinRTWindowsSdkDiscoveryMode.EnvironmentOrDefaultRoot,
        ): WinRTMetadataSource =
            WindowsSdk(
                version = version,
                includeExtensions = includeExtensions,
                sdkRoot = sdkRoot,
                discoveryMode = discoveryMode,
            )

        fun parse(value: String): WinRTMetadataSource {
            if (value.startsWith("nuget:", ignoreCase = true)) {
                val spec = value.substringAfter(':')
                val identitySeparator = spec.lastIndexOf('@')
                if (identitySeparator > 0 && identitySeparator < spec.lastIndex) {
                    return nugetPackage(
                        packageId = spec.substring(0, identitySeparator),
                        version = spec.substring(identitySeparator + 1),
                    )
                }
                return nugetPackage(Path.of(spec))
            }
            if (value == "local") {
                return local()
            }
            if (value == "sdk" || value == "sdk+") {
                return windowsSdk(includeExtensions = value.endsWith("+"))
            }
            val versionMatch = WINDOWS_SDK_VERSION_WITH_OPTIONAL_EXTENSIONS.matchEntire(value)
            if (versionMatch != null) {
                return windowsSdk(
                    version = versionMatch.groupValues[1],
                    includeExtensions = value.endsWith("+"),
                )
            }
            return path(Path.of(value))
        }

        fun parseInputs(values: List<String>): List<WinRTMetadataSource> =
            expandResponseFileArguments(values).map(::parse)

        fun parseInputs(vararg values: String): List<WinRTMetadataSource> = parseInputs(values.toList())

        private fun expandResponseFileArguments(values: List<String>): List<String> =
            values.flatMap { value -> expandResponseFileArgument(value, emptySet()) }

        private fun expandResponseFileArgument(value: String, responseStack: Set<Path>): List<String> {
            if (!value.startsWith("@") || value.length <= 1) {
                return listOf(value)
            }

            val responsePath = Path.of(value.drop(1))
            val extension = responsePath.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            require(!responsePath.isDirectory() && extension !in setOf("winmd", "dll", "exe")) {
                "'@' is reserved for response files"
            }
            val responseKey = responsePath.toAbsolutePath().normalize()
            require(responseKey !in responseStack) {
                "Response file '$responsePath' recursively includes itself."
            }
            return tokenizeResponseFile(responsePath.readText())
                .flatMap { token -> expandResponseFileArgument(token, responseStack + responseKey) }
        }

        private fun tokenizeResponseFile(content: String): List<String> {
            return content
                .lineSequence()
                .flatMap(::tokenizeResponseFileLine)
                .toList()
        }

        private fun tokenizeResponseFileLine(line: String): Sequence<String> = sequence {
            if (line.firstOrNull() == '#') {
                return@sequence
            }
            var cursor = 0
            var firstArgument = true
            var inQuotes = false
            var argument = StringBuilder()
            while (true) {
                while (cursor < line.length && (line[cursor] == ' ' || line[cursor] == '\t')) {
                    cursor += 1
                }
                if (!firstArgument) {
                    yield(argument.toString())
                    argument = StringBuilder()
                }
                if (cursor >= line.length) {
                    break
                }
                while (true) {
                    var copyCharacter = true
                    var backslashCount = 0
                    while (cursor < line.length && line[cursor] == '\\') {
                        cursor += 1
                        backslashCount += 1
                    }
                    if (cursor < line.length && line[cursor] == '"') {
                        if (backslashCount % 2 == 0) {
                            if (inQuotes && cursor + 1 < line.length && line[cursor + 1] == '"') {
                                cursor += 1
                            } else {
                                copyCharacter = false
                                inQuotes = !inQuotes
                            }
                        }
                        backslashCount /= 2
                    }
                    repeat(backslashCount) { argument.append('\\') }
                    if (cursor >= line.length || (!inQuotes && (line[cursor] == ' ' || line[cursor] == '\t'))) {
                        break
                    }
                    if (copyCharacter) {
                        argument.append(line[cursor])
                    }
                    cursor += 1
                }
                firstArgument = false
            }
        }

        private val WINDOWS_SDK_VERSION_WITH_OPTIONAL_EXTENSIONS =
            Regex("""(\d+\.\d+\.\d+\.\d+)\+?""")
    }
}

data class WinRTNuGetPackageIdentity(
    val packageId: String,
    val version: String,
) {
    val normalizedPackageId: String = packageId.trim()
    val normalizedVersion: String = WinRTNuGetPackageResolver.normalizeVersion(version)

    init {
        require(normalizedPackageId.isNotEmpty()) { "NuGet package id must not be blank." }
        require(normalizedVersion.isNotEmpty()) { "NuGet package version must not be blank." }
    }

    override fun toString(): String = "$normalizedPackageId@$normalizedVersion"
}

data class WinRTNuGetResolvedPackage(
    val identity: WinRTNuGetPackageIdentity,
    val packageRoot: Path,
    val nuspecPath: Path?,
    val dependencies: List<WinRTNuGetPackageIdentity>,
)

/**
 * Resolver for packages already restored by Microsoft NuGet tooling.
 *
 * This intentionally follows NuGet global-packages layout semantics instead of inventing a
 * kotlin-winrt cache. Restore/download remains a plugin concern; metadata only consumes the
 * package roots that NuGet CLI/MSBuild/dotnet restore place in the global packages folder.
 */
object WinRTNuGetPackageResolver {
    fun normalizeVersion(value: String): String {
        val trimmed = value.trim()
        return if (
            trimmed.length > 2 &&
            ',' !in trimmed &&
            ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                (trimmed.startsWith("(") && trimmed.endsWith(")")))
        ) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else {
            trimmed
        }
    }

    fun parseNuGetGlobalPackagesOutput(output: String): List<Path> =
        output.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("global-packages:", ignoreCase = true) }
            .map { it.substringAfter(':').trim().trim('"') }
            .filter(String::isNotEmpty)
            .map(Path::of)
            .toList()

    fun defaultGlobalPackagesRoot(
        environment: Map<String, String> = System.getenv(),
        userHome: String? = System.getProperty("user.home"),
    ): Path {
        environment["NUGET_PACKAGES"]
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.let { return it }

        require(!userHome.isNullOrBlank()) {
            "Cannot resolve NuGet global-packages root because user.home is not set and NUGET_PACKAGES is empty."
        }
        return Path.of(userHome, ".nuget", "packages")
    }

    fun globalPackagesRoots(
        explicitRoots: List<Path> = emptyList(),
        nugetLocalsOutput: String? = null,
        environment: Map<String, String> = System.getenv(),
        userHome: String? = System.getProperty("user.home"),
    ): List<Path> {
        val roots = explicitRoots +
            nugetLocalsOutput.orEmpty().let(::parseNuGetGlobalPackagesOutput) +
            defaultGlobalPackagesRoot(environment, userHome)
        return roots
            .map { it.toAbsolutePath().normalize() }
            .distinctBy { nuGetCanonicalPathKey(it) }
    }

    fun packageRoot(
        identity: WinRTNuGetPackageIdentity,
        globalPackagesRoots: List<Path> = globalPackagesRoots(),
    ): Path {
        val packageDirectoryName = identity.normalizedPackageId.lowercase()
        val versionCandidates = listOf(identity.normalizedVersion, identity.normalizedVersion.lowercase()).distinct()
        for (root in globalPackagesRoots) {
            val packageRoot = root.resolve(packageDirectoryName)
            for (version in versionCandidates) {
                val candidate = packageRoot.resolve(version)
                if (candidate.isDirectory()) {
                    return nuGetCanonicalizePath(candidate)
                }
            }
        }
        throw IllegalArgumentException(
            "NuGet package ${identity.normalizedPackageId}@${identity.normalizedVersion} was not found in global packages roots: " +
                globalPackagesRoots.joinToString(),
        )
    }

    fun resolve(
        identity: WinRTNuGetPackageIdentity,
        globalPackagesRoots: List<Path> = globalPackagesRoots(),
    ): WinRTNuGetResolvedPackage {
        val root = packageRoot(identity, globalPackagesRoots)
        return resolvePackageRoot(root, identity)
    }

    fun resolvePackageRoot(
        packageRoot: Path,
        identityOverride: WinRTNuGetPackageIdentity? = null,
    ): WinRTNuGetResolvedPackage {
        val root = nuGetCanonicalizePath(packageRoot)
        val identity = identityOverride ?: packageIdentity(root)
        return WinRTNuGetResolvedPackage(
            identity = identity,
            packageRoot = root,
            nuspecPath = findNuspec(root),
            dependencies = dependencies(root),
        )
    }

    fun resolveClosure(
        rootIdentity: WinRTNuGetPackageIdentity,
        globalPackagesRoots: List<Path> = globalPackagesRoots(),
    ): List<WinRTNuGetResolvedPackage> {
        val queue = ArrayDeque(listOf(rootIdentity))
        val visited = linkedSetOf<String>()
        val resolved = mutableListOf<WinRTNuGetResolvedPackage>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentResolved = resolve(current, globalPackagesRoots)
            val key = nuGetCanonicalPathKey(currentResolved.packageRoot)
            if (!visited.add(key)) {
                continue
            }
            resolved += currentResolved
            currentResolved.dependencies.forEach(queue::add)
        }
        return resolved
    }

    fun dependencies(packageRoot: Path): List<WinRTNuGetPackageIdentity> {
        val nuspec = findNuspec(packageRoot) ?: return emptyList()
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val document = builder.parse(nuspec.toFile())
        val dependencyNodes = document.getElementsByTagNameNS("*", "dependency")
            .takeIf { it.length > 0 }
            ?: document.getElementsByTagName("dependency")
        return buildList {
            for (index in 0 until dependencyNodes.length) {
                val element = dependencyNodes.item(index) as? org.w3c.dom.Element ?: continue
                val id = element.getAttribute("id").takeIf(String::isNotBlank) ?: continue
                val version = element.getAttribute("version").takeIf(String::isNotBlank) ?: continue
                add(WinRTNuGetPackageIdentity(id, version))
            }
        }.distinctBy { "${it.normalizedPackageId.lowercase()}:${it.normalizedVersion.lowercase()}" }
    }

    private fun packageIdentity(packageRoot: Path): WinRTNuGetPackageIdentity {
        val nuspec = findNuspec(packageRoot) ?: return packageIdentityFromInstallDirectory(packageRoot)
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val document = builder.parse(nuspec.toFile())
        val metadataNodes = document.getElementsByTagNameNS("*", "metadata")
            .takeIf { it.length > 0 }
            ?: document.getElementsByTagName("metadata")
        val metadata = (0 until metadataNodes.length)
            .mapNotNull { metadataNodes.item(it) as? org.w3c.dom.Element }
            .firstOrNull()
            ?: throw IllegalArgumentException("NuGet package '$packageRoot' has no metadata node in ${nuspec.fileName}.")
        return WinRTNuGetPackageIdentity(
            packageId = metadata.childText("id"),
            version = metadata.childText("version"),
        )
    }

    private fun packageIdentityFromInstallDirectory(packageRoot: Path): WinRTNuGetPackageIdentity {
        val packageName = Files.list(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .map { it.fileName.toString() }
                .firstOrNull { it.endsWith(".nupkg", ignoreCase = true) }
                ?.removeSuffix(".nupkg")
        } ?: packageRoot.fileName.toString()
        return parsePackageIdentityFromInstallName(packageName)
            ?: throw IllegalArgumentException("NuGet package root '$packageRoot' does not contain a .nuspec file and its directory name is not '<id>.<version>'.")
    }

    internal fun parsePackageIdentityFromInstallName(packageName: String): WinRTNuGetPackageIdentity? {
        val versionPattern = Regex("""^[0-9]+(?:\.[0-9]+){1,3}(?:[-+][A-Za-z0-9.-]+)?$""")
        return packageName.indices
            .asSequence()
            .filter { packageName[it] == '.' && it > 0 && it < packageName.lastIndex }
            .mapNotNull { separator ->
                val packageId = packageName.substring(0, separator)
                val version = packageName.substring(separator + 1)
                if (versionPattern.matches(version)) {
                    WinRTNuGetPackageIdentity(packageId, version)
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    private fun findNuspec(packageRoot: Path): Path? =
        Files.list(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .firstOrNull { it.name.endsWith(".nuspec", ignoreCase = true) }
        }

    private fun nuGetCanonicalizePath(path: Path): Path =
        runCatching { path.toAbsolutePath().normalize().toRealPath() }
            .getOrElse { runCatching { path.toAbsolutePath().normalize() }.getOrElse { path.normalize() } }

    private fun nuGetCanonicalPathKey(path: Path): String =
        nuGetCanonicalizePath(path).toString().let { value ->
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) value.lowercase() else value
        }
}

private fun org.w3c.dom.Element.childText(name: String): String {
    val nodes = getElementsByTagNameNS("*", name)
        .takeIf { it.length > 0 }
        ?: getElementsByTagName(name)
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? org.w3c.dom.Element ?: continue
        return element.textContent.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("NuGet nuspec <$name> must not be blank.")
    }
    throw IllegalArgumentException("NuGet nuspec is missing <$name>.")
}

enum class WinRTPackageAssetKind {
    Winmd,
    Resource,
    Native,
    Other,
}

data class WinRTPackageAsset(
    val packagePath: Path,
    val relativePath: String,
    val kind: WinRTPackageAssetKind,
    val extractedPath: Path? = null,
)

enum class WinRTMetadataSourceKind {
    Path,
    LocalMachine,
    WindowsSdk,
    NuGetPackage,
}

data class WinRTResolvedMetadataFile(
    val file: Path,
    val sourceKind: WinRTMetadataSourceKind,
    val sourceDescription: String,
)

data class WinRTMetadataCache(
    val files: List<Path>,
    val resolvedFiles: List<WinRTResolvedMetadataFile> = files.map {
        WinRTResolvedMetadataFile(
            file = it,
            sourceKind = WinRTMetadataSourceKind.Path,
            sourceDescription = it.toString(),
        )
    },
    val packageAssets: List<WinRTPackageAsset> = emptyList(),
) {
    fun load(): WinRTMetadataModel = WinRTMetadataLoader.loadDiscoveredFiles(files)
}

object WinRTMetadataSourceResolver {
    fun resolve(sources: List<WinRTMetadataSource>): WinRTMetadataCache {
        val resolvedSources = sources.map(::resolveSource)
        val seenFiles = linkedSetOf<String>()
        val resolvedFiles = resolvedSources
            .asSequence()
            .flatMap { source ->
                source.files
                    .asSequence()
                    .map { resolved -> resolved.copy(file = canonicalizePath(resolved.file)) }
                    .sortedBy { resolved -> canonicalPathKey(resolved.file) }
            }
            .map { resolved -> resolved.copy(file = canonicalizePath(resolved.file)) }
            .filter { resolved -> seenFiles.add(canonicalPathKey(resolved.file)) }
            .toList()
        val files = resolvedFiles.map(WinRTResolvedMetadataFile::file)
        val packageAssets = resolvedSources
            .flatMap(ResolvedMetadataSource::packageAssets)
            .distinctBy { asset -> "${canonicalPathKey(asset.packagePath)}:${asset.relativePath}" }
            .sortedWith(compareBy({ canonicalPathKey(it.packagePath) }, { it.relativePath }))
        return WinRTMetadataCache(files, resolvedFiles, packageAssets)
    }

    fun resolve(vararg sources: WinRTMetadataSource): WinRTMetadataCache = resolve(sources.toList())

    internal fun resolvePathInputs(paths: List<Path>): WinRTMetadataCache =
        resolve(paths.map(WinRTMetadataSource::path))

    private fun resolveSource(source: WinRTMetadataSource): ResolvedMetadataSource = when (source) {
        WinRTMetadataSource.LocalMachine -> ResolvedMetadataSource(
            localWinMetadataFiles().map { file ->
                WinRTResolvedMetadataFile(file, WinRTMetadataSourceKind.LocalMachine, "local")
            },
        )
        is WinRTMetadataSource.PathSource -> ResolvedMetadataSource(
            discoverPathSource(source.path).map { file ->
                WinRTResolvedMetadataFile(file, WinRTMetadataSourceKind.Path, source.path.toString())
            },
        )
        is WinRTMetadataSource.WindowsSdk -> ResolvedMetadataSource(
            windowsSdkFiles(source).map { file ->
                WinRTResolvedMetadataFile(
                    file = file,
                    sourceKind = WinRTMetadataSourceKind.WindowsSdk,
                    sourceDescription = buildString {
                        append("sdk")
                        source.version?.let { append(":").append(it) }
                        if (source.includeExtensions) append("+")
                    },
                )
            },
        )
        is WinRTMetadataSource.NuGetPackage -> resolveNuGetPackage(source.packagePath)
        is WinRTMetadataSource.NuGetPackageReference -> resolveNuGetPackageReference(source)
    }

    private fun discoverPathSource(path: Path): List<Path> = when {
        path.isDirectory() -> Files.list(path).use { stream ->
            stream.asSequence()
                .filter(Files::isRegularFile)
                .filter(::looksLikeCliMetadataCandidate)
                .toList()
        }

        path.isRegularFile() -> listOf(path)
        else -> throw IllegalArgumentException("Metadata path '$path' is not a file or directory.")
    }

    private fun localWinMetadataFiles(): List<Path> {
        val windir = System.getenv("windir") ?: System.getenv("WINDIR")
            ?: throw IllegalArgumentException("Cannot resolve 'local' metadata source because WINDIR is not set.")
        val metadataDirectory = Path.of(
            windir,
            if (is64BitProcess()) "System32" else "SysNative",
            "WinMetadata",
        )
        return discoverPathSource(metadataDirectory)
    }

    private fun windowsSdkFiles(source: WinRTMetadataSource.WindowsSdk): List<Path> {
        val sdkRoot = source.sdkRoot ?: locateWindowsSdkRoot(source.discoveryMode)
        val version = source.version ?: latestWindowsSdkVersion(sdkRoot)
        val files = linkedSetOf<Path>()
        val platformXml = sdkRoot.resolve("Platforms").resolve("UAP").resolve(version).resolve("Platform.xml")
        addFilesFromApiContractXml(files, sdkRoot, version, platformXml)

        if (source.includeExtensions) {
            val extensionSdks = sdkRoot.resolve("Extension SDKs")
            if (extensionSdks.isDirectory()) {
                Files.list(extensionSdks).use { stream ->
                    stream.asSequence()
                        .map { it.resolve(version).resolve("SDKManifest.xml") }
                        .filter { it.isRegularFile() }
                        .sortedWith(compareBy { canonicalPathKey(it) })
                        .forEach { manifest -> addFilesFromApiContractXml(files, sdkRoot, version, manifest) }
                }
            }
        }

        return files.toList()
    }

    private fun resolveNuGetPackage(packagePath: Path): ResolvedMetadataSource {
        val canonicalPackagePath = canonicalizePath(packagePath)
        return when {
            canonicalPackagePath.isDirectory() -> resolveNuGetPackageDirectory(canonicalPackagePath)
            canonicalPackagePath.isRegularFile() && canonicalPackagePath.name.endsWith(".nupkg", ignoreCase = true) ->
                resolveNuGetPackageArchive(canonicalPackagePath)
            else -> throw IllegalArgumentException("NuGet package '$packagePath' is not a package directory or .nupkg file.")
        }
    }

    private fun resolveNuGetPackageReference(source: WinRTMetadataSource.NuGetPackageReference): ResolvedMetadataSource {
        val packages = WinRTNuGetPackageResolver.resolveClosure(
            rootIdentity = WinRTNuGetPackageIdentity(source.packageId, source.version),
            globalPackagesRoots = WinRTNuGetPackageResolver.globalPackagesRoots(explicitRoots = source.globalPackagesRoots),
        )
        return packages
            .map { resolved -> resolveNuGetPackageDirectory(resolved.packageRoot) }
            .fold(ResolvedMetadataSource(emptyList())) { left, right ->
                ResolvedMetadataSource(
                    files = left.files + right.files,
                    packageAssets = left.packageAssets + right.packageAssets,
                )
            }
    }

    private fun resolveNuGetPackageDirectory(packagePath: Path): ResolvedMetadataSource {
        val assets = Files.walk(packagePath).use { stream ->
            stream.asSequence()
                .filter(Files::isRegularFile)
                .map { file ->
                    WinRTPackageAsset(
                        packagePath = packagePath,
                        relativePath = packagePath.relativize(file).toString().replace('\\', '/'),
                        kind = packageAssetKind(file.name),
                        extractedPath = file,
                    )
                }
                .toList()
        }
        return ResolvedMetadataSource(
            files = assets.filter { it.kind == WinRTPackageAssetKind.Winmd }.mapNotNull { asset ->
                asset.extractedPath?.let {
                    WinRTResolvedMetadataFile(it, WinRTMetadataSourceKind.NuGetPackage, packagePath.toString())
                }
            },
            packageAssets = assets,
        )
    }

    private fun resolveNuGetPackageArchive(packagePath: Path): ResolvedMetadataSource {
        val extractRoot = Files.createTempDirectory("kotlin-winrt-nuget-")
        val assets = ZipFile(packagePath.toFile()).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { entry ->
                    val kind = packageAssetKind(entry.name)
                    val extractedPath = extractRoot.resolve(entry.name).normalize()
                    require(extractedPath.startsWith(extractRoot)) {
                        "NuGet package '$packagePath' contains an invalid relative path '${entry.name}'."
                    }
                    extractedPath.parent?.createDirectories()
                    extractedPath.writeBytes(zip.getInputStream(entry).use { it.readAllBytes() })
                    WinRTPackageAsset(
                        packagePath = packagePath,
                        relativePath = entry.name,
                        kind = kind,
                        extractedPath = extractedPath,
                    )
                }
                .toList()
        }
        return ResolvedMetadataSource(
            files = assets.filter { it.kind == WinRTPackageAssetKind.Winmd }.mapNotNull { asset ->
                asset.extractedPath?.let {
                    WinRTResolvedMetadataFile(it, WinRTMetadataSourceKind.NuGetPackage, packagePath.toString())
                }
            },
            packageAssets = assets,
        )
    }

    private fun packageAssetKind(path: String): WinRTPackageAssetKind {
        val normalized = path.replace('\\', '/').lowercase()
        return when {
            normalized.endsWith(".winmd") -> WinRTPackageAssetKind.Winmd
            normalized.startsWith("resources/") ||
                normalized.contains("/resources/") ||
                normalized.contains(".resources/") ||
                normalized.endsWith(".pri") ||
                normalized.endsWith(".xbf") ||
                normalized.endsWith(".resw") ->
                WinRTPackageAssetKind.Resource
            normalized.startsWith("runtimes/") || normalized.endsWith(".dll") -> WinRTPackageAssetKind.Native
            else -> WinRTPackageAssetKind.Other
        }
    }

    private fun locateWindowsSdkRoot(discoveryMode: WinRTWindowsSdkDiscoveryMode): Path {
        if (discoveryMode == WinRTWindowsSdkDiscoveryMode.WindowsHostRegistry) {
            throw IllegalArgumentException(
                "Windows SDK registry/module-version discovery is a Windows-host integration boundary; provide sdkRoot or use EnvironmentOrDefaultRoot discovery.",
            )
        }

        System.getenv("KOTLIN_WINRT_WINDOWS_SDK_ROOT")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.takeIf { it.isDirectory() }
            ?.let { return it }

        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        val root = Path.of(programFilesX86, "Windows Kits", "10")
        if (root.isDirectory()) {
            return root
        }
        throw IllegalArgumentException(
            "Could not find the Windows SDK root. Set KOTLIN_WINRT_WINDOWS_SDK_ROOT or install Windows Kits 10.",
        )
    }

    private fun latestWindowsSdkVersion(sdkRoot: Path): String {
        val platforms = sdkRoot.resolve("Platforms").resolve("UAP")
        if (!platforms.isDirectory()) {
            throw IllegalArgumentException("Could not find Windows SDK UAP platforms under $platforms.")
        }
        return Files.list(platforms).use { stream ->
            stream.asSequence()
                .filter { it.resolve("Platform.xml").isRegularFile() }
                .map { it.name }
                .filter { WINDOWS_SDK_VERSION.matches(it) }
                .sortedWith(::compareSdkVersions)
                .lastOrNull()
        } ?: throw IllegalArgumentException("Could not find a Windows SDK version with Platform.xml under $platforms.")
    }

    private fun addFilesFromApiContractXml(
        files: MutableSet<Path>,
        sdkRoot: Path,
        sdkVersion: String,
        xmlPath: Path,
    ) {
        if (!xmlPath.isRegularFile()) {
            throw IllegalArgumentException("Could not read the Windows SDK metadata contract file at $xmlPath.")
        }
        val documentBuilder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val document = documentBuilder.parse(xmlPath.toFile())
        val apiContracts = document.getElementsByTagNameNS("*", "ApiContract")
            .takeIf { it.length > 0 }
            ?: document.getElementsByTagName("ApiContract")
        for (index in 0 until apiContracts.length) {
            val node = apiContracts.item(index)
            val attributes = node.attributes
            val name = attributes.getNamedItem("name")?.nodeValue
                ?: throw IllegalArgumentException("ApiContract in $xmlPath is missing a name attribute.")
            val version = attributes.getNamedItem("version")?.nodeValue
                ?: throw IllegalArgumentException("ApiContract '$name' in $xmlPath is missing a version attribute.")
            val file = sdkRoot
                .resolve("References")
                .resolve(sdkVersion)
                .resolve(name)
                .resolve(version)
                .resolve("$name.winmd")
            if (!file.isRegularFile()) {
                throw IllegalArgumentException("ApiContract '$name' in $xmlPath references missing WinMD $file.")
            }
            files.add(file)
        }
    }

    internal fun looksLikeCliMetadataCandidate(path: Path): Boolean =
        path.name.substringAfterLast('.', missingDelimiterValue = "").lowercase() in setOf("winmd", "dll", "exe")

    internal fun canonicalizePath(path: Path): Path =
        runCatching { path.toAbsolutePath().normalize().toRealPath() }
            .getOrElse { runCatching { path.toAbsolutePath().normalize() }.getOrElse { path.normalize() } }

    internal fun canonicalPathKey(path: Path): String =
        canonicalizePath(path).toString().let { value ->
            if (isWindows()) value.lowercase() else value
        }

    private fun compareSdkVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map(String::toInt)
        val rightParts = right.split('.').map(String::toInt)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val comparison = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) {
                return comparison
            }
        }
        return 0
    }

    private fun is64BitProcess(): Boolean =
        System.getProperty("os.arch").contains("64")

    private fun isWindows(): Boolean =
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    private val WINDOWS_SDK_VERSION = Regex("""\d+\.\d+\.\d+\.\d+""")
}

private data class ResolvedMetadataSource(
    val files: List<WinRTResolvedMetadataFile>,
    val packageAssets: List<WinRTPackageAsset> = emptyList(),
)
