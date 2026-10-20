package io.github.composefluent.windows.toolkit.gradle

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/** CsWinRT ImplGenerator routes types to named SDK/XAML/merged assemblies.
 * Kotlin publishes one real module per owner instead of forwarding duplicate class definitions.
 */
internal data class ProjectionIdentityOwner(val name: String, val version: String, val fingerprint: String)

internal fun projectionOwnerProvider(project: Project): Provider<String> = project.provider {
    val artifact = project.extensions.findByType(org.gradle.api.plugins.BasePluginExtension::class.java)
        ?.archivesName?.get() ?: project.name
    "${project.group}:$artifact"
}

/** Records the resolved projection closure, not packaging identities containing host binaries. */
internal fun projectionDependencyOwners(files: Iterable<File>): JsonArray {
    val owners = sortedMapOf<String, ProjectionIdentityOwner>()
    files.filter(File::isFile).forEach { file ->
        val owner = requireNotNull(readProjectionSurfaceIdentity(file).owner)
        val previous = owners.putIfAbsent(owner.name, owner)
        check(previous == null || previous == owner) {
            "Conflicting WinRT projection dependency owner '${owner.name}': $previous versus $owner"
        }
    }
    return JsonArray(owners.values.map { owner -> buildJsonObject {
        put("owner", owner.name)
        put("version", owner.version)
        put("identityFingerprint", owner.fingerprint)
    } })
}

internal fun readProjectionDependencyOwners(file: File): List<ProjectionIdentityOwner> {
    if (!file.isFile) return emptyList()
    val document = readProjectionIdentityDocument(file.readText(), file)
    val records = document["dependencyOwners"] ?: return emptyList()
    check(records is JsonArray) { "Malformed dependencyOwners in WinRT identity '$file'" }
    return records.map { record ->
        check(record is JsonObject) { "Malformed dependency owner in WinRT identity '$file'" }
        fun field(name: String): String {
            val value = record[name] as? JsonPrimitive
            check(value?.isString == true && value.content.isNotBlank()) {
                "Missing dependency owner $name in WinRT identity '$file'"
            }
            return value.content
        }
        ProjectionIdentityOwner(field("owner"), field("version"), field("identityFingerprint"))
    }
}

internal fun readProjectionIdentityOwner(document: JsonObject, file: File): ProjectionIdentityOwner? {
    if (document.isEmpty() && !file.isFile) return null
    fun required(name: String): String {
        val value = document[name] as? JsonPrimitive
        check(value?.isString == true && value.content.isNotBlank()) {
            "WinRT projection identity '$file' must declare $name; rebuild the producer with the current toolkit."
        }
        return value.content
    }
    // CsWinRT consumes reference-assembly contracts separately from linked implementations.
    // Component payloads belong to packaging: embedding a JVM JAR or Native DLL must not
    // change the projection owner recorded before compilation. This is still an identity
    // of the projection metadata, not a substitute for compiled Kotlin ABI validation.
    fun canonical(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }
    val name = required("projectionOwner")
    val version = required("projectionOwnerVersion")
    val projectionContract = JsonObject(document + ("authoredTargetArtifactRecords" to JsonArray(emptyList())))
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(canonical(projectionContract).toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return ProjectionIdentityOwner(name, version, fingerprint)
}
