package io.github.composefluent.winrt.build

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

abstract class VerifyBinaryMarkerAbsentTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaryArtifacts: ConfigurableFileCollection

    @get:Input
    abstract val markers: SetProperty<String>

    @get:Input
    abstract val requiredMarkers: SetProperty<String>

    /**
     * When set, forbidden markers are checked only in methods whose names start with one of these
     * prefixes. This is needed for generated support shards that intentionally colocate ABI codecs
     * with lowered call sites: codec bodies may use FFM scratch/layout helpers, while call-site
     * methods must not retain the old lowering path.
     */
    @get:Input
    abstract val methodNamePrefixes: SetProperty<String>

    /**
     * Optional prefixes used to scope marker checks to entries inside packed KLIB archives.
     * Directory-based artifacts continue to inspect every file.
     */
    @get:Input
    abstract val archiveEntryPrefixes: SetProperty<String>

    @get:Input
    abstract val artifactDescription: Property<String>

    @get:OutputFile
    abstract val verificationReport: RegularFileProperty

    init {
        requiredMarkers.convention(emptySet())
        methodNamePrefixes.convention(emptySet())
        archiveEntryPrefixes.convention(emptySet())
        verificationReport.convention(
            project.layout.buildDirectory.file("reports/kotlin-winrt/${name}.verified"),
        )
    }

    @TaskAction
    fun verifyMarkerIsAbsent() {
        val report = verificationReport.get().asFile.toPath()
        try {
            val files = binaryArtifacts.files
                .asSequence()
                .flatMap { artifact ->
                    if (artifact.isDirectory) {
                        artifact.walkTopDown().filter(File::isFile)
                    } else {
                        sequenceOf(artifact).filter(File::isFile)
                    }
                }
                .toList()
            check(files.isNotEmpty()) {
                "Missing ${artifactDescription.get()} binary artifacts."
            }
            val contents = files.asSequence()
                .flatMap(::readBinaryContents)
                .toList()
            val markerBytes = markers.get().associateWith(String::encodeToByteArray)
            val requiredMarkerBytes = requiredMarkers.get().associateWith(String::encodeToByteArray)
            check(markerBytes.isNotEmpty() || requiredMarkerBytes.isNotEmpty()) {
                "At least one forbidden or required marker is needed for ${artifactDescription.get()}."
            }
            val scopedPrefixes = methodNamePrefixes.get()
            val match = contents.firstNotNullOfOrNull { content ->
                findForbiddenMarker(content, markerBytes, scopedPrefixes)
            }
            check(match == null) {
                val (containingFile, marker, methodName) = match!!
                val methodSuffix = methodName?.let { " method '$it'" }.orEmpty()
                "Forbidden WinRT call-site marker '$marker' escaped lowering in " +
                    "${containingFile.path}$methodSuffix."
            }
            requiredMarkerBytes.forEach { (marker, bytes) ->
                check(contents.any { content -> content.bytes.containsSequence(bytes) }) {
                    "Required WinRT call-site marker '$marker' was not emitted in ${artifactDescription.get()}."
                }
            }
            val reportContent = "verified=true\nartifactDescription=${artifactDescription.get()}\n"
            if (!java.nio.file.Files.isRegularFile(report) || java.nio.file.Files.readString(report) != reportContent) {
                java.nio.file.Files.createDirectories(report.parent)
                java.nio.file.Files.writeString(report, reportContent)
            }
        } catch (failure: Throwable) {
            // A failed verification must never leave a previous success report behind.
            java.nio.file.Files.deleteIfExists(report)
            throw failure
        }
    }

    private fun findForbiddenMarker(
        content: BinaryContent,
        markerBytes: Map<String, ByteArray>,
        methodNamePrefixes: Set<String>,
    ): ForbiddenMarkerMatch? {
        val bytes = content.bytes
        if (methodNamePrefixes.isEmpty() || !content.path.endsWith(".class")) {
            return markerBytes.keys.firstOrNull { marker -> bytes.containsSequence(markerBytes.getValue(marker)) }
                ?.let { marker -> ForbiddenMarkerMatch(content, marker, null) }
        }

        var match: ForbiddenMarkerMatch? = null
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (methodNamePrefixes.none(name::startsWith)) return null
                    return object : MethodVisitor(Opcodes.ASM9) {
                        private fun inspect(vararg values: String?) {
                            if (match != null) return
                            val marker = values.asSequence()
                                .filterNotNull()
                                .flatMap { value -> markerBytes.keys.asSequence().filter(value::contains) }
                                .firstOrNull()
                            if (marker != null) match = ForbiddenMarkerMatch(content, marker, name)
                        }

                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) = inspect(owner, name, descriptor)

                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) =
                            inspect(owner, name, descriptor)

                        override fun visitTypeInsn(opcode: Int, type: String) = inspect(type)

                        override fun visitLdcInsn(value: Any) = inspect(value.toString())

                        override fun visitInvokeDynamicInsn(
                            name: String,
                            descriptor: String,
                            bootstrapMethodHandle: org.objectweb.asm.Handle,
                            vararg bootstrapMethodArguments: Any,
                        ) = inspect(
                            name,
                            descriptor,
                            bootstrapMethodHandle.owner,
                            bootstrapMethodHandle.name,
                            bootstrapMethodHandle.desc,
                            *bootstrapMethodArguments.map(Any::toString).toTypedArray(),
                        )
                    }
                }
            },
            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return match
    }

    private data class ForbiddenMarkerMatch(
        val content: BinaryContent,
        val marker: String,
        val methodName: String?,
    )

    private data class BinaryContent(
        val file: File,
        val entryName: String? = null,
        val bytes: ByteArray,
    ) {
        val path: String
            get() = entryName?.let { "${file.absolutePath}!/$it" } ?: file.absolutePath
    }

    private fun readBinaryContents(file: File): Sequence<BinaryContent> = sequence {
        if (!file.extension.equals("klib", ignoreCase = true)) {
            yield(BinaryContent(file = file, bytes = file.readBytes()))
            return@sequence
        }
        val entryPrefixes = archiveEntryPrefixes.get()
        ZipFile(file).use { archive ->
            archive.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { entry ->
                    entryPrefixes.isEmpty() || entryPrefixes.any(entry.name::startsWith)
                }
                .forEach { entry ->
                    archive.getInputStream(entry).use { input ->
                        yield(BinaryContent(file, entry.name, input.readBytes()))
                    }
                }
        }
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        if (sequence.isEmpty()) return true
        if (size < sequence.size) return false
        for (start in 0..size - sequence.size) {
            var matches = true
            for (offset in sequence.indices) {
                if (this[start + offset] != sequence[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }
}
