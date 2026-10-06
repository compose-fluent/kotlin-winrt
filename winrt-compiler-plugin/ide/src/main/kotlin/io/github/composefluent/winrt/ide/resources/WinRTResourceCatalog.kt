package io.github.composefluent.winrt.ide.resources

import com.google.gson.JsonParser
import io.github.composefluent.windows.toolkit.gradle.collectAppxResourceInputs
import io.github.composefluent.windows.toolkit.gradle.toSafeRelativePath
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTPackageLayoutData
import io.github.composefluent.winrt.ide.gradle.WinRTSourceSetData
import java.nio.file.Files
import java.nio.file.Path

data class WinRTResourceEntry(val target: String, val source: String, val owner: String,
    val overrides: List<String> = emptyList(), val sourceArchive: String? = null)
data class WinRTPriCandidate(val resourceUri: String, val type: String, val value: String, val qualifiers: String)
data class WinRTResourceInventory(val entries: List<WinRTResourceEntry>, val candidates: List<WinRTPriCandidate> = emptyList(),
    val label: String, val error: String? = null)

/** Source selection stays in AppxResourceLayout; final layouts come from the staging report. */
object WinRTResourceCatalog {
    fun sourceSet(module: WinRTModuleData, sourceSet: WinRTSourceSetData): WinRTResourceInventory = WinRTResourceInventory(
        collectAppxResourceInputs(sourceSet.appxResourceRoots.map(Path::of)).map { input ->
            WinRTResourceEntry(input.relativePathString, input.source.toString(), module.projectPath,
                input.overriddenSources.map(Path::toString))
        }, label = "${module.projectPath} · ${sourceSet.name} · source set files")

    fun staged(module: WinRTModuleData, layout: WinRTPackageLayoutData, modules: List<WinRTModuleData>): WinRTResourceInventory {
        val report = Path.of(layout.resourceReportFile)
        if (!Files.isRegularFile(report)) return WinRTResourceInventory(emptyList(), label = layout.variant,
            error = "Stage this variant to obtain its package resource report.")
        return runCatching {
            val root = JsonParser.parseString(Files.readString(report)).asJsonObject
            require(root["schemaVersion"].asInt == 1 && root["packageRootRelative"].asBoolean) { "Unsupported resource resolution report." }
            val entries = root["entries"].asJsonArray.map { value ->
                val item = value.asJsonObject
                val target = item["target"].asString.toSafeRelativePath("Resource report target").toString().replace('\\', '/')
                val source = item["source"].asString
                val archive = item["sourceArchive"]?.asString
                val owner = modules.filter { candidate ->
                    val path = Path.of(archive ?: source).toAbsolutePath().normalize()
                    path.startsWith(Path.of(candidate.projectDirectory).toAbsolutePath().normalize())
                }.maxByOrNull { it.projectDirectory.length }?.projectPath ?: item["origin"].asString
                val overrides = item["overriddenSources"]?.asJsonArray?.map { it.asString }
                    ?: listOfNotNull(item["overriddenSource"]?.asString)
                WinRTResourceEntry(target, source, owner, overrides, archive)
            }
            val candidates = root["generatedPri"]?.asJsonObject?.get("priMappings")?.asJsonArray?.toList().orEmpty().map { value ->
                val item = value.asJsonObject
                WinRTPriCandidate(item["resourceUri"].asString, item["candidateType"].asString,
                    item["value"]?.asString.orEmpty(), item["qualifiers"]?.asString.orEmpty())
            }
            WinRTResourceInventory(entries, candidates, "${module.projectPath} · ${layout.variant} · last staged package")
        }.getOrElse { WinRTResourceInventory(emptyList(), label = layout.variant, error = it.message ?: "Invalid resource report") }
    }

    // Filename qualifiers are display grouping only. Actual candidate selection is recorded by PRI.
    fun family(path: String): String = path.replace(Regex("\\.(?:scale|targetsize|contrast|altform|theme)-[^./]+"), "")
}
