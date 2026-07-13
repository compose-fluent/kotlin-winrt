package io.github.composefluent.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ValidateSplitProjectionPublicationTaskTest {
    @Test
    fun split_projection_publication_validation_accepts_api_poms_and_module_metadata() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-publication-validation-")
        val pomFiles = listOf(
            "jvm" to "winrt-projections-windows-sdk-jvm",
            "mingwX64" to "winrt-projections-windows-sdk-mingwx64",
            "kotlinMultiplatform" to "winrt-projections-windows-sdk",
        ).map { (publicationName, artifactId) ->
            publicationRoot.resolve("publications/$publicationName/pom-default.xml").also { path ->
                Files.createDirectories(path.parent)
                Files.writeString(
                    path,
                    """
                    <project><artifactId>winrt-projections-windows-app-sdk${if (publicationName == "kotlinMultiplatform") "" else if (publicationName == "jvm") "-jvm" else "-mingwx64"}</artifactId><dependencies><dependency>
                      <artifactId>$artifactId</artifactId>
                      <scope>compile</scope>
                    </dependency><dependency>
                      <artifactId>winrt-projections-windows-webview2${if (publicationName == "kotlinMultiplatform") "" else if (publicationName == "jvm") "-jvm" else "-mingwx64"}</artifactId>
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
              "component": { "module": "winrt-projections-windows-app-sdk" },
              "variants": [
                {
                  "name": "metadataApiElements",
                  "attributes": { "org.gradle.usage": "kotlin-metadata" },
                  "dependencies": [
                    { "module": "winrt-projections-windows-sdk" },
                    { "module": "winrt-projections-windows-webview2" }
                  ]
                },
                {
                  "name": "kotlinWinRTIdentityElements",
                  "attributes": { "org.gradle.usage": "kotlin-winrt-identity" },
                  "dependencies": []
                }
              ]
            }
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "validateSplitProjectionPublicationUnderTest",
            ValidateSplitProjectionPublicationTask::class.java,
        ) { registeredTask ->
            registeredTask.pomFiles.from(pomFiles)
            registeredTask.moduleMetadataFiles.from(moduleMetadata.toFile())
            registeredTask.requiredApiDependencies.set(
                mapOf(
                    "winrt-projections-windows-app-sdk" to
                        "winrt-projections-windows-sdk,winrt-projections-windows-webview2",
                ),
            )
        }.get()

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }

    @Test
    fun split_projection_publication_validation_rejects_runtime_only_dependency() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-publication-validation-failure-")
        val pom = publicationRoot.resolve("publications/jvm/pom-default.xml")
        Files.createDirectories(pom.parent)
        Files.writeString(
            pom,
            """
            <project><artifactId>winrt-projections-windows-webview2-jvm</artifactId><dependencies><dependency>
              <artifactId>winrt-projections-windows-sdk-jvm</artifactId>
              <scope>runtime</scope>
            </dependency></dependencies></project>
            """.trimIndent(),
        )
        val moduleMetadata = publicationRoot.resolve("publications/kotlinMultiplatform/module.json")
        Files.createDirectories(moduleMetadata.parent)
        Files.writeString(moduleMetadata, """{"component":{"module":"winrt-projections-windows-webview2"}}""")
        val task = project.tasks.register(
            "validateSplitProjectionPublicationFailureUnderTest",
            ValidateSplitProjectionPublicationTask::class.java,
        ) { registeredTask ->
            registeredTask.pomFiles.from(pom.toFile())
            registeredTask.moduleMetadataFiles.from(moduleMetadata.toFile())
            registeredTask.requiredApiDependencies.set(
                mapOf("winrt-projections-windows-webview2" to "winrt-projections-windows-sdk"),
            )
        }.get()

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("compile/API"))
    }

    @Test
    fun split_projection_publication_validation_rejects_module_dependency_outside_metadata_api_variant() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-publication-metadata-api-failure-")
        val moduleMetadata = publicationRoot.resolve("publications/kotlinMultiplatform/module.json")
        Files.createDirectories(moduleMetadata.parent)
        Files.writeString(
            moduleMetadata,
            """
            {
              "component": { "module": "winrt-projections-windows-webview2" },
              "variants": [
                {
                  "name": "metadataApiElements",
                  "attributes": { "org.gradle.usage": "kotlin-metadata" },
                  "dependencies": []
                },
                {
                  "name": "metadataRuntimeElements",
                  "attributes": { "org.gradle.usage": "kotlin-runtime" },
                  "dependencies": [
                    { "module": "winrt-projections-windows-sdk" }
                  ]
                },
                {
                  "name": "kotlinWinRTIdentityElements",
                  "attributes": { "org.gradle.usage": "kotlin-winrt-identity" },
                  "dependencies": [
                    { "module": "winrt-projections-windows-sdk" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "validateSplitProjectionMetadataApiFailureUnderTest",
            ValidateSplitProjectionPublicationTask::class.java,
        ) { registeredTask ->
            registeredTask.moduleMetadataFiles.from(moduleMetadata.toFile())
            registeredTask.requiredApiDependencies.set(
                mapOf("winrt-projections-windows-webview2" to "winrt-projections-windows-sdk"),
            )
        }.get()

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("metadataApiElements"))
    }

    @Test
    fun split_projection_publication_validation_rejects_missing_webview2_api_dependency() {
        val project = ProjectBuilder.builder().build()
        val publicationRoot = Files.createTempDirectory("kotlin-winrt-webview2-api-failure-")
        val pom = publicationRoot.resolve("publications/kotlinMultiplatform/pom-default.xml")
        Files.createDirectories(pom.parent)
        Files.writeString(
            pom,
            """
            <project>
              <artifactId>winrt-projections-windows-app-sdk</artifactId>
              <dependencies><dependency>
                <artifactId>winrt-projections-windows-sdk</artifactId>
                <scope>compile</scope>
              </dependency></dependencies>
            </project>
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "validateMissingWebView2ApiDependencyUnderTest",
            ValidateSplitProjectionPublicationTask::class.java,
        ) { registeredTask ->
            registeredTask.pomFiles.from(pom.toFile())
            registeredTask.requiredApiDependencies.set(
                mapOf(
                    "winrt-projections-windows-app-sdk" to
                        "winrt-projections-windows-sdk,winrt-projections-windows-webview2",
                ),
            )
        }.get()

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(
            failure?.message.orEmpty(),
            failure?.message.orEmpty().contains("winrt-projections-windows-webview2"),
        )
    }
}
