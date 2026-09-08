package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Path
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

internal data class PackagePayloadDecision(
    val source: Path,
    val target: Path,
    val origin: String,
    val overriddenSource: Path? = null,
    val isPriCompilerInput: Boolean = false,
)

internal object ApplicationPackagePayloadWriter {
    fun copyPackagePayloads(projectRoot: Path, packageRoot: Path, items: Set<ApplicationPackageItem>) {
        items.asSequence()
            .filter { it.kind.isPackagePayload }
            .sortedBy { it.targetKey }
            .forEach { item ->
                GradleFileOperations.copyFile(item.target, packageRoot.resolve(item.target.relativeTo(projectRoot)))
            }
    }

    fun resolvePackagePayloads(
        conventionInputs: Collection<AppxResourceInput>,
        dependencyInputs: Collection<AppxResourceInput> = emptyList(),
        explicitPayloadFiles: Collection<Path>,
        rootPayloadFiles: Collection<Path>,
        projectRoot: Path?,
        targetPaths: Map<String, String>,
        excludedPaths: Set<String>,
    ): List<PackagePayloadDecision> {
        val selected = linkedMapOf<String, PackagePayloadDecision>()
        val excludedKeys = excludedPaths.mapTo(linkedSetOf(), ::normalizedSourceKey)
        val configuredTargets = targetPaths.entries.associate { (source, target) ->
            normalizedSourceKey(source) to target
        }
        val unresolvedConflicts = linkedMapOf<String, MutableList<PackagePayloadDecision>>()

        dependencyInputs
            .filter { it.source.isRegularFile() }
            .filterNot { it.relativePath.parent == null && it.relativePath.name.equals("AppxManifest.xml", ignoreCase = true) }
            .forEach { input ->
                addDecision(
                    selected,
                    PackagePayloadDecision(
                        input.source,
                        input.relativePath.toString().toSafeRelativePath("dependency AppX resource path"),
                        "dependency AppX resource",
                        isPriCompilerInput = input.isPriCompilerInput(),
                    ),
                    priority = DEPENDENCY_PRIORITY,
                    unresolvedConflicts = unresolvedConflicts,
                )
            }

        conventionInputs
            .filter { it.source.isRegularFile() }
            .filterNot { it.relativePath.parent == null && it.relativePath.name.equals("AppxManifest.xml", ignoreCase = true) }
            .forEach { input ->
                addDecision(
                    selected,
                    PackagePayloadDecision(
                        input.source,
                        input.relativePath.toString().toSafeRelativePath("AppX resource path"),
                        "appxResources",
                        isPriCompilerInput = input.isPriCompilerInput(),
                    ),
                    priority = CONVENTION_PRIORITY,
                    unresolvedConflicts = unresolvedConflicts,
                )
            }

        explicitPayloadFiles
            .map { it.toAbsolutePath().normalize() }
            .sortedBy { it.toString().lowercase() }
            .forEach { source ->
                if (normalizedSourceKey(source) in excludedKeys) return@forEach
                if (!Files.exists(source)) {
                    throw GradleException("Declared package payload does not exist: $source")
                }
                val explicitTarget = configuredTargets[normalizedSourceKey(source)]
                    ?.toSafeRelativePath("packagePayload target path")
                val files = if (source.isDirectory()) {
                    Files.walk(source).use { stream ->
                        stream.filter(Files::isRegularFile).sorted().toList()
                    }
                } else if (source.isRegularFile()) {
                    listOf(source)
                } else {
                    emptyList()
                }
                files.forEach { file ->
                    if (normalizedSourceKey(file) in excludedKeys) return@forEach
                    val target = if (explicitTarget != null) {
                        if (source.isDirectory()) explicitTarget.resolve(file.relativeTo(source)) else explicitTarget
                    } else {
                        file.defaultPackagePayloadTarget(
                            fallbackRoot = if (source.isDirectory()) source else (source.parent ?: source),
                            projectRoot = projectRoot,
                        )
                    }
                    addDecision(
                        selected,
                        PackagePayloadDecision(
                            file,
                            target.toString().toSafeRelativePath("packagePayload target path"),
                            "explicit packagePayload",
                        ),
                        priority = EXPLICIT_PRIORITY,
                        unresolvedConflicts = unresolvedConflicts,
                    )
                }
            }

        rootPayloadFiles
            .map { it.toAbsolutePath().normalize() }
            .sortedBy { it.toString().lowercase() }
            .forEach { source ->
                if (!source.isRegularFile()) {
                    throw GradleException("Declared root package payload must be a file: $source")
                }
                addDecision(
                    selected,
                    PackagePayloadDecision(source, Path.of(source.name), "selected executable"),
                    priority = ROOT_PRIORITY,
                    reserved = true,
                    unresolvedConflicts = unresolvedConflicts,
                )
            }

        unresolvedConflicts.forEach { (key, conflicts) ->
            val selectedDecision = selected[key]
            if (selectedDecision != null && selectedDecision.origin == conflicts.first().origin) {
                val sources = conflicts
                    .map { decision -> decision.source }
                    .distinct()
                    .joinToString(" and ")
                throw GradleException(
                    "Conflicting ${conflicts.first().origin} files target ${selectedDecision.target}: $sources",
                )
            }
        }

        return selected.values.sortedBy { it.target.toNormalizedPackagePathKey() }
    }

