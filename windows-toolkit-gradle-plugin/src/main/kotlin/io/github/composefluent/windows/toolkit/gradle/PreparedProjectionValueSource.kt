package io.github.composefluent.windows.toolkit.gradle

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

/** Configuration-cache boundary for isolated, cache-first static projection generation. */
abstract class PreparedProjectionValueSource : ValueSource<String, PreparedProjectionValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val request: MapProperty<String, String>
        val classpath: ConfigurableFileCollection
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val request = parameters.request.get()
        val entry = Path.of(request.getValue("entry"))
        Files.createDirectories(entry.parent)
        FileChannel.open(
            entry.resolveSibling("${entry.fileName}.lock"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use {
                if (!isPreparedStaticSourceValid(entry.resolve("sources"))) {
                    val temporary = Files.createTempDirectory(entry.parent, ".${entry.fileName}-")
                    try {
                        val requestFile = temporary.resolve("request.properties")
                        Files.newOutputStream(requestFile).use { output ->
                            Properties().apply { putAll(request) }.store(output, null)
                        }
                        execOperations.javaexec { spec ->
                            spec.classpath(parameters.classpath)
                            spec.mainClass.set(PreparedProjectionGeneratorMain::class.java.name)
                            spec.jvmArgs("-Xmx2048m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8")
                            spec.args(requestFile.toString())
                        }.assertNormalExitValue()
                        Files.delete(requestFile)
                        check(isPreparedStaticSourceValid(temporary.resolve("sources"))) {
                            "Isolated static projection generation did not produce a valid manifest."
                        }
                        // Failed generation never replaces an existing cache entry.
                        if (Files.exists(entry)) GradleFileOperations.deleteDirectory(entry)
                        runCatching { Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE) }
                            .getOrElse { Files.move(temporary, entry) }
                    } catch (error: Throwable) {
                        GradleFileOperations.deleteDirectory(temporary)
                        throw error
                    }
                }
            }
        }
        return entry.resolve("sources").toString()
    }
}
