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
}
