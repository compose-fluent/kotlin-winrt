package io.github.composefluent.winrt.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ValidatePrebuiltProjectionPublicationTaskTest {
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
