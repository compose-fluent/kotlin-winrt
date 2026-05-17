package io.github.composefluent.winrt.gradle

import org.gradle.api.logging.Logger
import java.nio.file.Path

internal object MakePriRunner {
    fun run(
        makePri: Path,
        arguments: List<String>,
        workingDirectory: Path,
        description: String,
        logger: Logger,
    ): String? {
        val process = ProcessBuilder(listOf(makePri.toString()) + arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = decodeProcessOutput(process.inputStream.readBytes())
        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            output
        } else {
            logger.warn("Skipping application PRI generation after makepri failed to $description with exit code $exitCode:\n$output")
            null
        }
    }

    private fun decodeProcessOutput(bytes: ByteArray): String {
        if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) {
            return bytes.toString(Charsets.UTF_16LE)
        }
        return bytes.toString(Charsets.UTF_8)
    }
}
