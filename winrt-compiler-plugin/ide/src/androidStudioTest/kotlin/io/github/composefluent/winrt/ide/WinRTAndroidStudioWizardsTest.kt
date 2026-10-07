package io.github.composefluent.winrt.ide

import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.project.AndroidProjectEntryProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.templates.WinRTAndroidModuleDescriptionProvider
import io.github.composefluent.winrt.ide.templates.WinRTAndroidProjectEntryProvider

/** Studio's real extension registries, rather than IDEA's replaced gallery. */
class WinRTAndroidStudioWizardsTest : BasePlatformTestCase() {
    fun testStudioNewProjectAndModuleRegistriesIncludeWinRT() {
        val projects = ExtensionPointName.create<AndroidProjectEntryProvider>("com.android.androidProjectEntryProvider").extensionList
        val provider = projects.single { it is WinRTAndroidProjectEntryProvider }
        assertEquals(1, provider.getProjectEntries().size)
        // The native Android Next button would require Android SDK/activity
        // configuration; the selected entry offers the shared WinRT wizard.
        assertFalse(provider.getProjectEntries().single().canGoForward.value)
        val modules = ExtensionPointName.create<ModuleDescriptionProvider>("com.android.moduleDescriptionProvider").extensionList
        val entry = modules.single { it is WinRTAndroidModuleDescriptionProvider }.getDescriptions(project).single()
        assertEquals("Kotlin WinRT", entry.name)
        assertNotNull(entry.icon)
        val step = entry.createStep(project, "", object : ProjectSyncInvoker { override fun syncProject(project: Project) = Unit })
        try { assertEquals("WinRTStudioModuleStep", step.javaClass.simpleName) }
        finally { Disposer.dispose(step) }
    }
}
