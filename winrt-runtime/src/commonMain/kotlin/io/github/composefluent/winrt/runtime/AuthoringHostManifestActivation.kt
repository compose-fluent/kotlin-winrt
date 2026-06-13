package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

internal object AuthoringHostManifestActivation {
    fun tryGet(runtimeClassName: String, interfaceId: Guid): ActivationResult {
        if (!PlatformRuntime.isWindows) {
            return failure()
        }

        for (targetDllName in targetDllNamesFor(runtimeClassName)) {
            val module = DllModule.tryLoad(targetDllName) ?: continue
            val activationFactoryResult = module.getActivationFactory(runtimeClassName)
            if (!activationFactoryResult.isSuccess) {
                if (!isActivationClassUnavailable(activationFactoryResult.hResult)) {
                    return activationFactoryResult
                }
                continue
            }

            if (interfaceId == IID.IActivationFactory) {
                return activationFactoryResult
            }

            return try {
                WinRtPlatformApi.queryInterfaceRaw(activationFactoryResult.pointer, interfaceId).toActivationResult()
            } finally {
                WinRtPlatformApi.releaseRaw(activationFactoryResult.pointer)
            }
        }

        return failure()
    }

    internal fun targetDllNamesFor(runtimeClassName: String): List<String> =
        hostManifestFiles()
            .asSequence()
            .mapNotNull { path -> runCatching { path.readText() }.getOrNull() }
            .mapNotNull { content -> targetArtifactFor(content, runtimeClassName) }
            .filter { target -> target.endsWith(".dll", ignoreCase = true) }
            .distinct()
            .toList()

    private fun targetArtifactFor(content: String, runtimeClassName: String): String? =
        readJsonStringMap(content, "activatableClassTargets")[runtimeClassName]
            ?: readJsonString(content, "targetArtifact")
                ?.takeIf { runtimeClassName in readJsonStringArray(content, "activatableClasses") }

    private fun hostManifestFiles(): List<Path> =
        runtimeAssetsRootCandidates()
            .flatMap { root -> Path(root).walkFiles() }
            .filter { path -> path.fileName.endsWith(".host.json", ignoreCase = true) }
            .distinctBy { path -> path.canonicalString() }

    private fun runtimeAssetsRootCandidates(): List<String> =
        listOf(
            runtimeAssetsDirectoryName,
            "kotlin-winrt/runtime-assets",
            "build/kotlin-winrt/runtime-assets",
        )

    private fun readJsonString(content: String, name: String): String? =
        Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.decodeJsonString()

    private fun readJsonStringArray(content: String, name: String): List<String> {
        val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(content) ?: return emptyList()
        return Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(match.groupValues[1])
            .map { it.groupValues[1].decodeJsonString() }
            .toList()
    }

    private fun readJsonStringMap(content: String, name: String): Map<String, String> {
        val match = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
            .find(content) ?: return emptyMap()
        return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .findAll(match.groupValues[1])
            .associate { it.groupValues[1].decodeJsonString() to it.groupValues[2].decodeJsonString() }
    }

    private fun String.decodeJsonString(): String =
        replace("\\\"", "\"").replace("\\\\", "\\")

    private fun failure(): ActivationResult =
        ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)

    private const val runtimeAssetsDirectoryName = "kotlin-winrt-runtime-assets"
}
