package io.github.composefluent.winrt.gradle

import org.gradle.api.logging.Logger
import java.io.IOException
import java.nio.file.Path

internal object PowerShellAppxInstaller {
    fun install(
        powerShellExecutable: String,
        packageFile: Path,
        forceApplicationShutdown: Boolean,
        logger: Logger,
    ): Boolean {
        val command = buildString {
            append("Add-AppxPackage -Path '")
            append(packageFile.toString().replace("'", "''"))
            append("'")
            if (forceApplicationShutdown) {
                append(" -ForceApplicationShutdown")
            }
        }
        val process = try {
            ProcessBuilder(
            powerShellExecutable,
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            command,
            )
                .redirectErrorStream(true)
                .start()
        } catch (exception: IOException) {
            logger.warn("Skipping application package install because PowerShell could not be started: ${exception.message}")
            return false
        }
        val output = decodeProcessOutput(process.inputStream.readBytes())
        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            true
        } else {
            logger.warn("Skipping application package install after Add-AppxPackage failed with exit code $exitCode:\n$output")
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
