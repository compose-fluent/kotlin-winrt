package io.github.composefluent.winrt.ide.model

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Gradle prepares a separate payload and its resolved launch options while the
 * running application remains usable. The IDE only starts the prepared payload;
 * it does not duplicate Gradle's argument/environment/deployment policy. */
data class WinRTPreparedLaunch(val executable: String, val arguments: List<String>, val environment: Map<String, String>) {
    fun write(file: Path) {
        DataOutputStream(Files.newOutputStream(file)).use { output ->
            output.writeUTF("kotlin-winrt-prepared-launch-v1")
            output.writeUTF(executable)
            require(arguments.size <= 1024 && environment.size <= 4096)
            output.writeInt(arguments.size); arguments.forEach(output::writeUTF)
            output.writeInt(environment.size); environment.forEach { (key, value) -> output.writeUTF(key); output.writeUTF(value) }
        }
    }
    companion object {
        const val SLOT_ENVIRONMENT = "KOTLIN_WINRT_RESTART_SLOT"
        const val FILE_NAME = "prepared-launch.bin"
        fun deployment(base: Path, slot: String): Path {
            val root = base.toAbsolutePath().normalize()
            if (slot.isEmpty()) return root
            require(UUID.fromString(slot).toString() == slot) { "Invalid restart deployment identity." }
            // Keep the extra path short: the Windows bootstrap loader has a path limit.
            return root.parent.resolve(".r").resolve(slot)
        }
        fun read(file: Path, expectedExecutable: Path): WinRTPreparedLaunch {
            require(!Files.isSymbolicLink(file) && Files.size(file) <= 1024 * 1024)
            return DataInputStream(Files.newInputStream(file)).use { input ->
                require(input.readUTF() == "kotlin-winrt-prepared-launch-v1")
                val executable = input.readUTF()
                require(Path.of(executable).toAbsolutePath().normalize() == expectedExecutable.toAbsolutePath().normalize() && Files.isRegularFile(expectedExecutable))
                val args = List(input.readInt().also { require(it in 0..1024) }) { input.readUTF() }
                val environment = buildMap { repeat(input.readInt().also { require(it in 0..4096) }) { put(input.readUTF(), input.readUTF()) } }
                require(input.read() == -1)
                WinRTPreparedLaunch(executable, args, environment)
            }
        }
        fun discard(baseExecutable: Path, candidate: Path) {
            require(isReplacement(baseExecutable, candidate)) { "Refusing to remove an unrelated deployment." }
            val root = candidate.toAbsolutePath().normalize().parent
            require(!Files.isSymbolicLink(root) && !Files.isSymbolicLink(root.parent))
            if (Files.exists(root)) Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
        fun isReplacement(baseExecutable: Path, candidate: Path): Boolean = runCatching {
            val base = baseExecutable.toAbsolutePath().normalize()
            val path = candidate.toAbsolutePath().normalize()
            val relative = base.parent.parent.resolve(".r").relativize(path)
            relative.nameCount == 2 && relative.fileName == base.fileName &&
                deployment(base.parent, relative.getName(0).toString()).resolve(base.fileName) == path
        }.getOrDefault(false)
    }
}
