package io.github.composefluent.winrt.gradle

import org.gradle.api.logging.Logger
import java.io.IOException
import java.nio.file.Path

internal object MakeAppxRunner {
    fun pack(
        makeAppx: Path,
        packageRoot: Path,
        outputFile: Path,
        logger: Logger,
    ): Boolean {
        val arguments = listOf(
            "pack",
            "/d",
            packageRoot.toString(),
            "/p",
            outputFile.toString(),
            "/o",
        )
        val process = try {
            ProcessBuilder(listOf(makeAppx.toString()) + arguments)
                .directory(packageRoot.toFile())
                .redirectErrorStream(true)
                .start()
        } catch (exception: IOException) {
            logger.warn("Skipping application package creation because makeappx could not be started: ${exception.message}")
            return false
        }
        val output = decodeProcessOutput(process.inputStream.readBytes())
        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            true
        } else {
            logger.warn("Skipping application package creation after makeappx failed with exit code $exitCode:\n$output")
            false
        }
    }

    fun unpack(
        makeAppx: Path,
        packageFile: Path,
        outputDirectory: Path,
        logger: Logger,
    ): Boolean {
        val arguments = listOf(
            "unpack",
            "/p",
            packageFile.toString(),
            "/d",
            outputDirectory.toString(),
            "/o",
        )
        val process = try {
            ProcessBuilder(listOf(makeAppx.toString()) + arguments)
                .directory(outputDirectory.parent.toFile())
                .redirectErrorStream(true)
                .start()
        } catch (exception: IOException) {
            logger.warn("Skipping application package verification because makeappx could not be started: ${exception.message}")
            return false
        }
        val output = decodeProcessOutput(process.inputStream.readBytes())
        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            true
        } else {
            logger.warn("Skipping application package verification after makeappx failed with exit code $exitCode:\n$output")
            false
        }
    }

    private fun decodeProcessOutput(bytes: ByteArray): String {
        if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) {
            return bytes.toString(Charsets.UTF_16LE)
        }
        return bytes.toString(Charsets.UTF_8)
    }
}
