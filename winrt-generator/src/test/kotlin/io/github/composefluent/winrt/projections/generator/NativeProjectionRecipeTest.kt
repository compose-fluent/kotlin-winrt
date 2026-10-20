package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class NativeProjectionRecipeTest {
    @Test fun abi_only_static_interface_retains_declarations_and_slots_without_object_proxy() {
        val statics = WinRTTypeDefinition("App", "IStatics", WinRTTypeKind.Interface, isExclusiveTo = true,
            iid = Guid("11111111-2222-3333-4444-555555555569"),
            methods = listOf(WinRTMethodDefinition("Read", "Int32", methodRowId = 1)))
        val tools = WinRTTypeDefinition("App", "Tools", WinRTTypeKind.RuntimeClass, isStaticType = true,
            activation = WinRTActivationShape(staticInterfaceNames = listOf(statics.qualifiedName)))
        val model = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(statics, tools))))
        val files = KotlinProjectionGenerator(nativeAbiOnlyInterfaces = model.staticFactoryInterfacesWithoutProjectedUses(setOf(tools.qualifiedName)))
            .generate(model).associate { it.relativePath to it.contents }
        val contract = files.getValue("app/IStatics.kt")
        assertTrue(contract, contract.contains("fun read(): Int"))
        assertTrue(contract, contract.contains("READ_SLOT: Int = 6"))
        assertTrue(contract, contract.contains("val IID:"))
        assertFalse(contract, contract.contains("class NativeProjection"))
        assertFalse(contract, contract.contains("fun wrap("))
        assertTrue(files.getValue("app/Tools.kt").contains("fun read("))
    }

    @Test fun static_member_demand_preserves_contract_slots_and_overload_families() {
        // CsWinRT Discover excludes SDK projections as roots and separately discovers
        // exposed interface shapes. Static-wrapper slicing is the Native adaptation:
        // DLL public roots survive optimization, but ABI shapes must remain complete.
        val statics = WinRTTypeDefinition("App", "IToolsStatics", WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555563"), methods = listOf(
                WinRTMethodDefinition("Unused", "Int32", methodRowId = 10),
                WinRTMethodDefinition("Read", "Int32", methodRowId = 11),
                WinRTMethodDefinition("Read", "Int32", methodRowId = 12,
                    parameters = listOf(WinRTParameterDefinition("value", "Int32"))),
            ), properties = listOf(
                WinRTPropertyDefinition("Setting", "Int32", getterMethodName = "get_Setting",
                    setterMethodName = "put_Setting", getterMethodRowId = 13, setterMethodRowId = 14),
                WinRTPropertyDefinition("UnusedSetting", "Int32", getterMethodName = "get_UnusedSetting", getterMethodRowId = 15),
            ), events = listOf(WinRTEventDefinition("Changed", "App.Handler",
                addMethodName = "add_Changed", removeMethodName = "remove_Changed",
                addMethodRowId = 16, removeMethodRowId = 17)))
        val tools = WinRTTypeDefinition("App", "Tools", WinRTTypeKind.RuntimeClass,
            isStaticType = true, activation = WinRTActivationShape(staticInterfaceNames = listOf(statics.qualifiedName)))
        val handler = WinRTTypeDefinition("App", "Handler", WinRTTypeKind.Delegate,
            iid = Guid("11111111-2222-3333-4444-555555555564"),
            methods = listOf(WinRTMethodDefinition("Invoke", "Unit", methodRowId = 18)))
        val model = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(statics, tools, handler))))
        fun generate(demand: Set<String>?) = KotlinProjectionGenerator(nativeStaticMemberDemand = demand)
            .generate(model).associate { it.relativePath to it.contents }
        val full = generate(null)
        val selected = generate(setOf("App.Tools\tread", "App.Tools\tsetting"))
        val source = selected.getValue("app/Tools.kt")
        assertFalse(source, source.contains("fun unused("))
        assertEquals(source, 2, Regex("fun read\\(").findAll(source).count())
        assertTrue(source, source.contains("var setting:"))
        assertTrue(source, source.contains("set(`value`)"))
        assertFalse(source, source.contains("val unusedSetting:"))
        assertEquals("Interface ABI layout and all callbacks remain intact",
            full.getValue("app/IToolsStatics.kt"), selected.getValue("app/IToolsStatics.kt"))
        assertEquals(full, generate(setOf("App.Tools\t*")))
        assertFalse(generate(emptySet()).getValue("app/Tools.kt").contains("fun read("))
        assertFalse(source, source.contains("fun addChanged("))
        val eventSource = generate(setOf("App.Tools\tremoveChanged")).getValue("app/Tools.kt")
        assertTrue(eventSource, eventSource.contains("fun addChanged("))
        assertTrue(eventSource, eventSource.contains("fun removeChanged("))
        assertTrue(eventSource, eventSource.contains("val changed:"))
        // Static helper discovery follows the same complete event family as the
        // emitted wrappers, including a use through removeChanged alone.
        val planner = KotlinProjectionPlanner()
        val plans = planner.plan(model)
        fun helpers(demand: Set<String>?) = planner.eventSourceDescriptors(model, model.semanticHelpers(), plans,
            nativeInstanceMemberDemand = NativeInstanceMemberDemand.create(model, emptySet(), emptySet(), emptySet(),
                demand, setOf(statics.qualifiedName)), nativeStaticMemberDemand = demand)
        assertTrue(helpers(emptySet()).isEmpty())
        assertTrue(helpers(setOf("App.Tools\tremoveChanged")).isNotEmpty())
        assertEquals(helpers(null), helpers(setOf("App.Tools\t*")))
    }

    @Test fun demand_closes_over_signatures_without_moving_dependency_ownership() {
        // CsWinRT 3 InteropGenerator.Discover harvests business uses while the
        // projection modules supply shape information. ImplGenerator preserves owners.
        val shared = WinRTTypeDefinition("Shared", "State", WinRTTypeKind.Enum, enumUnderlyingType = WinRTIntegralType.Int32)
        val used = WinRTTypeDefinition("App", "IUsed", WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555561"),
            methods = listOf(WinRTMethodDefinition("Read", "Shared.State", methodRowId = 1)))
        val unused = WinRTTypeDefinition("App", "IUnused", WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555562"))
        val dateTime = WinRTTypeDefinition("Windows.Foundation", "DateTime", WinRTTypeKind.Struct,
            fields = listOf(WinRTFieldDefinition("UniversalTime", "Int64")))
        val sharedModel = WinRTMetadataModel(listOf(WinRTNamespace("Shared", listOf(shared)),
            WinRTNamespace("Windows.Foundation", listOf(dateTime))),
            windowsSdkSelections = listOf(WinRTWindowsSdkSelection("10.0.26100.0", listOf(
                WinRTWindowsSdkContract("Windows.Foundation.UniversalApiContract", "19.0.0.0")))))
        val appModel = WinRTMetadataModel(sharedModel.namespaces + WinRTNamespace("App", listOf(used, unused)))
        val root = Files.createTempDirectory("native-recipe-test")
        try {
            NativeProjectionRecipe.write(root.resolve("shared"), sharedModel,
                WinRTMetadataProjectionContext(emptyList(), include = setOf("Shared"), exclude = setOf("Other")),
                emptySet(), emptySet(), emptySet(), "shared.dll")
            NativeProjectionRecipe.write(root.resolve("app"), appModel,
                WinRTMetadataProjectionContext(emptyList()), setOf("Shared.State"), emptySet(), emptySet(), "app.dll")
            val recipes = listOf("shared", "app").map { NativeProjectionRecipe.read(root.resolve(it)) }
            assertEquals(19, recipes.first().model.universalApiContractMajorVersion)
            assertEquals(setOf("Shared"), recipes.first().context.include)
            assertEquals(setOf("Other"), recipes.first().context.exclude)
            assertFalse("Runtime mappings are not SDK implementation owners",
                "Windows.Foundation.DateTime" in recipes.first().ownedTypes)
            // The declaration inventory is an optional producer-side acceleration.
            // Old recipes retain the exact same ownership; suppression still belongs
            // to each owner, rather than a consumer rebuilding SDK ABI plans.
            val recipeFile = root.resolve("shared/recipe.json")
            val saved = Files.readString(recipeFile)
            val json = kotlinx.serialization.json.Json.parseToJsonElement(saved) as kotlinx.serialization.json.JsonObject
            Files.writeString(recipeFile, kotlinx.serialization.json.JsonObject(json - "declarationTypes").toString())
            assertEquals(recipes.first().ownedTypes, NativeProjectionRecipe.read(root.resolve("shared")).ownedTypes)
            Files.writeString(recipeFile, saved)
            assertTrue(NativeProjectionRecipe.closure(recipes, emptySet()).namespaces.isEmpty())
            val closure = NativeProjectionRecipe.closure(recipes, setOf("App.IUsed"))
            assertEquals(setOf("App.IUsed", "Shared.State"), closure.namespaces.flatMap { it.types }.map { it.qualifiedName }.toSet())
            recipes[1].generate(closure, root.resolve("out"))
            val names = root.resolve("out").toFile().walkTopDown().filter { it.extension == "kt" }.map { it.name }.toSet()
            assertTrue(names.toString(), "IUsed.kt" in names)
            assertFalse(names.toString(), "IUnused.kt" in names)
            assertFalse("A dependency-owned type must not be regenerated in its consumer", "State.kt" in names)
        } finally { root.toFile().deleteRecursively() }
    }
}
