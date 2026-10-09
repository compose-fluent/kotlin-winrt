package io.github.composefluent.winrt.ide.resources

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiFileFactory
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.XmlSchemaProvider
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.templates.WinRTInstalledSdks

/** Resolve Microsoft's real SDK schemas, including their namespace-only imports.
 * This retains native completion, declaration navigation and invalid-attribute diagnostics. */
class WinRTManifestSchemaProvider : XmlSchemaProvider() {
    override fun isAvailable(file: XmlFile): Boolean = file.getUserData(BUNDLED) == true || WinRTXmlFormEditorProvider.isManifest(file.name) ||
        file.name.endsWith(".xsd", true) && service<WinRTInstalledSdks>().state.value.sdks.any { sdk ->
            sdk.schemas.values.any { it.toString().replace('\\', '/') == file.virtualFile?.path }
        }

    override fun getSchema(url: String, module: Module?, baseFile: PsiFile): XmlFile? {
        val sdks = service<WinRTInstalledSdks>().state.value.sdks
        val path = baseFile.virtualFile?.path.orEmpty()
        val owningSdk = sdks.firstOrNull { sdk -> sdk.schemas.values.any { it.toString().replace('\\', '/') == path } }
        val model = baseFile.project.service<WinRTProjectService>().modules.value.filter {
            path.startsWith(it.projectDirectory.replace('\\', '/') + "/")
        }.maxByOrNull { it.projectDirectory.length }
        // Respect the project's selected SDK. Never silently validate a newer namespace against another version.
        val sdk = owningSdk ?: if (model?.windowsSdkVersion?.isNotBlank() == true)
            sdks.firstOrNull { it.version == model.windowsSdkVersion } else sdks.firstOrNull()
        val source = sdk?.schemas?.get(url) ?: return baseFile.project.service<WinRTManifestBundledSchemas>().get(url)
        val file = LocalFileSystem.getInstance().findFileByNioFile(source) ?: return null
        return PsiManager.getInstance(baseFile.project).findFile(file) as? XmlFile
    }
    companion object { internal val BUNDLED = com.intellij.openapi.util.Key.create<Boolean>("winrt.manifest.bundled.schema") }
}

@com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)
internal class WinRTManifestBundledSchemas(private val project: com.intellij.openapi.project.Project) {
    private val files = java.util.concurrent.ConcurrentHashMap<String, XmlFile>()
    fun get(namespace: String): XmlFile? {
        val schema = texts[namespace] ?: return null
        return files.computeIfAbsent(namespace) {
            (PsiFileFactory.getInstance(project).createFileFromText(schema.first, XMLLanguage.INSTANCE, schema.second) as XmlFile)
                .also { it.putUserData(WinRTManifestSchemaProvider.BUNDLED, true) }
        }
    }
    companion object {
        internal fun sources(): Map<String, String> = texts.mapValues { it.value.second }
        // SDK Include/winrt omits these namespaces. The vendored Microsoft MSIX
        // schemas retain their original definitions and accompanying MIT license.
        private val texts by lazy {
            listOf("RestrictedCapabilitiesManifestSchema.xsd", "RestrictedCapabilitiesManifestSchema_v2.xsd",
                "WindowsCapabilitiesManifestSchema.xsd", "WindowsCapabilitiesManifestSchema_v2.xsd").mapNotNull { name ->
                val text = WinRTManifestBundledSchemas::class.java.getResourceAsStream("/manifest-schema/$name")
                    ?.bufferedReader()?.use { it.readText() } ?: return@mapNotNull null
                Regex("targetNamespace=\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.let { it to (name to text) }
            }.toMap()
        }
    }
}
