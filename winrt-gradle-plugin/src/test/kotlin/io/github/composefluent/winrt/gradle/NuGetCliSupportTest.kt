package io.github.composefluent.winrt.gradle

import org.gradle.api.logging.Logging
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

class NuGetCliSupportTest {
    @Test
    fun install_uses_isolated_caches_and_stable_download_arguments() {
        assumeTrue(System.getProperty("os.name").contains("Windows", ignoreCase = true))
        val root = Files.createTempDirectory("kotlin-winrt-nuget-cli-test-")
        val logFile = root.resolve("nuget-invocation.txt")
        val executable = root.resolve("nuget.cmd")
        Files.writeString(
            executable,
            """
            @echo off
            >>"$logFile" echo args=%*
            >>"$logFile" echo NUGET_PACKAGES=%NUGET_PACKAGES%
            >>"$logFile" echo NUGET_HTTP_CACHE_PATH=%NUGET_HTTP_CACHE_PATH%
            exit /b 0
            """.trimIndent(),
        )
        val scratchDirectory = root.resolve("scratch")

        NuGetCliSupport(
            executable = executable.toString(),
            cliVersion = "7.3.1",
            cliCacheDirectory = root.resolve("cli-cache"),
            scratchDirectory = scratchDirectory,
            logger = Logging.getLogger(NuGetCliSupportTest::class.java),
        ).run(
            arguments = listOf(
                "install",
                "Microsoft.WindowsAppSDK.Base",
                "-Version",
                "1.8.251216001",
                "-NonInteractive",
                "-OutputDirectory",
                root.resolve("install").toString(),
            ),
            description = "install Microsoft.WindowsAppSDK.Base",
        )

        val invocation = Files.readString(logFile)
        assertTrue(invocation.contains("-DirectDownload"))
        assertTrue(invocation.contains("-DisableParallelProcessing"))
        assertTrue(invocation.contains("NUGET_PACKAGES=${scratchDirectory.resolve("global-packages")}"))
        assertTrue(invocation.contains("NUGET_HTTP_CACHE_PATH=${scratchDirectory.resolve("http-cache")}"))
    }
}