    /**
     * Validates the stable, package-root-relative report emitted during staging against an
     * unpacked package. The report is intentionally checked separately from manifest validation:
     * framework packages may provide files that are absent from the application package, while
     * every application-owned decision must be present here.
     */
    fun validateResolutionReport(report: Path, packageRoot: Path): List<String> {
        if (!Files.isRegularFile(report)) {
            return listOf("resource resolution report does not exist: $report")
        }
        val root = runCatching {
            Json.parseToJsonElement(Files.readString(report)).jsonObject
        }.getOrElse { error ->
            return listOf("resource resolution report could not be parsed: ${error.message}")
        }
        val errors = mutableListOf<String>()
        val schema = root["schemaVersion"]?.jsonPrimitive?.intOrNull
        if (schema != 1) {
            errors += "resource resolution report schemaVersion must be 1"
        }
        val packageRootRelative = root["packageRootRelative"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        if (packageRootRelative != true) {
            errors += "resource resolution report packageRootRelative must be true"
        }
        val entries = runCatching { root["entries"]?.jsonArray }.getOrNull()
        if (entries == null) {
            errors += "resource resolution report is missing entries"
            return errors
        }
        val normalizedRoot = packageRoot.toAbsolutePath().normalize()
        val targets = linkedSetOf<String>()
        entries.forEachIndexed { index, entry ->
            val objectEntry = entry as? JsonObject
            if (objectEntry == null) {
                errors += "resource resolution report entry[$index] is not an object"
                return@forEachIndexed
            }
            val rawTarget = objectEntry["target"]?.jsonPrimitive?.content.orEmpty()
            val target = runCatching { rawTarget.toSafeRelativePath("resource resolution report target") }
                .getOrElse { error ->
                    errors += "resource resolution report entry[$index] has an invalid target: ${error.message}"
                    return@forEachIndexed
                }
            if (target.toString().isBlank() || target == Path.of(".")) {
                errors += "resource resolution report entry[$index] has an empty target"
                return@forEachIndexed
            }
            val key = target.toString().replace('\\', '/').lowercase()
            if (!targets.add(key)) {
                errors += "resource resolution report contains duplicate target: $rawTarget"
                return@forEachIndexed
            }
            val resolved = normalizedRoot.resolve(target).normalize()
            if (!resolved.startsWith(normalizedRoot)) {
                errors += "resource resolution report target escapes the package root: $rawTarget"
            } else if (!resolved.isRegularFile()) {
                errors += "resource resolution report target is missing from the package: $rawTarget"
            }
            if (objectEntry["origin"]?.jsonPrimitive?.content.isNullOrBlank()) {
                errors += "resource resolution report entry[$index] is missing origin"
            }
            val expectedHash = objectEntry["sha256"]?.jsonPrimitive?.content.orEmpty()
            if (!SHA256_PATTERN.matches(expectedHash)) {
                errors += "resource resolution report entry[$index] has an invalid sha256: $rawTarget"
            } else if (resolved.isRegularFile()) {
                val actualHash = sha256(resolved)
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    errors += "resource resolution report target has a content hash mismatch: $rawTarget"
                }
            }
        }
        val generatedPriValue = root["generatedPri"]
        val generatedPri = runCatching { generatedPriValue?.jsonObject }.getOrElse {
            errors += "generated PRI record must be an object"
            null
        }
        if (generatedPri != null) {
            val rawTarget = generatedPri["target"]?.jsonPrimitive?.content.orEmpty()
            val target = runCatching { rawTarget.toSafeRelativePath("generated PRI target") }
                .getOrElse { error ->
                    errors += "generated PRI target is invalid: ${error.message}"
                    null
                }
            if (target != null) {
                if (target.toString().isBlank() || target == Path.of(".")) {
                    errors += "generated PRI target is empty"
                }
                if (!target.toString().replace('\\', '/').equals("resources.pri", ignoreCase = true)) {
                    errors += "generated PRI target must be resources.pri: $rawTarget"
                }
                val resolved = normalizedRoot.resolve(target).normalize()
                if (!resolved.startsWith(normalizedRoot)) {
                    errors += "generated PRI target escapes the package root: $rawTarget"
                } else if (!resolved.isRegularFile()) {
                    errors += "generated PRI target is missing from the package: $rawTarget"
                } else {
                    val expectedHash = generatedPri["sha256"]?.jsonPrimitive?.content.orEmpty()
                    if (!SHA256_PATTERN.matches(expectedHash)) {
                        errors += "generated PRI record is missing sha256: $rawTarget"
                    } else if (!sha256(resolved).equals(expectedHash, ignoreCase = true)) {
                        errors += "generated PRI target has a content hash mismatch: $rawTarget"
                    }
                }
            }
            val rawMappings = generatedPri["priMappings"]?.jsonArray
            if (rawMappings == null) {
                errors += "generated PRI record is missing structured mappings"
            } else {
                val mappings = rawMappings.mapIndexedNotNull { index, value ->
                    val mapping = value as? JsonObject
                    if (mapping == null) {
                        errors += "generated PRI mapping[$index] must be an object"
                        return@mapIndexedNotNull null
                    }
                    val resourceUri = mapping["resourceUri"]?.jsonPrimitive?.content.orEmpty()
                    val candidateType = mapping["candidateType"]?.jsonPrimitive?.content.orEmpty()
                    if (resourceUri.isBlank() || candidateType.isBlank()) {
                        errors += "generated PRI mapping[$index] is missing resourceUri or candidateType"
                        return@mapIndexedNotNull null
                    }
                    PriResourceMapping(
                        resourceUri = resourceUri,
                        candidateType = candidateType,
                        value = mapping["value"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank),
                        qualifiers = mapping["qualifiers"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank),
                    )
                }
                errors += PriResourceMapValidator.validate(mappings, packageRoot)
            }
        }
        return errors
    }

