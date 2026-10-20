package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.*
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import java.io.File
import java.security.MessageDigest

internal val WINRT_NATIVE_PROJECTION_ARTIFACT: Attribute<String> =
    Attribute.of("io.github.composefluent.winrt.native-projection-artifact", String::class.java)

/** Like CsWinRT's ProduceOnlyReferenceAssembly, publication needs no SDK implementation.
 * Final binaries resolve the recipe variant and generate implementations with this identity. */
internal fun configureNativeProjectionReferenceVariants(
    project: Project,
    target: KotlinNativeTarget,
    reference: KotlinNativeCompilation,
) {
    val api = project.configurations.getByName(target.apiElementsConfigurationName)
    val referenceFile = nativeProjectionReferenceArtifact(project, reference)
    val referenceIsComponentArtifact = api.outgoing.artifacts.any {
        it.file == referenceFile.get().asFile && it.classifier == "winrt-projection-reference"
    }
    val hasBusinessArtifact = api.outgoing.artifacts.any { it.file != referenceFile.get().asFile }
    val projectionHeader = referenceFile
    val business = target.compilations.getByName("main")
    val businessHeader = if (hasBusinessArtifact) configureNativeProjectionHeader(project, business) else null
    val businessReference = businessHeader?.let { header ->
        project.tasks.register("${target.name}WinRTMainReference", PackWinRTNativeHeaderTask::class.java) {
            it.header.from(project.files(header).builtBy(business.compileTaskProvider))
            it.outputFile.set(project.layout.buildDirectory.file("libs/${target.name}-winrt-main-reference.klib"))
        }
    }
    val businessReferenceFile = businessReference?.flatMap { it.outputFile }
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    val compilerHome = reference.compileTaskProvider.get().konanHome
    val binaryContract = project.tasks.register("${target.name}WinRTBinaryContract", GenerateWinRTNativeContractTask::class.java) { task ->
        task.headerKlibs.from(project.files(projectionHeader).builtBy(reference.compileTaskProvider))
        businessHeader?.let { task.headerKlibs.from(project.files(it).builtBy(business.compileTaskProvider)) }
        task.compilerClasspath.from(compilerHome.map { File(it, "konan/lib/kotlin-native-compiler-embeddable.jar") })
        task.javaLauncher.set(project.extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java)
            .launcherFor { it.languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(25)) })
        task.kotlinCompilerVersion.set(project.getKotlinPluginVersion())
        task.owner.set(projectionOwnerProvider(project))
        task.producerVersion.set(project.provider { project.version.toString() })
        task.outputFile.set(project.layout.buildDirectory.file("kotlin-winrt/contracts/${target.name}.json"))
    }
    project.configurations.create("${target.name}WinRTReferenceElements") { contract ->
        contract.isCanBeResolved = false
        contract.isCanBeConsumed = true
        // Extending the consumable configuration itself also inherits its implementation
        // artifacts. Copy its dependency parents and direct declarations instead.
        contract.extendsFrom(*api.extendsFrom.toTypedArray())
        contract.withDependencies { dependencies -> api.dependencies.forEach { dependencies.add(it.copy()) } }
        api.dependencyConstraints.forEach { contract.dependencyConstraints.add(it) }
        api.attributes.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST") val typed = key as Attribute<Any>
            contract.attributes.attribute(typed, api.attributes.getAttribute(typed)!!)
        }
        contract.attributes.attribute(WINRT_NATIVE_PROJECTION_ARTIFACT, "reference")
        businessReferenceFile?.let { file ->
            contract.outgoing.artifact(file) {
                it.classifier = "winrt-main-reference"
                it.extension = "klib"
                it.type = "klib"
            }
        }
        contract.outgoing.artifact(referenceFile) {
            it.classifier = "winrt-projection-reference"
            it.extension = "klib"
            it.type = "klib"
            it.builtBy(reference.compileTaskProvider)
        }
        // KGP prefers unpacked project variants. Preserve their business artifact while
        // adding the projection; otherwise that fast path silently drops public types.
        api.outgoing.variants.matching { it.name == "non-packed-klib" }.all { unpacked ->
            // Pure prebuilt projections promote their reference KLIB to the primary
            // artifact. Their NO-SOURCE main compilation must not survive in this variant.
            if (!hasBusinessArtifact) unpacked.artifacts.clear()
            contract.outgoing.variants.create(unpacked.name) { variant ->
                unpacked.attributes.keySet().forEach { key ->
                    @Suppress("UNCHECKED_CAST") val typed = key as Attribute<Any>
                    variant.attributes.attribute(typed, unpacked.attributes.getAttribute(typed)!!)
                }
                variant.attributes.attribute(WINRT_NATIVE_PROJECTION_ARTIFACT, "reference")
                businessHeader?.let { header ->
                    variant.artifact(header) { it.builtBy(business.compileTaskProvider) }
                }
                variant.artifact(referenceFile) { it.builtBy(reference.compileTaskProvider) }
            }
            unpacked.artifact(referenceFile) { it.builtBy(reference.compileTaskProvider) }
        }
    }
    project.pluginManager.withPlugin("maven-publish") {
        project.extensions.getByType(PublishingExtension::class.java).publications
            .withType(MavenPublication::class.java).matching { it.name == target.name }.configureEach { publication ->
                businessReferenceFile?.let { file -> publication.artifact(file) {
                    it.classifier = "winrt-main-reference"
                    it.extension = "klib"
                } }
                publication.artifact(binaryContract.flatMap { it.outputFile }) {
                    it.classifier = "winrt-contract"
                    it.extension = "json"
                }
                // KGP already publishes the classified contract for business libraries.
                // Pure SDKs promote it to the primary artifact and need the named alias.
                if (!referenceIsComponentArtifact) publication.artifact(referenceFile) {
                    it.classifier = "winrt-projection-reference"
                    it.extension = "klib"
                    it.builtBy(reference.compileTaskProvider)
                }
                val metadataTaskName = "generateMetadataFileFor${publication.name.replaceFirstChar(Char::uppercaseChar)}Publication"
                project.tasks.withType(GenerateModuleMetadata::class.java).matching { it.name == metadataTaskName }
                    .configureEach { task ->
                        // KGP does not expose an API to add a usage to its Native component.
                        // Adapt only the standard module-metadata document, without reflecting
                        // into KGP's immutable usage contexts. Both API variants contain contracts.
                        val metadata = task.outputFile
                        val artifactName = "${publication.artifactId}-${publication.version}-winrt-projection-reference.klib"
                        val businessName = "${publication.artifactId}-${publication.version}.klib"
                        val businessReferenceName = "${publication.artifactId}-${publication.version}-winrt-main-reference.klib"
                        businessReferenceFile?.let {
                            task.inputs.file(it).withPropertyName("winrtNativeMainReference")
                                .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.NONE)
                        }
                        task.inputs.file(referenceFile).withPropertyName("winrtNativeReference")
                            .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.NONE)
                        task.inputs.property("winrtNativeReferenceName", artifactName)
                        task.dependsOn(reference.compileTaskProvider)
                        task.doLast {
                            addNativeReferenceMetadataVariant(metadata.get().asFile, referenceFile.get().asFile, artifactName,
                                businessReferenceFile?.get()?.asFile, businessReferenceName, businessName)
                        }
                    }
            }
    }
}

