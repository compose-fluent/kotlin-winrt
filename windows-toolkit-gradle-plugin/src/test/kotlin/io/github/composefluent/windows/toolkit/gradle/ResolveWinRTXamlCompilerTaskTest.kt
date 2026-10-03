package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ResolveWinRTXamlCompilerTaskTest {
    private fun packageFile(root: Path, version: String = "1.0.0", protocol: Int = 1, unsafe: Boolean = false): Path {
        val executable = root.resolve("XamlCompiler.exe")
        Files.writeString(executable, "tooling fixture")
        val manifest = buildJsonObject {
            put("schemaVersion", 1); put("protocolVersion", protocol); put("version", version)
            put("host", "win-x64"); put("executable", "XamlCompiler.exe")
            put("files", buildJsonObject { put("XamlCompiler.exe", xamlSha256(executable)) })
        }
        return root.resolve("compiler.zip").also { path ->
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                listOf("kotlin-xamlc.json" to manifest.toString(), "XamlCompiler.exe" to "tooling fixture")
                    .plus(if (unsafe) listOf("../escaped.txt" to "invalid") else emptyList()).forEach { (name, content) ->
                        zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
                    }
            }
        }
    }

    @Test fun validates_package_identity_integrity_and_safe_paths_before_replacing_output() {
        val root = Files.createTempDirectory("xaml-tool-package-")
        val archive = packageFile(root)
        val output = root.resolve("installed")
        extractXamlCompiler(archive, output, "1.0.0")
        assertTrue(Files.isRegularFile(output.resolve("XamlCompiler.exe")))
        assertTrue(runCatching { extractXamlCompiler(archive, output, "2.0.0") }.isFailure)
        assertEquals("tooling fixture", Files.readString(output.resolve("XamlCompiler.exe")))
        assertTrue(runCatching { extractXamlCompiler(packageFile(root, protocol = 3), output, "1.0.0") }.isFailure)
        assertTrue(runCatching { extractXamlCompiler(packageFile(root, unsafe = true), output, "1.0.0") }.isFailure)
        assertFalse(Files.exists(root.resolve("escaped.txt")))
        Files.writeString(output.resolve("XamlCompiler.exe"), "tampered")
        assertTrue(runCatching { validateXamlCompilerPackage(output, "1.0.0") }.isFailure)
    }

    @Test fun offline_resolution_requires_a_verified_archive() {
        val root = Files.createTempDirectory("xaml-tool-offline-")
        val archive = packageFile(root)
        val hash = xamlSha256(archive)
        val cache = root.resolve("cache/$hash").also(Files::createDirectories)
        Files.copy(archive, cache.resolve("compiler.zip"))
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.register("resolve", ResolveWinRTXamlCompilerTask::class.java).get()
        task.compilerVersion.set("1.0.0")
        task.archiveSha256.set(hash)
        task.archiveUrl.set("https://example.invalid/compiler.zip")
        task.offline.set(true)
        task.cacheDirectory.set(root.resolve("cache").toFile())
        task.outputDirectory.set(root.resolve("installed").toFile())
        task.resolve()
        Files.writeString(cache.resolve("compiler.zip"), "corrupt")
        assertTrue(runCatching { task.resolve() }.exceptionOrNull()?.message.orEmpty().contains("offline"))
    }
}
