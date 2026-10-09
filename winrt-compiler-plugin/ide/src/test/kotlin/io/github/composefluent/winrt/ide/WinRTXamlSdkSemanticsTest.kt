package io.github.composefluent.winrt.ide

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalogService
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import java.nio.file.Files
import java.nio.file.Path

/** A restored SDK and actual Gallery control exercise DirectUI2010Paths and
 * instance/attachable property elements; miniature WinMD fixtures cannot cover
 * the SDK's inheritance and interface closure. Requires winrt.ide.xamlInput. */
@OptIn(KaAllowAnalysisOnEdt::class)
class WinRTXamlSdkSemanticsTest : BasePlatformTestCase() {
    private var importedRoot: String? = null
    fun testGalleryTemplatesVisualStatesAndStoryboardsResolveWithTheRestoredSdk() {
        val checkout = Path.of(System.getProperty("winrt.ide.toolchain"))
        val source = Files.readString(checkout.resolve("winui-gallery/src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/controls/HorizontalScrollContainer.xaml"))
        val file = myFixture.configureByText("Templates.xaml", source.substringBefore("\n    <Grid>") + "\n</UserControl>") as XmlFile
        val root = file.virtualFile.parent.path.also { importedRoot = it }
        val input = System.getProperty("winrt.ide.xamlInput")!!
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(WinRTModuleData(":", root, "$root/build", "", "",
            emptyList(), emptyList(), emptyList(), emptyList(), listOf(WinRTXamlCompilationData("analyze", listOf(root), "", input, "", "")))))
        val catalogs = project.service<WinRTXamlCatalogService>()
        PlatformTestUtil.waitWithEventsDispatching("Restored WinUI metadata", { catalogs.forFile(file.virtualFile.path) != null }, 30)
        val catalog = catalogs.forFile(file.virtualFile.path)!!
        val family = setOf("ControlTemplate", "VisualState", "VisualStateGroup", "VisualStateManager", "Storyboard",
            "ObjectAnimationUsingKeyFrames", "DiscreteObjectKeyFrame")
        for (name in family) assertNotNull(name, catalog.resolve(WinRTXamlCatalog.PRESENTATION, name))
        val tags = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filter {
            it.localName.substringBefore('.') in family }
        for (tag in tags) assertNotNull(tag.name, allowAnalysisOnEdt { tag.descriptor?.declaration })
        myFixture.enableInspections(com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection())
        val errors = allowAnalysisOnEdt { myFixture.doHighlighting() }.filter { it.severity == HighlightSeverity.ERROR }
        assertEmpty(errors.map { "${file.text.substring(it.startOffset, it.endOffset)}: ${it.description}" })
    }

    override fun tearDown() {
        try { importedRoot?.let { project.service<WinRTProjectService>().replaceBuildModels(it, emptyList()) } }
        finally { super.tearDown() }
    }
}