    /** Records the final generated application PRI after staging has completed. */
    fun recordGeneratedPri(report: Path, packageRoot: Path, priDump: Path? = null) {
        if (!Files.isRegularFile(report)) return
        val normalizedRoot = packageRoot.toAbsolutePath().normalize()
        val pri = normalizedRoot.resolve("resources.pri").normalize()
        if (!pri.startsWith(normalizedRoot) || !pri.isRegularFile()) return
        val root = runCatching { Json.parseToJsonElement(Files.readString(report)).jsonObject }.getOrNull() ?: return
        val mappings = priDump?.let(PriResourceMapValidator::readDump).orEmpty()
        val updated = root.toMutableMap().apply {
            put(
                "generatedPri",
                buildJsonObject {
                    put("target", "resources.pri")
                    put("sha256", sha256(pri))
                    put("priMappings", buildJsonArray {
                        mappings.forEach { mapping ->
                            add(buildJsonObject {
                                put("resourceUri", mapping.resourceUri)
                                put("candidateType", mapping.candidateType)
                                mapping.value?.let { value -> put("value", value) }
                                mapping.qualifiers?.let { qualifiers -> put("qualifiers", qualifiers) }
                            })
                        }
                    })
                },
            )
        }
        Files.writeString(report, JsonObject(updated).toString() + System.lineSeparator())
    }

