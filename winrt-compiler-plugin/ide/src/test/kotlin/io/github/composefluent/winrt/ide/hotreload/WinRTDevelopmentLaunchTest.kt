package io.github.composefluent.winrt.ide.hotreload

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.WinRTHotReloadLaunchData
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTStaticPreviewData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Path

/** A normal platform fixture holds write intent on EDT and conceals the
 * Compose launch regression. Invoke the production session from naked EDT,
 * then fail preparation before submitting any Gradle task or starting an app. */
class WinRTDevelopmentLaunchTest : BasePlatformTestCase() {
    override fun runInDispatchThread() = false
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()

    fun testAutomaticSdkPreviewAndRetryAcceptNakedEdtWithoutSavingTheEditor() = launch(sdk = true, preview = true)

    fun testProjectCodePreviewSavesDocumentsFromNakedEdtBeforePreparation() = launch(sdk = false, preview = true)

    fun testHotReloadLaunchSavesDocumentsFromNakedEdtBeforePreparation() = launch(sdk = false, preview = false)

    private fun launch(sdk: Boolean, preview: Boolean) {
        val original = """<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><TextBlock Text="Saved"/></Page>"""
        val edited = original.replace("Saved", "Unsaved")
        lateinit var file: VirtualFile
        lateinit var document: Document
        lateinit var module: WinRTModuleData
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = EdtTestUtil.runInEdtAndGet<WinRTDevelopmentSession, Exception> {
            file = myFixture.addFileToProject("Design.xaml", original).virtualFile
            val manager = FileDocumentManager.getInstance()
            document = manager.getDocument(file)!!
            manager.saveAllDocuments()
            WriteCommandAction.runWriteCommandAction(project) { document.setText(edited) }
            assertTrue(manager.isDocumentUnsaved(document))
            // A file in place of the build directory makes preparation fail
            // predictably after the save boundary, without a test Gradle runner.
            val blockedBuild = myFixture.addFileToProject("blocked-build", "not a directory").virtualFile.path
            val root = Path.of(file.path).parent.toString()
            module = WinRTModuleData(":app", root, blockedBuild, "2.4.0", "", emptyList(), emptyList(), emptyList(), emptyList(),
                hotReloadLaunches = listOf(WinRTHotReloadLaunchData("runWindows", "$root/App.exe", root)),
                staticPreview = WinRTStaticPreviewData("runWinRTXamlSdkPreview", "$root/Designer.exe", root, "$root/references.txt"))
            WinRTDevelopmentSession(project, scope, isolatedPreview = preview)
        }
        try {
            val selected = if (sdk) module.staticPreview!!.launch() else module.hotReloadLaunches.single()
            repeat(if (sdk) 2 else 1) { attempt ->
                EdtTestUtil.runInEdtAndWait<Exception>({
                    val application = ApplicationManager.getApplication()
                    assertTrue(application.isDispatchThread)
                    assertFalse("The callback must reproduce Compose's missing write intent", application.isWriteIntentLockAcquired)
                    if (sdk && attempt == 0) session.ensurePreview(module, selected)
                    else session.start(module, selected, preview = preview)
                    assertTrue(session.state.value.busy)
                }, false)
                val state = runBlocking { withTimeout(10_000) { session.state.first { !it.busy } } }
                assertFalse(state.connected)
                assertTrue("Preparation failure should be displayed, not escape into IDE error handling: ${state.message}",
                    state.message.contains("blocked-build"))
                EdtTestUtil.runInEdtAndWait<Exception> {
                    assertEquals("The SDK designer must preserve unsaved XAML", sdk, FileDocumentManager.getInstance().isDocumentUnsaved(document))
                    assertEquals(edited, document.text)
                    // 262 can write the physical file asynchronously after the
                    // saved VFS state is committed. This test stops before the
                    // Gradle runner and checks the IDE's saved file contents.
                    assertEquals(if (sdk) original else edited, file.contentsToByteArray().toString(Charsets.UTF_8))
                }
            }
        } finally {
            EdtTestUtil.runInEdtAndWait<Exception> { Disposer.dispose(session) }
            scope.cancel()
            runBlocking { withTimeout(10_000) { scope.coroutineContext[Job]!!.join() } }
        }
    }
}
