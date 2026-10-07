package io.github.composefluent.winrt.ide.templates

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.composefluent.winrt.metadata.WindowsSdkRootDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory

internal data class WinRTInstalledSdk(val version: String, val root: Path, val schemas: Map<String, Path>)
internal data class WinRTInstalledSdkState(val sdks: List<WinRTInstalledSdk> = emptyList(), val loading: Boolean = true)

/** Same registry-first root selection as .cswinrt/src/cswinrt/cmd_reader.h.
 * The wizard and XML resolver share this background inventory; neither does IO in a PSI query. */
@Service(Service.Level.APP)
internal class WinRTInstalledSdks(private val scope: CoroutineScope) {
    val state = MutableStateFlow(WinRTInstalledSdkState())
    internal val initialization = refresh()
    fun refresh() = scope.launch(Dispatchers.IO) {
        val sdks = read(WindowsSdkRootDiscovery.candidateRootsWithRegistry())
        sdks.flatMap { it.schemas.values }.forEach { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
        state.value = WinRTInstalledSdkState(sdks, false)
        ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.project.ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }.forEach {
                com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(it).restart("Windows SDK manifest schemas loaded")
            }
        }
    }

    companion object {
        fun read(roots: List<Path>): List<WinRTInstalledSdk> = roots.flatMap { root ->
            val include = root.resolve("Include")
            if (!Files.isDirectory(include)) emptyList() else Files.list(include).use { paths ->
                paths.filter { Files.isDirectory(it) && it.fileName.toString().matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) }
                    .map { path ->
                        val version = path.fileName.toString()
                        // A metadata-only NuGet package is not an installed Windows SDK.
                        if (!Files.isRegularFile(path.resolve("um/Windows.h")) || !Files.isDirectory(root.resolve("Lib/$version")) ||
                            !Files.isRegularFile(root.resolve("UnionMetadata/$version/Windows.winmd")) ||
                            !Files.isRegularFile(root.resolve("bin/$version/x64/makeappx.exe"))) null
                        else WinRTInstalledSdk(version, root, schemas(path.resolve("winrt")))
                    }.filter { it != null }.map { it!! }.toList()
            }
        }.distinctBy { it.version }.sortedWith { a, b -> compareVersions(b.version, a.version) }

        private fun schemas(directory: Path): Map<String, Path> {
            if (!Files.isDirectory(directory)) return emptyMap()
            val factory = XMLInputFactory.newDefaultFactory().apply {
                setProperty(XMLInputFactory.SUPPORT_DTD, false)
                setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            }
            return Files.list(directory).use { files -> files.filter { it.fileName.toString().endsWith(".xsd", true) }
                .sorted().toList().mapNotNull { path -> runCatching {
                    Files.newInputStream(path).use { input ->
                        val reader = factory.createXMLStreamReader(input)
                        try {
                            while (reader.hasNext() && reader.next() != javax.xml.stream.XMLStreamConstants.START_ELEMENT) { }
                            reader.getAttributeValue(null, "targetNamespace")?.let { it to path }
                        } finally { reader.close() }
                    }
                }.getOrNull() }.toMap() }
        }

        internal fun compareVersions(a: String, b: String): Int = a.split('.').map(String::toInt)
            .zip(b.split('.').map(String::toInt)).firstNotNullOfOrNull { (left, right) -> left.compareTo(right).takeIf { it != 0 } } ?: 0
    }
}
