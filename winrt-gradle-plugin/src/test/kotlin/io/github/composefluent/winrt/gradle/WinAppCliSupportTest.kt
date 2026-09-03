package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText

class WinAppCliSupportTest {
    @Test
    fun compatible_system_cli_is_preferred_without_downloading() {
        val downloads = AtomicInteger()
        val root = Files.createTempDirectory("kotlin-winrt-winapp-system-")
        val support = support(
            cacheDirectory = root,
            packageBytes = byteArrayOf(),
            packageSha512 = "unused",
            packageDownloads = downloads,
            runner = { executable, _, _, _ ->
                WinAppCliInvocation(executable, 0, "0.6.0\n")
            },
        )

        val resolved = support.resolve()

        assertEquals("winapp", resolved.command)
        assertEquals("0.6.0", resolved.version)
        assertEquals(WinAppCliSource.System, resolved.source)
        assertEquals(0, downloads.get())
    }

    @Test
    fun incompatible_system_cli_falls_back_to_verified_managed_package() {
        val packageBytes = winAppPackage(
            "tools/win-x64/winapp.exe" to "managed-executable".toByteArray(),
            "tools/win-x64/libSkiaSharp.dll" to "native-dependency".toByteArray(),
            "tools/win-arm64/winapp.exe" to "other-architecture".toByteArray(),
        )
        val downloads = AtomicInteger()
        val root = Files.createTempDirectory("kotlin-winrt-winapp-managed-")
        val support = support(
            cacheDirectory = root,
            packageBytes = packageBytes,
            packageSha512 = sha512(packageBytes),
            packageDownloads = downloads,
            runner = { executable, _, _, _ ->
                val version = if (executable == "winapp") "0.5.0" else "0.6.0"
                WinAppCliInvocation(executable, 0, "$version\n")
            },
        )

        val resolved = support.resolve()
        val executable = java.nio.file.Path.of(resolved.command)

        assertEquals(WinAppCliSource.Managed, resolved.source)
        assertEquals(1, downloads.get())
        assertTrue(executable.exists())
        assertEquals(sha512(packageBytes).take(16), executable.parent.parent.fileName.toString())
        assertTrue(executable.parent.resolve("libSkiaSharp.dll").exists())
        assertTrue(executable.parent.resolve(".complete").readText().contains("version=0.6.0"))
        assertFalse(executable.parent.resolve("other-architecture").exists())
    }

    @Test
    fun complete_managed_cache_is_reused_while_offline() {
        val packageBytes = winAppPackage(
            "tools/win-x64/winapp.exe" to "managed-executable".toByteArray(),
        )
        val downloads = AtomicInteger()
        val root = Files.createTempDirectory("kotlin-winrt-winapp-offline-")
        val packageSha512 = sha512(packageBytes)
        val runner = { executable: String, _: List<String>, _: java.nio.file.Path?, _: Map<String, String> ->
            val version = if (executable == "winapp") null else "0.6.0"
            if (version == null) {
                WinAppCliInvocation(executable, -1, "missing")
            } else {
                WinAppCliInvocation(executable, 0, "$version\n")
            }
        }
        support(root, packageBytes, packageSha512, downloads, runner).resolve()

        val offline = WinAppCliSupport(
            configuredExecutable = "winapp",
            cliVersion = "0.6.0",
            packageSha512 = packageSha512,
            cliCacheDirectory = root,
            offline = true,
            logger = Logging.getLogger(WinAppCliSupportTest::class.java),
            hostRuntimeIdentifier = "win-x64",
            packageStream = { error("offline cache must not download") },
            commandRunner = runner,
        ).resolve()

        assertEquals(WinAppCliSource.Managed, offline.source)
        assertEquals(1, downloads.get())
    }

    @Test
    fun checksum_mismatch_does_not_publish_a_complete_cache() {
        val packageBytes = winAppPackage(
            "tools/win-x64/winapp.exe" to "managed-executable".toByteArray(),
        )
        val root = Files.createTempDirectory("kotlin-winrt-winapp-checksum-")
        val support = support(
            cacheDirectory = root,
            packageBytes = packageBytes,
            packageSha512 = "00",
            runner = { executable, _, _, _ -> WinAppCliInvocation(executable, -1, "missing") },
        )

        val failure = runCatching { support.resolve() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("checksum mismatch"))
        assertFalse(root.resolve("0.6.0/00/win-x64/.complete").exists())
    }

    private fun support(
        cacheDirectory: java.nio.file.Path,
        packageBytes: ByteArray,
        packageSha512: String,
        packageDownloads: AtomicInteger = AtomicInteger(),
        runner: (
            executable: String,
            arguments: List<String>,
            workingDirectory: java.nio.file.Path?,
            environment: Map<String, String>,
        ) -> WinAppCliInvocation,
    ): WinAppCliSupport = WinAppCliSupport(
        configuredExecutable = "winapp",
        cliVersion = "0.6.0",
        packageSha512 = packageSha512,
        cliCacheDirectory = cacheDirectory,
        offline = false,
        logger = Logging.getLogger(WinAppCliSupportTest::class.java),
        hostRuntimeIdentifier = "win-x64",
        packageStream = {
            packageDownloads.incrementAndGet()
            ByteArrayInputStream(packageBytes)
        },
        commandRunner = runner,
    )

    private fun winAppPackage(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }

    private fun sha512(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-512")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
