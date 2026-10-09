package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files

class ResolveWinRTXamlReferencesTaskTest {
    @Test
    fun reference_resolution_waits_for_the_restore_output_and_preserves_unchanged_manifests() {
        // .cswinrt/nuget/Microsoft.Windows.CsWinRT.targets: ResolveReferences
        // precedes CsWinRTPrepareProjection; importing the graph is not a restore.
        val project = ProjectBuilder.builder().build()
        val root = project.layout.buildDirectory.dir("xaml-references-fixture").get().asFile.toPath()
        val lock = root.resolve(".winapp/winmds.lock.json")
        val output = root.resolve("references.txt")
        val task = project.tasks.register("fixtureXamlReferences", ResolveWinRTXamlReferencesTask::class.java) {
            it.nugetPackages.set(listOf("Sample.Xaml@1.0.0"))
            it.restoreLockFiles.from(lock)
            it.outputFile.set(output.toFile())
        }.get()
        assertFalse(Files.exists(lock))
        assertFalse(Files.exists(output))
        val cache = root.resolve("nuget")
        val packageRoot = cache.resolve("sample.xaml/1.0.0")
        val winmd = packageRoot.resolve("lib/Sample.Xaml.winmd")
        Files.createDirectories(winmd.parent)
        Files.writeString(winmd, "fixture metadata")
        Files.writeString(packageRoot.resolve("Sample.Xaml.nuspec"),
            "<package><metadata><id>Sample.Xaml</id><version>1.0.0</version></metadata></package>")
        Files.createDirectories(lock.parent)
        Files.writeString(lock, """
            {"schema":3,"nuget_cache_dir":"${cache.toString().replace('\\', '/')}",
             "packages":[{"name":"Sample.Xaml","version":"1.0.0","winmds":[]}]}
        """.trimIndent())
        task.resolve()
        assertEquals(listOf(winmd.toAbsolutePath().normalize().toString()), Files.readAllLines(output))
        val modificationTime = Files.getLastModifiedTime(output)
        task.resolve()
        assertEquals(modificationTime, Files.getLastModifiedTime(output))
    }
}
