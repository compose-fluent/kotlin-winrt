package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class NativeDemandPublicationTest {
    @Test fun native_publication_graph_has_no_full_implementation_producer() {
        val project = org.gradle.testfixtures.ProjectBuilder.builder().build()
        project.pluginManager.apply("maven-publish")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val target = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            .mingwX64()
        val implementation = target.compilations.create("winRTProjection")
        val reference = target.compilations.create("winRTProjectionReference")
        reference.compileTaskProvider.configure { it.compilerOptions.moduleName.set("canonical-sdk") }
        val api = project.configurations.getByName(target.apiElementsConfigurationName)
        api.outgoing.artifacts.clear()
        api.outgoing.artifact(nativeProjectionReferenceArtifact(project, reference))
        project.tasks.register("generateWinRTReferenceProjections")
        configureNativeProjectionReferenceVariants(project, target, reference)
        configureNativeDemandVariant(project, target, reference, true)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val visited = mutableSetOf<org.gradle.api.Task>()
        fun visit(task: org.gradle.api.Task) {
            if (visited.add(task)) task.taskDependencies.getDependencies(task).forEach(::visit)
        }
        visit(project.tasks.getByName("generateMetadataFileForMingwX64Publication"))
        api.outgoing.artifacts.forEach { artifact -> artifact.buildDependencies.getDependencies(null).forEach(::visit) }
        assertTrue(reference.compileTaskProvider.get() in visited)
        assertTrue(project.tasks.getByName("mingwX64WinRTNativeRecipe") in visited)
        assertTrue(project.tasks.getByName("mingwX64WinRTProjectionReferenceKlib") in visited)
        assertNull("A pure SDK header must not be mistaken for a business KLIB",
            project.tasks.findByName("mingwX64WinRTNativeUses"))
        assertFalse(implementation.compileTaskProvider.get() in visited)
    }

    @Test fun pure_sdk_metadata_publishes_only_contract_and_recipe_without_implementation_file() {
        // CsWinRTGenerateReferenceProjection uses ProduceOnlyReferenceAssembly;
        // Native publishers must likewise need no implementation compiler output.
        val root = Files.createTempDirectory("native-contract-only-publication").toFile()
        try {
            val metadata = root.resolve("sdk.module").apply { writeText("""
                {"formatVersion":"1.1","variants":[{"name":"nativeApi","attributes":{
                "org.jetbrains.kotlin.platform.type":"native","org.gradle.usage":"kotlin-api"},
                "dependencies":[{"group":"sample","module":"base","version":{"requires":"1"}}],
                "files":[{"name":"sdk-mingwX64Main-1.klib","url":"sdk-1.klib"}]}]}
            """.trimIndent()) }
            val reference = root.resolve("reference.klib").apply { writeText("canonical SDK contract") }
            val recipe = root.resolve("recipe").apply { writeText("recipe") }
            repeat(2) {
                addNativeReferenceMetadataVariant(metadata, reference, "sdk-1-winrt-projection-reference.klib")
                addNativeDemandMetadataVariant(metadata, listOf(recipe to "sdk-1.winrt-recipe"), null)
            }
            val variants = Json.parseToJsonElement(metadata.readText()).jsonObject.getValue("variants").jsonArray
            assertEquals(3, variants.size)
            variants.forEach { variant ->
                val files = variant.jsonObject.getValue("files").jsonArray.map {
                    it.jsonObject.getValue("url").jsonPrimitive.content
                }
                assertEquals(listOf(if (variant.jsonObject.getValue("name").jsonPrimitive.content.endsWith("-winrt-demand"))
                    "sdk-1.winrt-recipe" else "sdk-1-winrt-projection-reference.klib"), files)
                assertEquals(variants.first().jsonObject["dependencies"], variant.jsonObject["dependencies"])
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun clean_native_metadata_publication_waits_for_business_usage_packaging() {
        // Native adaptation of CsWinRT's compiled-reference handoff: publication
        // metadata must hash the produced usage artifact, even on a clean build.
        val project = org.gradle.testfixtures.ProjectBuilder.builder().build()
        project.pluginManager.apply("maven-publish")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            .mingwX64()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val usage = project.tasks.named("mingwX64WinRTNativeUses", PackWinRTNativeUsesTask::class.java).get()
        val metadata = project.tasks.getByName("generateMetadataFileForMingwX64Publication")
        assertFalse(usage.outputFile.get().asFile.exists())
        assertTrue("Native metadata must wait for its usage manifest producer",
            usage in metadata.taskDependencies.getDependencies(metadata))
    }

    @Test fun sliced_owners_compile_dependencies_first_and_reject_cycles() {
        assertEquals(listOf("sdk", "a", "b", "app"), nativeProjectionOwnerOrder(listOf("app", "b", "a", "sdk"),
            mapOf("app" to listOf("b", "a"), "a" to listOf("sdk", "runtime"), "b" to listOf("sdk"))))
        assertEquals(listOf("sdk", "app"), nativeProjectionOwnerOrder(listOf("app", "sdk"),
            mapOf("app" to listOf("bridge"), "bridge" to listOf("sdk"))))
        assertTrue(runCatching {
            nativeProjectionOwnerOrder(listOf("a", "b"), mapOf("a" to listOf("b"), "b" to listOf("a")))
        }.isFailure)
    }
    @Test fun member_only_dependency_roots_its_owner_and_rejects_stale_or_incomplete_manifests() {
        val demand = readNativeSemanticDemand(listOf("kotlin-winrt-native-uses-v4",
            "T\tApp.Button", "M\tDependency.Tools\tread", "M\tDependency.Tools\tread",
            "I\tDependency.IReader\tread", "E\tDependency.Base", "C\tDependency.Constructed"))
        assertEquals(setOf("App.Button", "Dependency.Tools", "Dependency.IReader", "Dependency.Base", "Dependency.Constructed"), demand.types)
        assertEquals(setOf("Dependency.Constructed"), demand.constructionTypes)
        assertEquals(setOf("Dependency.Tools\tread"), demand.staticMembers)
        assertEquals(setOf("Dependency.IReader\tread"), demand.instanceMembers)
        assertEquals(setOf("Dependency.Base"), demand.exposedTypes)
        assertTrue(readNativeSemanticDemand(listOf("kotlin-winrt-native-uses-v4")).types.isEmpty())
        listOf(listOf("kotlin-winrt-native-uses-v1", "Dependency.Tools"),
            listOf("kotlin-winrt-native-uses-v3", "T\tDependency.Tools"),
            listOf("kotlin-winrt-native-uses-v2", "T\tDependency.Tools"),
            listOf("kotlin-winrt-native-uses-v4", "M\tDependency.Tools\t"),
            listOf("kotlin-winrt-native-uses-v4", "M\tDependency.Tools"))
            .forEach { assertTrue(runCatching { readNativeSemanticDemand(it) }.isFailure) }
    }

    @Test fun published_demand_keeps_business_and_dependencies_but_replaces_sdk_implementation() {
        val root = Files.createTempDirectory("native-demand-publication").toFile()
        try {
            val metadata = root.resolve("sdk.module").apply { writeText("""
                {"formatVersion":"1.1","variants":[{"name":"nativeApi","attributes":{
                "org.jetbrains.kotlin.platform.type":"native","org.gradle.usage":"kotlin-api"},
                "dependencies":[{"group":"sample","module":"base","version":{"requires":"1"}}],
                "files":[{"name":"sdk-mingwX64Main-1.klib","url":"sdk-1.klib"},{"name":"sdk-1-winrt-projection.klib"}]}]}
            """.trimIndent()) }
            val recipe = root.resolve("recipe").apply { writeText("recipe") }
            val uses = root.resolve("uses").apply { writeText("uses") }
            val artifacts = listOf(recipe to "sdk-1.winrt-recipe", uses to "sdk-1.winrt-uses")
            repeat(2) { addNativeDemandMetadataVariant(metadata, artifacts, "sdk-1.klib") }
            val variants = Json.parseToJsonElement(metadata.readText()).jsonObject.getValue("variants").jsonArray
            assertEquals(2, variants.size)
            val demand = variants.last().jsonObject
            assertEquals(setOf("sdk-mingwX64Main-1.klib", "sdk-1.winrt-recipe", "sdk-1.winrt-uses"),
                demand.getValue("files").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet())
            assertEquals(variants.first().jsonObject["dependencies"], demand["dependencies"])
            assertEquals("demand", demand.getValue("attributes").jsonObject.getValue(WINRT_NATIVE_PROJECTION_ARTIFACT.name).jsonPrimitive.content)
        } finally { root.deleteRecursively() }
    }
}
