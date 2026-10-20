package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.XamlFramework
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Portable generation inputs, not generated SDK bodies. Corresponds to CsWinRT 3's
 * projection modules consumed by InteropGenerator.Discover/Emit. Module identity is
 * supplied by the Native publication, independently of the selected implementation. */
object NativeProjectionRecipe {
    fun write(
        directory: Path,
        model: WinRTMetadataModel,
        context: WinRTMetadataProjectionContext,
        suppressedTypes: Set<String>,
        suppressedAdditions: Set<String>,
        authoredTypes: Set<String>,
        owner: String,
    ) {
        // CsWinRT's reference modules already enumerate their declarations. Record
        // the producer's declaration inventory while computing its existing shard
        // count, so consumers do not build every SDK member/ABI plan just for names.
        val declarations = KotlinProjectionPlanner().plan(model, context)
        WinRTMetadataModelCodec.writeAtomic(directory.resolve("model.json"), model)
        val settings = buildJsonObject {
            put("schema", 1)
            put("owner", owner)
            put("xamlFramework", context.xamlFramework.name)
            put("windowsSdk", context.sources.any { it is WinRTMetadataSource.WindowsSdk })
            put("component", context.component)
            put("abiSupportShardCount", projectionAbiSupportShardCount(declarations.size))
            fun names(key: String, values: Set<String>) = put(key, JsonArray(values.sorted().map(::JsonPrimitive)))
            names("declarationTypes", declarations.mapTo(linkedSetOf()) { it.type.qualifiedName })
            names("suppressedTypes", suppressedTypes)
            names("include", context.include)
            names("exclude", context.exclude)
            names("suppressedAdditions", suppressedAdditions)
            names("authoredTypes", authoredTypes)
            names("excludedTypes", context.excludedTypes)
            names("additionExclude", context.additionExclude)
            names("inaccessibleDependencyTypes", context.inaccessibleDependencyTypes)
        }
        Files.writeString(directory.resolve("recipe.json"), settings.toString())
    }

    fun read(directory: Path): Recipe {
        val settings = Json.parseToJsonElement(Files.readString(directory.resolve("recipe.json"))).jsonObject
        require(settings.getValue("schema").jsonPrimitive.int == 1) { "Unsupported Native projection recipe" }
        fun names(key: String) = settings.getValue(key).jsonArray.map { it.jsonPrimitive.content }.toSet()
        val model = WinRTMetadataModelCodec.read(directory.resolve("model.json"))
        val declarations = settings["declarationTypes"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
        require(declarations == null || model.namespaces.flatMap { it.types }.mapTo(hashSetOf()) { it.qualifiedName }
            .containsAll(declarations)) { "Native declaration inventory contains types absent from its metadata model" }
        return Recipe(
            model,
            WinRTMetadataProjectionContext(
                sources = if (settings.getValue("windowsSdk").jsonPrimitive.boolean)
                    listOf(WinRTMetadataSource.WindowsSdk()) else emptyList(),
                xamlFramework = XamlFramework.valueOf(settings.getValue("xamlFramework").jsonPrimitive.content),
                component = settings.getValue("component").jsonPrimitive.boolean,
                include = names("include"),
                exclude = names("exclude"),
                excludedTypes = names("excludedTypes"),
                additionExclude = names("additionExclude"),
                inaccessibleDependencyTypes = names("inaccessibleDependencyTypes"),
            ), names("suppressedTypes"), names("suppressedAdditions"), names("authoredTypes"),
            settings.getValue("owner").jsonPrimitive.content,
            settings.getValue("abiSupportShardCount").jsonPrimitive.int,
            declarations,
        )
    }

    data class Recipe(
        val model: WinRTMetadataModel,
        val context: WinRTMetadataProjectionContext,
        val suppressedTypes: Set<String>,
        val suppressedAdditions: Set<String>,
        val authoredTypes: Set<String>,
        val owner: String,
        val abiSupportShardCount: Int,
        private val declarationTypes: Set<String>? = null,
    ) {
        // The model also contains runtime-owned mappings (DateTime, IReference, etc.).
        // Reuse declaration planning so those shapes never become SDK implementation owners.
        val ownedTypes: Set<String> by lazy {
            (declarationTypes ?: KotlinProjectionPlanner().plan(model, context).map { it.type.qualifiedName }.toSet()) -
                suppressedTypes - authoredTypes
        }

        fun generateBusinessOverlays(output: Path) = KotlinProjectionGenerator(
            emitSupportFiles = true, projectionContext = context,
            suppressedSourceAdditionTypeNames = suppressedAdditions,
            resolveSupplementalMetadata = false,
        ).generateBusinessOverlaysTo(model, output)

        fun generate(closure: WinRTMetadataModel, output: Path, staticMembers: Set<String>? = null,
            abiOnlyInterfaces: Set<String> = emptySet(),
            instanceDemand: NativeInstanceMemberDemand? = null): KotlinProjectionWriteSummary {
            val selected = closure.namespaces.flatMap { it.types }.map { it.qualifiedName }.toSet()
            // Each owner keeps its original shape/context. A higher SDK's metadata must
            // not introduce source additions or change declarations in a lower SDK owner.
            val selectedModel = WinRTMetadataModel(model.namespaces.mapNotNull { namespace ->
                namespace.types.filter { it.qualifiedName in selected }.takeIf { it.isNotEmpty() }
                    ?.let { WinRTNamespace(namespace.name, it) }
            }, windowsSdkSelections = model.windowsSdkSelections)
            return KotlinProjectionGenerator(
                emitSupportFiles = true,
                projectionContext = context,
                suppressedProjectionTypeNames = selected - ownedTypes,
                suppressedSourceAdditionTypeNames = suppressedAdditions,
                authoredRuntimeClassNames = authoredTypes,
                supportOwnerIdentity = owner,
                emitJvmAuthoringHostExports = false,
                abiSupportShardCountOverride = abiSupportShardCount,
                nativeStaticMemberDemand = staticMembers,
                nativeAbiOnlyInterfaces = abiOnlyInterfaces,
                nativeInstanceMemberDemand = instanceDemand,
                resolveSupplementalMetadata = false,
            ).generateTo(selectedModel, output)
        }
    }

    /** Empty demand means empty implementation, unlike an empty user selection filter. */
    fun closure(recipes: List<Recipe>, roots: Set<String>): WinRTMetadataModel {
        if (roots.isEmpty()) return WinRTMetadataModel(emptyList())
        val model = WinRTMetadataModel(recipes.flatMap { it.model.namespaces }).normalized()
        return model.filterProjectionSurface(types = roots,
            additionalTypeReferences = ::redirectedWinUIXamlProjectionSurfaceTypeReferences)
    }
}
