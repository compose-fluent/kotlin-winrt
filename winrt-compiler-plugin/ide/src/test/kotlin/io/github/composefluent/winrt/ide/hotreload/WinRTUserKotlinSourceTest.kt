package io.github.composefluent.winrt.ide.hotreload

import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

class WinRTUserKotlinSourceTest {
    @Test fun imported_generated_root_cannot_reclassify_build_output_as_user_code() {
        val project = Path.of("workspace/app")
        for (directory in listOf("build", "Build", ".gradle", ".kotlin")) {
            val generated = project.resolve("$directory/generated/kotlin")
            assertFalse(isWinRTUserKotlinSource(generated.resolve("Generated.kt"), listOf(project), listOf(generated)))
        }
        val user = project.resolve("src/winuiMain/kotlin")
        assertTrue(isWinRTUserKotlinSource(user.resolve("MainWindow.kt"), listOf(project), listOf(user)))
    }
    @Test fun owning_module_exclusions_override_another_modules_imported_source_root() {
        val root = Path.of("workspace")
        val dependency = root.resolve("controls")
        val generated = dependency.resolve("build/generated/kotlin")
        assertFalse(isWinRTUserKotlinSource(generated.resolve("Generated.kt"), listOf(root, dependency), listOf(generated)))
        val nestedProject = root.resolve(".gradle/fixture/app")
        assertTrue(isWinRTUserKotlinSource(nestedProject.resolve("src/Window.kt"), listOf(root, nestedProject), emptyList()))
    }
    @Test fun external_user_sources_and_projects_inside_gradle_fixtures_still_trigger_rebuilds() {
        val project = Path.of("workspace/.gradle/fixture/app")
        val external = Path.of("workspace/shared/kotlin")
        assertTrue(isWinRTUserKotlinSource(project.resolve("src/MainWindow.kt"), listOf(project), listOf(external)))
        assertTrue(isWinRTUserKotlinSource(external.resolve("Model.kt"), listOf(project), listOf(external)))
        assertFalse(isWinRTUserKotlinSource(external.resolve("build/Generated.kt"), listOf(project), listOf(external)))
        assertFalse(isWinRTUserKotlinSource(Path.of("workspace/unrelated/Other.kt"), listOf(project), listOf(external)))
    }
}
