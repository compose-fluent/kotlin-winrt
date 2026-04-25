package io.github.kitectlab.winrt.metadata

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

enum class WinRtMetadataTarget {
    Net8,
    NetStandard20,
}

data class WinRtMetadataFilter(
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
) {
    fun includes(name: String): Boolean {
        val included = include.isEmpty() || include.any { prefix -> name.startsWith(prefix) }
        val excluded = exclude.any { prefix -> name.startsWith(prefix) }
        return included && !excluded
    }

    fun includes(type: WinRtTypeDefinition): Boolean = includes(type.qualifiedName)

    fun normalized(): WinRtMetadataFilter =
        copy(
            include = include.map(String::trim).filter(String::isNotEmpty).toSortedSet(),
            exclude = exclude.map(String::trim).filter(String::isNotEmpty).toSortedSet(),
        )
}

data class WinRtMetadataProjectionContext(
    val sources: List<WinRtMetadataSource>,
    val outputFolder: Path? = null,
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
    val additionExclude: Set<String> = emptySet(),
    val target: WinRtMetadataTarget = WinRtMetadataTarget.Net8,
    val component: Boolean = false,
    val internal: Boolean = false,
    val embedded: Boolean = false,
    val publicEnums: Boolean = false,
    val publicExclusiveTo: Boolean = false,
    val idicExclusiveTo: Boolean = false,
    val partialFactory: Boolean = false,
    val verbose: Boolean = false,
) {
    val filter: WinRtMetadataFilter
        get() = WinRtMetadataFilter(include, exclude).normalized()

    val additionFilter: WinRtMetadataFilter
        get() = WinRtMetadataFilter(include, additionExclude).normalized()

    fun resolveCache(): WinRtMetadataCache = WinRtMetadataSourceResolver.resolve(sources)

    fun load(): WinRtMetadataModel = resolveCache().load()
}

sealed interface WinRtMetadataSource {
    data class PathSource(val path: Path) : WinRtMetadataSource
    data object LocalMachine : WinRtMetadataSource
    data class WindowsSdk(
        val version: String? = null,
        val includeExtensions: Boolean = false,
        val sdkRoot: Path? = null,
    ) : WinRtMetadataSource

    companion object {
        fun path(path: Path): WinRtMetadataSource = PathSource(path)
        fun local(): WinRtMetadataSource = LocalMachine
        fun windowsSdk(
            version: String? = null,
            includeExtensions: Boolean = false,
            sdkRoot: Path? = null,
        ): WinRtMetadataSource =
            WindowsSdk(version = version, includeExtensions = includeExtensions, sdkRoot = sdkRoot)

        fun parse(value: String): WinRtMetadataSource {
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

        fun parseInputs(values: List<String>): List<WinRtMetadataSource> =
            expandResponseFileArguments(values).map(::parse)

        fun parseInputs(vararg values: String): List<WinRtMetadataSource> = parseInputs(values.toList())

        private fun expandResponseFileArguments(values: List<String>): List<String> =
            values.flatMap { value ->
                if (value.startsWith("@") && value.length > 1) {
                    tokenizeResponseFile(Path.of(value.drop(1)).readText())
                } else {
                    listOf(value)
                }
            }

        private fun tokenizeResponseFile(content: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            fun flush() {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }
            content.forEach { char ->
                when {
                    quote != null && char == quote -> quote = null
                    quote != null -> current.append(char)
                    char == '"' || char == '\'' -> quote = char
                    char.isWhitespace() -> flush()
                    else -> current.append(char)
                }
            }
            flush()
            require(quote == null) { "Response file contains an unterminated quoted argument." }
            return tokens
        }

        private val WINDOWS_SDK_VERSION_WITH_OPTIONAL_EXTENSIONS =
            Regex("""(\d+\.\d+\.\d+\.\d+)\+?""")
    }
}

data class WinRtMetadataCache(
    val files: List<Path>,
) {
    fun load(): WinRtMetadataModel = WinRtMetadataLoader.loadDiscoveredFiles(files)
}

object WinRtMetadataSourceResolver {
    fun resolve(sources: List<WinRtMetadataSource>): WinRtMetadataCache {
        val files = sources
            .asSequence()
            .flatMap { source -> resolveSource(source).asSequence() }
            .map(::canonicalizePath)
            .distinctBy(::canonicalPathKey)
            .sortedBy(::canonicalPathKey)
            .toList()
        return WinRtMetadataCache(files)
    }

    fun resolve(vararg sources: WinRtMetadataSource): WinRtMetadataCache = resolve(sources.toList())

    internal fun resolvePathInputs(paths: List<Path>): WinRtMetadataCache =
        resolve(paths.map(WinRtMetadataSource::path))

    private fun resolveSource(source: WinRtMetadataSource): List<Path> = when (source) {
        WinRtMetadataSource.LocalMachine -> localWinMetadataFiles()
        is WinRtMetadataSource.PathSource -> discoverPathSource(source.path)
        is WinRtMetadataSource.WindowsSdk -> windowsSdkFiles(source)
    }

    private fun discoverPathSource(path: Path): List<Path> = when {
        path.isDirectory() -> Files.walk(path).use { stream ->
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

    private fun windowsSdkFiles(source: WinRtMetadataSource.WindowsSdk): List<Path> {
        val sdkRoot = source.sdkRoot ?: locateWindowsSdkRoot()
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
                        .forEach { manifest -> addFilesFromApiContractXml(files, sdkRoot, version, manifest) }
                }
            }
        }

        return files.toList()
    }

    private fun locateWindowsSdkRoot(): Path {
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
