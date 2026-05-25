package io.github.composefluent.winrt.gradle

import org.gradle.api.logging.Logger
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal object SignToolRunner {
    fun sign(
        signTool: Path,
        packageFile: Path,
        certificateThumbprint: String,
        certificateFile: Path?,
        certificatePassword: String,
        timestampUrl: String,
        hashAlgorithm: String,
        logger: Logger,
    ): Boolean {
        val algorithm = hashAlgorithm.ifBlank { "SHA256" }
        val arguments = buildList {
            add("sign")
            add("/fd")
            add(algorithm)
            if (timestampUrl.isNotBlank()) {
                add("/tr")
                add(timestampUrl)
                add("/td")
                add(algorithm)
            }
            when {
                certificateFile != null && certificateFile.isRegularFile() -> {
                    add("/f")
                    add(certificateFile.toString())
                    if (certificatePassword.isNotBlank()) {
                        add("/p")
                        add(certificatePassword)
                    }
                }
                certificateThumbprint.isNotBlank() -> {
                    add("/sha1")
                    add(certificateThumbprint)
                }
                else -> add("/a")
            }
            add(packageFile.toString())
        }
        val processBuilder = ProcessBuilder(listOf(signTool.toString()) + arguments)
            .redirectErrorStream(true)
        packageFile.parent?.let { processBuilder.directory(it.toFile()) }
        val process = try {
            processBuilder.start()
        } catch (exception: IOException) {
            logger.warn("Skipping application package signing because signtool could not be started: ${exception.message}")
            return false
        }
        val output = decodeProcessOutput(process.inputStream.readBytes())
        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            true
        } else {
            logger.warn("Skipping application package signing after signtool failed with exit code $exitCode:\n$output")
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
