package io.github.composefluent.winrt.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class VerifyBinaryMarkerAbsentTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaryArtifacts: ConfigurableFileCollection

    @get:Input
    abstract val markers: SetProperty<String>

    @get:Input
    abstract val artifactDescription: Property<String>

    @TaskAction
    fun verifyMarkerIsAbsent() {
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
        val markerBytes = markers.get().associateWith(String::encodeToByteArray)
        check(markerBytes.isNotEmpty()) {
            "At least one forbidden marker is required for ${artifactDescription.get()}."
        }
        val match = files.firstNotNullOfOrNull { file ->
            val bytes = file.readBytes()
            markerBytes.keys.firstOrNull { marker -> bytes.containsSequence(markerBytes.getValue(marker)) }
                ?.let { marker -> file to marker }
        }
        check(match == null) {
            val (containingFile, marker) = match!!
            "Forbidden WinRT call-site marker '$marker' escaped lowering in ${containingFile.absolutePath}."
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