internal fun addNativeReferenceMetadataVariant(
    metadata: File, reference: File, artifactName: String,
    businessReference: File? = null, businessReferenceName: String = "", businessName: String = "",
) {
    val document = Json.parseToJsonElement(metadata.readText()).jsonObject
    check(document["formatVersion"]?.jsonPrimitive?.content == "1.1") { "Unsupported Gradle module metadata format: $metadata" }
    val variants = document.getValue("variants").jsonArray
    val original = variants.map { it.jsonObject }.single { variant ->
        val attributes = variant["attributes"]?.jsonObject
        attributes?.get("org.jetbrains.kotlin.platform.type")?.jsonPrimitive?.content == "native" &&
            attributes["org.gradle.usage"]?.jsonPrimitive?.content == "kotlin-api" &&
            WINRT_NATIVE_PROJECTION_ARTIFACT.name !in attributes && variant["files"] != null
    }
    val file = nativeModuleArtifact(reference, artifactName)
    // KGP may snapshot main before the standalone projection artifact is added.
    // Keep only the real business KLIB and add the independently compiled contract.
    // Pure SDK owners have no main output; never hash or request a full implementation.
    val published = JsonObject(original + ("files" to JsonArray(
        original.getValue("files").jsonArray.filter {
            businessReference != null &&
                (it.jsonObject["url"]?.jsonPrimitive?.content ?: it.jsonObject.getValue("name").jsonPrimitive.content) == businessName
        } + file)))
    val variantName = original.getValue("name").jsonPrimitive.content + "-winrt-reference"
    val contract = JsonObject(published.toMutableMap().apply {
        put("name", JsonPrimitive(variantName))
        put("attributes", JsonObject(original.getValue("attributes").jsonObject +
            (WINRT_NATIVE_PROJECTION_ARTIFACT.name to JsonPrimitive("reference"))))
        put("files", JsonArray(published.getValue("files").jsonArray.map {
            val name = it.jsonObject["url"]?.jsonPrimitive?.content ?: it.jsonObject.getValue("name").jsonPrimitive.content
            when {
                businessReference != null && name == businessName -> nativeModuleArtifact(businessReference, businessReferenceName)
                else -> it
            }
        }))
    })
    val result = JsonObject(document + ("variants" to JsonArray(variants.filterNot {
        it.jsonObject.getValue("name").jsonPrimitive.content == variantName
    }.map { if (it == original) published else it } + contract)))
    metadata.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), result) + "\n")
}

internal fun nativeModuleArtifact(artifact: File, name: String): JsonObject {
    val bytes = artifact.readBytes()
    return buildJsonObject {
        put("name", name); put("url", name); put("size", bytes.size)
        listOf("sha512" to "SHA-512", "sha256" to "SHA-256", "sha1" to "SHA-1", "md5" to "MD5").forEach { (key, algorithm) ->
            put(key, MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) })
        }
    }
}
