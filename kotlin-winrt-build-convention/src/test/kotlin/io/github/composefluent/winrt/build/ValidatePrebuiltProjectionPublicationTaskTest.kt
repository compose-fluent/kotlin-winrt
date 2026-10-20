package io.github.composefluent.winrt.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ValidatePrebuiltProjectionPublicationTaskTest {
    @Test
    fun native_api_pom_accepts_root_and_target_coordinates() {
        // The Native reference variant may preserve a KMP root dependency;
        // rich module metadata still selects its target-specific artifact.
        listOf("projection-api", "projection-api-mingwx64").forEach { artifact ->
            val failure = validateNativePom(artifact)
            assertTrue(failure?.message.orEmpty(), failure == null)
        }
    }

    @Test
    fun native_root_api_pom_still_rejects_runtime_and_optional_dependencies() {
        val runtimeFailure = validateNativePom("projection-api", scope = "runtime")
        assertTrue(runtimeFailure?.message.orEmpty(), runtimeFailure?.message.orEmpty().contains("compile/API"))
        val optionalFailure = validateNativePom("projection-api", optional = true)
        assertTrue(optionalFailure?.message.orEmpty(), optionalFailure?.message.orEmpty().contains("optional"))
    }

    @Test
    fun native_pom_rejects_compile_only_root_and_target_coordinates() {
        listOf("windows-sdk", "windows-sdk-mingwx64").forEach { artifact ->
            val failure = validateNativePom(artifact, forbidden = true)
            assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("forbidden compile-only"))
        }
    }

    private fun validateNativePom(
        artifact: String,
        scope: String = "compile",
        optional: Boolean = false,
        forbidden: Boolean = false,
    ): Throwable? {
        val project = ProjectBuilder.builder().build()
        val pom = Files.createTempDirectory("native-projection-pom-")
            .resolve("publications/mingwX64/pom-default.xml")
        Files.createDirectories(pom.parent)
        Files.writeString(pom, """
            <project><artifactId>projection-mingwx64</artifactId><dependencies><dependency>
              <artifactId>$artifact</artifactId><scope>$scope</scope><optional>$optional</optional>
            </dependency></dependencies></project>
        """.trimIndent())
        val task = project.tasks.create("validateNativePom", ValidatePrebuiltProjectionPublicationTask::class.java)
        task.pomFiles.from(pom.toFile())
        task.requiredApiDependencies.set(mapOf("projection" to if (forbidden) "" else "projection-api"))
        if (forbidden) task.forbiddenPublishedDependencies.set(mapOf("projection" to "windows-sdk"))
        return runCatching { task.validate() }.exceptionOrNull()
    }

    @Test
    fun publication_validation_accepts_api_poms_and_metadata() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-prebuilt-publication-")
        val pomFiles = listOf(
            "jvm" to "projection-api-jvm",
            "mingwX64" to "projection-api-mingwx64",
            "kotlinMultiplatform" to "projection-api",
        ).map { (publicationName, artifactId) ->
            publicationRoot.resolve("publications/$publicationName/pom-default.xml").also { path ->
                Files.createDirectories(path.parent)
                Files.writeString(
                    path,
                    """
                    <project><artifactId>projection${if (publicationName == "kotlinMultiplatform") "" else if (publicationName == "jvm") "-jvm" else "-mingwx64"}</artifactId><dependencies><dependency>
                      <artifactId>$artifactId</artifactId>
                      <scope>compile</scope>
                    </dependency></dependencies></project>
                    """.trimIndent(),
                )
            }.toFile()
        }
        val moduleMetadata = publicationRoot.resolve("publications/kotlinMultiplatform/module.json")
        Files.writeString(
            moduleMetadata,
            """
            {
              "component": { "module": "projection" },
              "variants": [{
                "name": "metadataApiElements",
                "attributes": { "org.gradle.usage": "kotlin-metadata" },
                "dependencies": [{ "module": "projection-api" }]
              }]
            }
            """.trimIndent(),
        )
        val task = project.tasks.create(
            "validatePrebuiltPublicationUnderTest",
            ValidatePrebuiltProjectionPublicationTask::class.java,
        )
        task.pomFiles.from(pomFiles)
        task.moduleMetadataFiles.from(moduleMetadata.toFile())
        task.requiredApiDependencies.set(mapOf("projection" to "projection-api"))

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }

    @Test
    fun publication_validation_rejects_runtime_scope_pom_dependency() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-prebuilt-publication-runtime-scope-")
        val pom = publicationRoot.resolve("publications/jvm/pom-default.xml")
        Files.createDirectories(pom.parent)
        Files.writeString(
            pom,
            """
            <project><artifactId>projection-jvm</artifactId><dependencies><dependency>
              <artifactId>projection-api-jvm</artifactId>
              <scope>runtime</scope>
            </dependency></dependencies></project>
            """.trimIndent(),
        )
        val task = project.tasks.create(
            "validatePrebuiltPublicationRuntimeScopeUnderTest",
            ValidatePrebuiltProjectionPublicationTask::class.java,
        )
        task.pomFiles.from(pom.toFile())
        task.requiredApiDependencies.set(mapOf("projection" to "projection-api"))

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("compile/API"))
    }

    @Test
    fun publication_validation_rejects_missing_expected_api_dependency() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-prebuilt-publication-missing-api-")
        val pom = publicationRoot.resolve("publications/kotlinMultiplatform/pom-default.xml")
        Files.createDirectories(pom.parent)
        Files.writeString(
            pom,
            """
            <project><artifactId>projection</artifactId><dependencies><dependency>
              <artifactId>projection-api</artifactId>
              <scope>compile</scope>
            </dependency></dependencies></project>
            """.trimIndent(),
        )
        val task = project.tasks.create(
            "validatePrebuiltPublicationMissingApiUnderTest",
            ValidatePrebuiltProjectionPublicationTask::class.java,
        )
        task.pomFiles.from(pom.toFile())
        task.requiredApiDependencies.set(
            mapOf("projection" to "projection-api,projection-webview2"),
        )

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("projection-webview2"))
    }

    @Test
    fun publication_validation_rejects_metadata_dependency_outside_api_usage() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-prebuilt-publication-metadata-api-")
        val moduleMetadata = publicationRoot.resolve("publications/kotlinMultiplatform/module.json")
        Files.createDirectories(moduleMetadata.parent)
        Files.writeString(
            moduleMetadata,
            """
            {
              "component": { "module": "projection" },
              "variants": [
                {
                  "name": "metadataApiElements",
                  "attributes": { "org.gradle.usage": "kotlin-metadata" },
                  "dependencies": []
                },
                {
                  "name": "metadataRuntimeElements",
                  "attributes": { "org.gradle.usage": "kotlin-runtime" },
                  "dependencies": [{ "module": "projection-api" }]
                },
                {
                  "name": "kotlinWinRTIdentityElements",
                  "attributes": { "org.gradle.usage": "kotlin-winrt-identity" },
                  "dependencies": [{ "module": "projection-api" }]
                }
              ]
            }
            """.trimIndent(),
        )
        val task = project.tasks.create(
            "validatePrebuiltPublicationMetadataApiUnderTest",
            ValidatePrebuiltProjectionPublicationTask::class.java,
        )
        task.moduleMetadataFiles.from(moduleMetadata.toFile())
        task.requiredApiDependencies.set(mapOf("projection" to "projection-api"))

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("metadataApiElements"))
    }

    @Test
    fun publication_validation_rejects_compile_only_dependency_leaking_into_pom_or_metadata() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-prebuilt-publication-forbidden-dependency-")
        val pom = publicationRoot.resolve("publications/jvm/pom-default.xml")
        Files.createDirectories(pom.parent)
        Files.writeString(
            pom,
            """
            <project><artifactId>projection-jvm</artifactId><dependencies><dependency>
              <artifactId>windows-sdk-jvm</artifactId>
              <scope>compile</scope>
            </dependency></dependencies></project>
            """.trimIndent(),
        )
        val moduleMetadata = publicationRoot.resolve("publications/kotlinMultiplatform/module.json")
        Files.createDirectories(moduleMetadata.parent)
        Files.writeString(
            moduleMetadata,
            """
            {
              "component": { "module": "projection" },
              "variants": [{
                "name": "metadataApiElements",
                "attributes": { "org.gradle.usage": "kotlin-metadata" },
                "dependencies": [{ "module": "windows-sdk" }]
              }]
            }
            """.trimIndent(),
        )
        val task = project.tasks.create(
            "validatePrebuiltPublicationForbiddenDependencyUnderTest",
            ValidatePrebuiltProjectionPublicationTask::class.java,
        )
        task.pomFiles.from(pom.toFile())
        task.moduleMetadataFiles.from(moduleMetadata.toFile())
        task.requiredApiDependencies.set(mapOf("projection" to ""))
        task.forbiddenPublishedDependencies.set(mapOf("projection" to "windows-sdk"))

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("windows-sdk"))
    }
}