    fun writeResolutionReport(path: Path, decisions: Collection<PackagePayloadDecision>) {
        Files.createDirectories(path.parent)
        val json = buildJsonObject {
            put("schemaVersion", 1)
            put("packageRootRelative", true)
            put("entries", buildJsonArray {
                decisions
                    .filterNot { it.isPriCompilerInput }
                    .sortedBy { it.target.toNormalizedPackagePathKey() }
                    .forEach { decision ->
                    add(buildJsonObject {
                        put("target", decision.target.toString().replace('\\', '/'))
                        put("source", decision.source.toString())
                        put("origin", decision.origin)
                        if (decision.source.isRegularFile()) {
                            put("sha256", sha256(decision.source))
                        }
                        decision.overriddenSource?.let { put("overriddenSource", it.toString()) }
                    })
                }
            })
        }
        Files.writeString(path, json.toString() + System.lineSeparator())
    }

    private fun addDecision(
        selected: MutableMap<String, PackagePayloadDecision>,
        decision: PackagePayloadDecision,
        priority: Int,
        reserved: Boolean = false,
        unresolvedConflicts: MutableMap<String, MutableList<PackagePayloadDecision>>,
    ) {
        if (decision.target.toString().isBlank() || decision.target == Path.of(".")) {
            throw GradleException("Package payload target cannot be empty: ${decision.source}.")
        }
        val key = decision.target.toNormalizedPackagePathKey()
        if (key == Path.of("AppxManifest.xml").toNormalizedPackagePathKey()) {
            throw GradleException("Package payload cannot replace reserved AppxManifest.xml at ${decision.source}.")
        }
        val existing = selected[key]
        if (existing == null) {
            selected[key] = decision
            return
        }
        if (reserved || priority == ROOT_PRIORITY || existing.origin == "selected executable") {
            throw GradleException(
                "Package payload ${decision.source} conflicts with reserved package path " +
                    "${decision.target} already provided by ${existing.source}.",
            )
        }
        if (existing.origin == decision.origin) {
            if (normalizedSourceKey(existing.source) != normalizedSourceKey(decision.source)) {
                unresolvedConflicts.getOrPut(key) { mutableListOf(existing) }.add(decision)
            }
            return
        }
        if (priority == EXPLICIT_PRIORITY && existing.origin == "explicit packagePayload") {
            if (normalizedSourceKey(existing.source) != normalizedSourceKey(decision.source)) {
                throw GradleException(
                    "Conflicting explicit package payloads target ${decision.target}: " +
                        "${existing.source} and ${decision.source}.",
                )
            }
            return
        }
        if (priority < originPriority(existing.origin) ||
            priority < EXPLICIT_PRIORITY && existing.origin == "explicit packagePayload"
        ) {
            return
        }
        selected[key] = decision.copy(overriddenSource = existing.source)
    }

    private fun originPriority(origin: String): Int = when (origin) {
        "dependency AppX resource" -> DEPENDENCY_PRIORITY
        "appxResources" -> CONVENTION_PRIORITY
        "explicit packagePayload" -> EXPLICIT_PRIORITY
        "selected executable" -> ROOT_PRIORITY
        else -> CONVENTION_PRIORITY
    }
}

/** Resource compiler inputs are staged into the project PRI, not copied as loose package files. */
private fun AppxResourceInput.isPriCompilerInput(): Boolean =
    relativePath.name.endsWith(".resw", ignoreCase = true)

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun normalizedSourceKey(path: String): String =
    normalizedSourceKey(Path.of(path))

private fun normalizedSourceKey(path: Path): String =
    path.toNormalizedInputPathKey()

private const val CONVENTION_PRIORITY = 10
private const val DEPENDENCY_PRIORITY = 5
private const val EXPLICIT_PRIORITY = 20
private const val ROOT_PRIORITY = 30
private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")

private fun Path.defaultPackagePayloadTarget(fallbackRoot: Path, projectRoot: Path?): Path {
    val normalized = toAbsolutePath().normalize()
    val normalizedFallback = fallbackRoot.toAbsolutePath().normalize()
    return if (projectRoot != null && normalized.startsWith(projectRoot)) {
        normalized.relativeTo(projectRoot)
    } else {
        normalized.relativeTo(normalizedFallback)
    }
}
