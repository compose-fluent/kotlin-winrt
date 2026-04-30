package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class KotlinWinRtAuthoringSourceScannerTest {
    @Test
    fun scans_runtime_class_and_interface_authored_types_without_application_special_case() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-scan-")
        root.resolve("Sample.kt").toFile().writeText(
            """
            package sample

            import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Application
            import io.github.kitectlab.winrt.projections.windows.foundation.IStringable

            internal class App : Application()

            internal class StringableThing : IStringable
            """.trimIndent(),
        )

        val candidates = KotlinWinRtAuthoringSourceScanner.scan(listOf(root), model())

        assertEquals(
            listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "StringableThing",
                    sourceTypeName = "sample.StringableThing",
                    winRtBaseClassName = null,
                    winRtInterfaceNames = listOf("Windows.Foundation.IStringable"),
                    overridableInterfaceNames = emptyList(),
                ),
            ),
            candidates,
        )
    }

    private fun model(): WinRtMetadataModel =
        WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.IApplicationOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationOverrides",
                            kind = WinRtTypeKind.Interface,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IStringable",
                            kind = WinRtTypeKind.Interface,
                        ),
                    ),
                ),
            ),
        )
}
