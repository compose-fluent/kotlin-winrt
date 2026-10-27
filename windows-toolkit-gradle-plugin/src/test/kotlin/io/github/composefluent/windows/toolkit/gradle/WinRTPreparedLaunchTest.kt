package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.ide.model.WinRTPreparedLaunch
import java.nio.file.Files
import java.util.UUID
import org.junit.Test
import org.junit.Assert.*

class WinRTPreparedLaunchTest {
    @Test fun prepared_native_payload_keeps_old_deployment_and_resolved_launch_options() {
        val root = Files.createTempDirectory("prepared-native")
        try {
            val project = org.gradle.testfixtures.ProjectBuilder.builder().withProjectDir(root.toFile()).build()
            val source = root.resolve("staging"); Files.createDirectories(source)
            Files.writeString(source.resolve("app.exe"), "new binary")
            val base = root.resolve("run/Unpackaged"); Files.createDirectories(base)
            Files.writeString(base.resolve("app.exe"), "running binary")
            val slot = UUID.randomUUID().toString()
            val task = project.tasks.create("prepareNative", RunWinAppHostTask::class.java)
            task.hostExecutable.set(source.resolve("app.exe").toFile())
            task.workingDirectory.set(source.toFile()); task.deploymentDirectory.set(base.toFile())
            task.nativeCompilerHome.set(root.resolve("compiler").toString())
            task.restartSlot.set(slot); task.noLaunch.set(true)
            task.args.set(listOf("space argument", "\"quoted\""))
            task.environmentVariables.put("APPLICATION_MODE", "configured")
            task.run()
            val executable = WinRTPreparedLaunch.deployment(base, slot).resolve("app.exe")
            val record = WinRTPreparedLaunch.read(executable.parent.resolve(WinRTPreparedLaunch.FILE_NAME), executable)
            assertEquals("running binary", Files.readString(base.resolve("app.exe")))
            assertEquals("new binary", Files.readString(executable))
            assertEquals(listOf("space argument", "\"quoted\""), record.arguments)
            assertEquals("configured", record.environment["APPLICATION_MODE"])
            assertEquals(executable.parent.toString(), record.environment["KOTLIN_WINRT_RUNTIME_ASSETS_ROOT"])
            assertTrue(WinRTPreparedLaunch.isReplacement(base.resolve("app.exe"), executable))
            assertFalse(WinRTPreparedLaunch.isReplacement(base.resolve("other.exe"), executable))
            assertThrows(IllegalArgumentException::class.java) { WinRTPreparedLaunch.discard(base.resolve("app.exe"), source.resolve("app.exe")) }
            WinRTPreparedLaunch.discard(base.resolve("app.exe"), executable)
            assertFalse(Files.exists(executable))
            assertEquals("running binary", Files.readString(base.resolve("app.exe")))
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    @Test fun malformed_slot_cannot_escape_deployment_root() {
        val base = java.nio.file.Path.of("build/run/Unpackaged")
        for (value in listOf("..", "../outside", "", "not-a-uuid")) {
            if (value.isEmpty()) assertEquals(base.toAbsolutePath().normalize(), WinRTPreparedLaunch.deployment(base, value))
            else assertThrows(IllegalArgumentException::class.java) { WinRTPreparedLaunch.deployment(base, value) }
        }
    }
}
