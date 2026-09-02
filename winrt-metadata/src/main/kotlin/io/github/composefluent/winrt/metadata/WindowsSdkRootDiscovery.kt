package io.github.composefluent.winrt.metadata

import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Resolves the installed Windows 10 SDK root using the same registry location as CsWinRT.
 *
 * The registry is authoritative for an installed SDK. The project environment variable is
 * retained for custom or portable SDK layouts and is considered only after registry candidates.
 */
object WindowsSdkRootDiscovery {
    const val environmentVariable: String = "KOTLIN_WINRT_WINDOWS_SDK_ROOT"
    const val registryKey: String = "HKLM\\SOFTWARE\\Microsoft\\Windows Kits\\Installed Roots"
    const val registryValue: String = "KitsRoot10"

    /**
     * Returns the first existing SDK root in registry, environment, and default-path order.
     */
    fun discover(
        environment: Map<String, String> = System.getenv(),
        registryRoots: List<String>? = null,
    ): Path? {
        val installedRoots = registryRoots ?: readRegistryKitsRoots()
        return candidateRoots(environment, installedRoots).firstOrNull { it.isDirectory() }
    }

    /**
     * Exposes the ordered candidates so callers that also resolve a version can preserve the
     * registry-first policy while falling through when a requested version is not installed.
     */
    fun candidateRoots(
        environment: Map<String, String> = System.getenv(),
        registryRoots: List<String> = emptyList(),
    ): List<Path> {
        val candidates = buildList {
            registryRoots.forEach { value -> addPath(value, environment) }
            addPath(environment.valueIgnoreCase(environmentVariable), environment)

            val programFilesX86 = environment.valueIgnoreCase("ProgramFiles(x86)")
                ?.trim()
                ?.trim('"')
                ?.takeIf(String::isNotBlank)
                ?: "C:\\Program Files (x86)"
            runCatching { Path.of(programFilesX86, "Windows Kits", "10") }
                .getOrNull()
                ?.let(::add)
        }
        return candidates.distinctBy(::pathKey)
    }

    /**
     * Returns candidates after querying the installed SDK registry entries.
     *
     * Registry access is intentionally explicit because it invokes reg.exe. Callers that run
     * during Gradle configuration should use [candidateRoots] with already-known values and
     * defer this method to task execution.
     */
    fun candidateRootsWithRegistry(environment: Map<String, String> = System.getenv()): List<Path> =
        candidateRoots(environment, readRegistryKitsRoots())

    /**
     * Reads the first non-empty KitsRoot10 value from the registry's available views.
     */
    fun readRegistryKitsRoot(): String? = readRegistryKitsRoots().firstOrNull()

    /**
     * Reads the installer view first, then the process-default view, matching CsWinRT's
     * KEY_WOW64_32KEY -> default lookup order.
     */
    fun readRegistryKitsRoots(): List<String> {
        if (!isWindowsHost()) {
            return emptyList()
        }

        return registryQueryViews
            .mapNotNull(::queryRegistryView)
            .distinct()
    }

    /** The registry view arguments tried in priority order. */
    val registryQueryViews: List<List<String>> = listOf(
        listOf("/reg:32"),
        emptyList(),
    )

    fun parseRegistryValueLine(line: String): String? {
        val tokens = line
            .replace("\uFEFF", "")
            .replace("\u0000", "")
            .trim()
            .split(WHITESPACE, limit = 3)
        if (tokens.size != 3 || !tokens[0].equals(registryValue, ignoreCase = true)) {
            return null
        }
        return tokens[2].trim().trim('"').takeIf(String::isNotEmpty)
    }

    /** Parses the text emitted by `reg.exe query` and returns its KitsRoot10 value. */
    fun parseRegistryOutput(output: String): String? =
        output.lineSequence()
            .mapNotNull(::parseRegistryValueLine)
            .firstOrNull()

    /** Decodes the common reg.exe output encodings before parsing the KitsRoot10 value. */
    fun parseRegistryOutput(bytes: ByteArray): String? {
        val detectedCharset = when {
            bytes.startsWith(UTF16_LE_BOM) -> Charsets.UTF_16LE
            bytes.startsWith(UTF16_BE_BOM) -> Charsets.UTF_16BE
            looksLikeUtf16LittleEndian(bytes) -> Charsets.UTF_16LE
            looksLikeUtf16BigEndian(bytes) -> Charsets.UTF_16BE
            else -> null
        }
        if (detectedCharset != null) {
            return parseRegistryOutput(bytes.toString(detectedCharset))
        }

        return registryOutputCharsets
            .asSequence()
            .map { charset -> bytes.toString(charset) }
            .mapNotNull { output ->
                parseRegistryOutput(output)?.let { value ->
                    value to decodingErrorScore(output)
                }
            }
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second })
            ?.first
    }

    private fun MutableList<Path>.addPath(value: String?, environment: Map<String, String>) {
        val path = value
            ?.trim()
            ?.trim('"')
            ?.expandEnvironmentVariables(environment)
            ?.takeIf(String::isNotEmpty)
            ?.let { runCatching { Path.of(it) }.getOrNull() }
        if (path != null) {
            add(path)
        }
    }

    private fun String.expandEnvironmentVariables(environment: Map<String, String>): String =
        replace(ENVIRONMENT_VARIABLE) { match ->
            val name = match.groupValues[1]
            environment.valueIgnoreCase(name)
                ?: match.value
        }

    private fun Map<String, String>.valueIgnoreCase(name: String): String? =
        entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

    private fun queryRegistryView(view: List<String>): String? =
        runCatching {
            val process = ProcessBuilder(
                "reg.exe",
                "query",
                registryKey,
                "/v",
                registryValue,
                *view.toTypedArray(),
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.use { it.readBytes() }
            if (process.waitFor() != 0) {
                return@runCatching null
            }
            parseRegistryOutput(output)
        }.getOrNull()

    private fun pathKey(path: Path): String =
        runCatching { path.toAbsolutePath().normalize().toString() }
            .getOrElse { path.toString() }
            .let { value -> if (isWindowsHost()) value.lowercase() else value }

    private fun isWindowsHost(): Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)

    private val registryOutputCharsets: List<Charset> = listOf(
        Charsets.UTF_8,
        Charsets.UTF_16LE,
        Charsets.UTF_16BE,
        Charset.defaultCharset(),
        runCatching { Charset.forName(System.getProperty("native.encoding")) }.getOrNull(),
    ).filterNotNull()
        .distinct()

    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private fun looksLikeUtf16LittleEndian(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()

    private fun looksLikeUtf16BigEndian(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[2] == 0.toByte()

    private fun decodingErrorScore(output: String): Int =
        output.count { character -> character == '\uFFFD' } * 100 +
            output.count { character -> character == '\u0000' }

    private val WHITESPACE = Regex("\\s+")
    private val ENVIRONMENT_VARIABLE = Regex("%([^%]+)%")
}
