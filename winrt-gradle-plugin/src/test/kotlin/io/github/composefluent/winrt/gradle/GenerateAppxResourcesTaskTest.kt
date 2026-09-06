package io.github.composefluent.winrt.gradle

import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateAppxResourcesTaskTest {
    @Test
    fun renders_appx_resource_accessors_with_kotlinpoet() {
        val source = renderAppxResourcesSource(
            packageName = "sample.appx",
            inputs = listOf(
                AppxResourceInput(Path.of("logo"), Path.of("Assets/Square44x44Logo.png")),
                AppxResourceInput(Path.of("custom"), Path.of("Assets/Logo one.png")),
            ),
            targetSourceSet = "winuiJvmMain",
        )

        assertTrue(source.contains("public object AppxRes"))
        assertTrue(source.contains("public object Assets"))
        assertTrue(source.contains("Square44x44LogoPng"))
        assertTrue(source.contains("\"Assets/Square44x44Logo.png\""))
        assertTrue(source.contains("LogoOnePng"))
        assertTrue(source.contains("\"Assets/Logo%20one.png\""))
        assertTrue(source.contains("Uri(\"ms-appx:///\" + encodedPath)"))
    }
}
