package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTMetadataSourceResolver
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.projections.generator.redirectedWinAppSdkProjectionSurfaceTypeReferences
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Runs only in the bounded generator JVM, never in Gradle's configuration daemon. */
object PreparedProjectionGeneratorMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected a static projection request file." }
        val requestFile = Path.of(args.single())
        val request = Properties().apply { Files.newInputStream(requestFile).use(::load) }
        fun value(name: String): String = requireNotNull(request.getProperty(name)) { "Missing $name" }
        fun values(name: String): List<String> = value(name).takeIf(String::isNotEmpty)?.split('\u0000').orEmpty()
        val registryRoots = values("registryRoots").map(Path::of).takeIf(List<Path>::isNotEmpty)
        val sources = values("sources").map(WinRTMetadataSource::parse).mapIndexed { index, source ->
            when (source) {
                is WinRTMetadataSource.WindowsSdk -> source.copy(registryRoots = registryRoots)
                is WinRTMetadataSource.NuGetPackageReference -> source.copy(globalPackagesRoots = values("nugetRoots.$index").map(Path::of))
                else -> source
            }
        }
        val cache = WinRTMetadataSourceResolver.resolve(sources)
        val model = cache.load(Path.of(value("modelCache")))
        val identityFiles = values("identities").map(::File)
        val includeTypes = values("includeTypes")
        val effectiveIncludeTypes = includeTypes + automaticXamlComponentResourceDictionaryTypes(model, includeTypes.toSet())
        val dependencySurfaceTypes = dependencyProjectionSurfaceTypeNames(identityFiles)
        val staticModel = if (value("packagingOnly").toBooleanStrict()) {
            WinRTMetadataModel(emptyList())
        } else {
            model.filterProjectionSurface(
                namespaces = values("includeNamespaces").toSet(),
                types = (effectiveIncludeTypes + dependencySurfaceTypes).toSet(),
                excludedNamespaces = values("excludeNamespaces").toSet(),
                excludedTypes = values("excludeTypes").toSet(),
                additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
            )
        }
        val context = WinRTMetadataProjectionContext(
            sources = sources,
            include = values("includeNamespaces").toSet() + effectiveIncludeTypes.toSet() + dependencySurfaceTypes.toSet(),
            exclude = values("excludeNamespaces").toSet() + values("excludeTypes").toSet(),
            excludedTypes = values("excludeTypes").toSet(),
            additionExclude = values("additionExclude").toSet(),
        )
        // Same metadata/generator responsibility split as .cswinrt/src/cswinrt/main.cpp;
        // process isolation is the Gradle-specific boundary, not a new projection policy.
        KotlinProjectionGenerator(
            emitSupportFiles = true,
            groupProjectionFilesByPackageOnWrite = true,
            projectionContext = context,
            suppressedProjectionTypeNames = dependencyProjectedTypeNames(staticModel, identityFiles),
            suppressedSourceAdditionTypeNames = dependencySourceAdditionTypeNames(identityFiles),
            supportOwnerIdentity = value("owner"),
            emitJvmAuthoringHostExports = value("emitJvmAuthoringHostExports").toBooleanStrict(),
        ).generateTo(staticModel, requestFile.parent.resolve("sources"))
        writePreparedStaticManifest(requestFile.parent.resolve("manifest.tsv"), cache.files, staticModel)
    }
}
