package io.github.composefluent.winrt.ide.analysis

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

data class WinRTXamlDocument(val path: String, val text: String, val revision: Long)

/** Uses XamlCompiler's DOM/harvester and KotlinXamlDeclarationWriter, just like CompileWinRTXamlTask. */
internal object WinRTXamlDocumentCompiler {
    suspend fun harvest(compilation: WinRTXamlCompilationData, documents: List<WinRTXamlDocument>, cacheRoot: Path): String =
        withContext(Dispatchers.IO) {
            require(Files.isRegularFile(Path.of(compilation.inputFile))) {
                "Prepare XAML analysis with ${compilation.taskName} before editing declarations."
            }
            val compiler = Path.of(compilation.compilerDirectory).resolve("XamlCompiler.exe")
            require(Files.isRegularFile(compiler)) { "The configured XamlCompiler is unavailable; prepare XAML analysis." }
            val input = Json.parseToJsonElement(Files.readString(Path.of(compilation.inputFile))).jsonObject
            require(input["IsPass1"]?.jsonPrimitive?.boolean == true) { "IDE analysis requires a declaration-pass input." }
            Files.createDirectories(cacheRoot)
            val directory = Files.createTempDirectory(cacheRoot, "snapshot-")
            var process: OSProcessHandler? = null
            val sourceMapping = mutableMapOf<String, String>()
            try {
                val edits = documents.associateBy { WinRTXamlSnapshotService.key(it.path) }
                val sources = input.getValue("XamlPages").jsonArray.mapIndexedNotNull { ordinal, item ->
                    val data = item.jsonObject
                    val source = data["FullPath"]?.jsonPrimitive?.content ?: data.getValue("ItemSpec").jsonPrimitive.content
                    if (!Files.isRegularFile(Path.of(source))) return@mapIndexedNotNull null
                    val edit = edits[WinRTXamlSnapshotService.key(source)] ?: return@mapIndexedNotNull item
                    val resource = data["MSBuild_Link"]?.jsonPrimitive?.content ?: Path.of(source).fileName.toString()
                    require(directory.resolve("sources").resolve(resource).normalize().startsWith(directory.resolve("sources"))) {
                        "XAML resource escapes the snapshot directory."
                    }
                    // XAMLC's .NET Framework host still has MAX_PATH-sensitive code.
                    // MSBuild_Link retains the package identity independently of this
                    // short physical path; copying the package hierarchy here can
                    // exceed that limit under an IDE sandbox's system directory.
                    val target = directory.resolve("sources/$ordinal.xaml")
                    Files.createDirectories(target.parent)
                    Files.writeString(target, edit.text)
                    sourceMapping[target.toString()] = source
                    JsonObject(data + mapOf("FullPath" to JsonPrimitive(target.toString()), "ItemSpec" to JsonPrimitive(target.toString())))
                }
                val preparedPaths = input.getValue("XamlPages").jsonArray.map {
                    WinRTXamlSnapshotService.key(it.jsonObject.getValue("FullPath").jsonPrimitive.content)
                }.toSet()
                require(edits.keys.all { it in preparedPaths }) { "New XAML files require Gradle synchronization and Prepare XAML analysis." }
                val snapshotInput = JsonObject(input + mapOf(
                    "ProjectPath" to JsonPrimitive(directory.resolve("snapshot.proj").toString()),
                    "OutputPath" to JsonPrimitive(directory.resolve("compiled").toString()),
                    "SavedStateFile" to JsonPrimitive(directory.resolve("state.xml").toString()),
                    "XamlPages" to JsonArray(sources),
                ))
                val inputFile = directory.resolve("input.json")
                val outputFile = directory.resolve("output.json")
                Files.writeString(inputFile, snapshotInput.toString())
                val output = StringBuilder()
                val handler = OSProcessHandler(GeneralCommandLine(compiler.toString(), inputFile.toString(), outputFile.toString())
                    .withWorkDirectory(directory.toFile()).withCharset(Charsets.UTF_8))
                process = handler
                val exit = suspendCancellableCoroutine { continuation ->
                    handler.addProcessListener(object : ProcessListener {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) { synchronized(output) { output.append(event.text) } }
                        override fun processTerminated(event: ProcessEvent) {
                            if (continuation.isActive) continuation.resume(event.exitCode)
                        }
                    })
                    continuation.invokeOnCancellation { handler.destroyProcess() }
                    handler.startNotify()
                }
                coroutineContext.ensureActive()
                require(Files.isRegularFile(outputFile)) { "XamlCompiler exited with $exit: $output" }
                val index = runCatching { WinRTXamlDeclarations.readCompilerOutput(outputFile) }.getOrElse { failure ->
                    val message = sourceMapping.entries.fold(failure.message.orEmpty()) { text, (snapshot, source) ->
                        text.replace(snapshot, source, ignoreCase = true)
                    }
                    throw IllegalArgumentException(message, failure)
                }
                check(exit == 0) { "XamlCompiler exited with $exit: $output" }
                WinRTXamlDeclarations.canonicalText(index)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    process?.takeUnless { it.isProcessTerminated }?.let { it.destroyProcess(); it.waitFor(5000) }
                    // Only this invocation's owned temporary directory is removed,
                    // after the compiler has released its files on Windows.
                    check(directory.toAbsolutePath().normalize().parent == cacheRoot.toAbsolutePath().normalize())
                    check(directory.fileName.toString().startsWith("snapshot-"))
                    if (process == null || process.isProcessTerminated) {
                        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
                    }
                }
            }
        }
}
