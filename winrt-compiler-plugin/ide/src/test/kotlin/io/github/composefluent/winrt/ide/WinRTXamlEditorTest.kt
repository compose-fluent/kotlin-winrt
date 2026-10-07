package io.github.composefluent.winrt.ide

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.xaml.*
import io.github.composefluent.winrt.metadata.*
import kotlinx.serialization.json.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameProcessor
import java.nio.file.Files
import java.nio.file.Path

/** Uses the real XML completion/PSI pipeline and the existing WinMD writer/loader. */
@OptIn(KaAllowAnalysisOnEdt::class)
class WinRTXamlEditorTest : BasePlatformTestCase() {
    private var root: Path? = null

    private fun configure(markup: String): XmlFile {
        myFixture.addFileToProject("Shell.kt", "package sample\nclass Shell { val reservedHandler = 0; fun onClick(sender: Any, args: Any) {} }")
        myFixture.addFileToProject("Widget.kt", """
            package sample
            open class WidgetBase {
                var inherited: String = ""
                companion object { val inheritedProperty = microsoft.ui.xaml.DependencyProperty() }
            }
            class Widget : WidgetBase() {
                var Label: String = ""
                var frame: String = ""
                companion object {
                    val frameProperty = microsoft.ui.xaml.DependencyProperty()
                    val LabelProperty: String = "Not a dependency property"
                    val rowProperty = microsoft.ui.xaml.DependencyProperty()
                    fun GetRow(target: Any): Int = 0
                    fun SetRow(target: Any, value: Int) {}
                }
            }
        """.trimIndent())
        myFixture.addFileToProject("DependencyProperty.kt", "package microsoft.ui.xaml\nclass DependencyProperty")
        val file = myFixture.configureByText("Shell.xaml", markup) as XmlFile
        val directory = Files.createTempDirectory("winrt-ide-editor-").also { root = it }
        val metadata = directory.resolve("Controls.winmd")
        // Corresponds to cswinrt's normalized type/member view. No IDE-only
        // fake control registry is used to make XML completion pass.
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Controls", listOf(
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.Control"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.Button", "Microsoft.UI.Xaml.Controls.Control"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Visibility", enumEntries = listOf("Visible", "Collapsed")),
        ), mapOf(
            "Microsoft.UI.Xaml.Controls.Control" to WinRTXamlApplicationTypeMembers(properties = listOf(
                WinRTXamlApplicationProperty("Width", WinRTTypeRef.named("Double")),
                WinRTXamlApplicationProperty("Visibility", WinRTTypeRef.named("Microsoft.UI.Xaml.Visibility")),
            )),
            "Microsoft.UI.Xaml.Controls.Button" to WinRTXamlApplicationTypeMembers(
                properties = listOf(WinRTXamlApplicationProperty("Content", WinRTTypeRef.named("String"))),
                events = listOf(WinRTXamlApplicationEvent("Click", WinRTTypeRef.named("Sample.ClickHandler"))),
            ),
        ), metadata, mapOf("Sample.ClickHandler" to "Sample", "Windows.Foundation.EventRegistrationToken" to "Windows.Foundation.FoundationContract"))
        val input = directory.resolve("input.json")
        Files.writeString(input, buildJsonObject { put("ReferenceAssemblies", buildJsonArray {
            add(buildJsonObject { put("FullPath", metadata.toString()) })
        }) }.toString())
        val sourceRoot = file.originalFile.virtualFile.parent.path
        project.service<WinRTProjectService>().replaceBuildModels(directory.toString(), listOf(
            WinRTModuleData(":", directory.toString(), directory.resolve("build").toString(), "2.4.0", "",
                emptyList(), emptyList(), emptyList(), emptyList(), listOf(
                    WinRTXamlCompilationData("analyzeWinRTXaml", listOf(sourceRoot), directory.resolve("declarations.json").toString(), input.toString(), "", ""),
                )),
        ))
        val catalogs = project.service<WinRTXamlCatalogService>()
        PlatformTestUtil.waitWithEventsDispatching("WinMD catalog", { catalogs.forFile(file.originalFile.virtualFile.path) != null }, 10)
        val resources = project.service<io.github.composefluent.winrt.ide.resources.WinRTResourceIndex>()
        PlatformTestUtil.waitWithEventsDispatching("Resource index initialized", {
            resources.forFile(file.originalFile.virtualFile.path)?.module?.projectDirectory == directory.toString()
        }, 10)
        val snapshots = project.service<io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService>()
        PlatformTestUtil.waitWithEventsDispatching("XAML analysis input initialized", {
            snapshots.currentText(directory.resolve("declarations.json").toString()) != null
        }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return file
    }

    fun testXmlHighlightingAndInheritedMembersComeFromWinmd() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" <caret>/>""")
        assertEquals("WinRT XAML", com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("xaml").name)
        val names = file.rootTag!!.descriptor!!.getAttributesDescriptors(file.rootTag).map { it.name }.toSet()
        assertTrue(names.toString(), names.containsAll(listOf("Content", "Width", "Click", "Visibility", "x:Name")))
        myFixture.complete(CompletionType.BASIC)
        val variants = myFixture.lookupElementStrings.orEmpty()
        assertTrue(variants.toString(), variants.containsAll(listOf("Content", "Width", "Click")))
    }

    fun testNativeXmlHighlightingAcceptsXamlNamespacesSdkTypesAndLanguageObjects() {
        configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" xmlns:local="using:sample"><local:Widget/><StaticResource x:Key="Alias" ResourceKey="Accent"/><x:String x:Key="Caption">Hello</x:String></Button>""")
        myFixture.enableInspections(com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection())
        val errors = allowAnalysisOnEdt { myFixture.doHighlighting() }.filter {
            it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR
        }
        assertEmpty(errors.map { it.description })
    }

    fun testUnknownXmlNamespaceAndSdkTagAreStillReported() {
        configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:foreign="urn:unknown-language"><DoesNotExist/><foreign:Missing/></Button>""")
        myFixture.enableInspections(com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection())
        val errors = allowAnalysisOnEdt { myFixture.doHighlighting() }.filter {
            it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR
        }
        assertTrue(errors.toString(), errors.any { it.description?.contains("DoesNotExist") == true })
        assertTrue(errors.toString(), errors.any { myFixture.file.text.substring(it.startOffset, it.endOffset) == "urn:unknown-language" })
    }

    fun testTagAndEnumCompletionAndCustomTypeNavigation() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:local="using:sample" Visibility="<caret>"><local:Widget/></Button>""")
        assertEquals(listOf("Visible", "Collapsed"), file.rootTag!!.descriptor!!.getAttributeDescriptor("Visibility", file.rootTag)!!.enumeratedValues!!.toList())
        myFixture.complete(CompletionType.BASIC)
        assertTrue(myFixture.lookupElementStrings.toString(), myFixture.lookupElementStrings.orEmpty().containsAll(listOf("Visible", "Collapsed")))
        val child = file.rootTag!!.subTags.single()
        assertEquals("Widget", (child.descriptor!!.declaration as KtClass).name)
        val descriptors = file.rootTag!!.descriptor!!.getElementsDescriptors(file.rootTag).map { it.name }
        assertTrue(descriptors.toString(), descriptors.containsAll(listOf("Button", "local:Widget")))
    }

    fun testNativeAttributeNavigationAndSemanticColorsIncludeInheritedAndAttachedDependencyProperties() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:local="using:sample" Content="Hello" Width="240"><local:Widget Label="Normal" Frame="DP" Inherited="Base" local:Widget.Row="1"><local:Widget.Label>Property element</local:Widget.Label></local:Widget></Button>""")
        myFixture.addFileToProject("SdkControls.kt", """
            package microsoft.ui.xaml.controls
            open class Control {
                var width: Double = 0.0
                companion object { val widthProperty = microsoft.ui.xaml.DependencyProperty() }
            }
            class Button : Control() { var content: String = "" }
        """.trimIndent())
        fun targets(attribute: com.intellij.psi.xml.XmlAttribute): List<String?> = allowAnalysisOnEdt {
            val source = attribute.nameElement!!
            WinRTXamlAttributeNavigation().getGotoDeclarationTargets(source, source.textRange.endOffset - 1, myFixture.editor)!!
                .map { (it as org.jetbrains.kotlin.psi.KtNamedDeclaration).name }
        }
        val button = file.rootTag!!
        val widget = button.subTags.single()
        assertEquals(listOf("content"), targets(button.getAttribute("Content")!!))
        assertEquals(listOf("width", "widthProperty"), targets(button.getAttribute("Width")!!))
        assertEquals(listOf("Label"), targets(widget.getAttribute("Label")!!)) // The suffix alone cannot identify a DP.
        assertEquals(listOf("frame", "frameProperty"), targets(widget.getAttribute("Frame")!!))
        assertEquals(listOf("inherited", "inheritedProperty"), targets(widget.getAttribute("Inherited")!!))
        assertEquals(listOf("GetRow", "rowProperty"), targets(widget.getAttribute("local:Widget.Row")!!))
        assertEquals("Label", allowAnalysisOnEdt { (widget.subTags.single().descriptor!!.declaration as org.jetbrains.kotlin.psi.KtProperty).name })
        val width = button.getAttribute("Width")!!
        assertEquals("width", allowAnalysisOnEdt { (width.descriptor!!.declaration as org.jetbrains.kotlin.psi.KtProperty).name })
        myFixture.editor.caretModel.moveToOffset(width.nameElement!!.textRange.startOffset + 2)
        val native = allowAnalysisOnEdt { com.intellij.codeInsight.TargetElementUtil.findTargetElement(myFixture.editor,
            com.intellij.codeInsight.TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED) }
        assertEquals("width", (native as org.jetbrains.kotlin.psi.KtProperty).name)
        val highlights = allowAnalysisOnEdt { myFixture.doHighlighting() }
        fun colored(key: com.intellij.openapi.editor.colors.TextAttributesKey) = highlights.filter { it.forcedTextAttributesKey == key }
            .map { file.text.substring(it.startOffset, it.endOffset) }.toSet()
        assertEquals(setOf("Content", "Label"), colored(WinRTXamlMarkupColors.ATTRIBUTE))
        assertEquals(setOf("Width", "Frame", "Inherited", "Row"), colored(WinRTXamlMarkupColors.DEPENDENCY_PROPERTY))
    }

    fun testClassAndEventReferencesResolveAndParticipateInRename() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Shell" Click="onClick"/>""")
        val classValue = file.rootTag!!.getAttribute("Class", WinRTXamlCatalog.XAML)!!.valueElement!!
        val handlerValue = file.rootTag!!.getAttribute("Click")!!.valueElement!!
        val classReferences = PsiReferenceService.getService().getReferences(classValue, PsiReferenceService.Hints.NO_HINTS)
        assertTrue(classReferences.toString(), classReferences.any { (it.resolve() as? KtClass)?.name == "Shell" })
        val handler = PsiReferenceService.getService().getReferences(handlerValue, PsiReferenceService.Hints.NO_HINTS)
            .first { allowAnalysisOnEdt { it.resolve() } is KtNamedFunction }
        assertEquals("onClick", (allowAnalysisOnEdt { handler.resolve() } as KtNamedFunction).name)
        WriteCommandAction.runWriteCommandAction(project) { handler.handleElementRename("onPressed") }
        assertEquals("onPressed", handlerValue.value)
        assertTrue(file.text.contains("x:Class=\"sample.Shell\""))
    }

    fun testEventSignaturesUseTheClosedProjectedDelegateAndInheritedHandlers() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.EventPage" Click="inherited"/>""")
        myFixture.addFileToProject("Events.kt", """
            package sample
            open class Args
            class SpecificArgs : Args()
            fun interface ClickHandler<T> { fun invoke(sender: Any?, args: T) }
            open class EventBase { fun inherited(sender: Any?, args: Args) {} }
            class EventPage : EventBase() {
                fun good(sender: Any?, args: SpecificArgs) {}
                fun nonNullableSender(sender: Any, args: Args) {}
                fun wrongArgs(sender: Any?, args: String) {}
                fun wrongCount(sender: Any?) {}
                fun wrongReturn(sender: Any?, args: Args): Int = 1
                suspend fun suspending(sender: Any?, args: Args) {}
                fun <T> generic(sender: Any?, args: Args) {}
                fun overloaded(sender: Any?, args: Args) {}
                fun overloaded(sender: Any?) {}
            }
        """.trimIndent())
        myFixture.addFileToProject("Button.kt", """
            package microsoft.ui.xaml.controls
            class Button { fun addClick(handler: sample.ClickHandler<sample.SpecificArgs>) {} }
        """.trimIndent())
        val result = allowAnalysisOnEdt { WinRTXamlEventAnalysis.forAttribute(file.rootTag!!.getAttribute("Click")!!) }!!
        assertTrue(result.delegateAvailable)
        assertNull(result.problem("inherited"))
        assertNull(result.problem("good"))
        assertEquals("inherited", (result.target("inherited") as KtNamedFunction).name)
        for (name in listOf("nonNullableSender", "wrongArgs", "wrongCount", "wrongReturn", "suspending", "generic", "overloaded", "missing"))
            assertNotNull(name, result.problem(name))
        val reference = PsiReferenceService.getService().getReferences(file.rootTag!!.getAttribute("Click")!!.valueElement!!, PsiReferenceService.Hints.NO_HINTS)
            .first { allowAnalysisOnEdt { it.resolve() } is KtNamedFunction }
        assertEquals("inherited", (allowAnalysisOnEdt { reference.resolve() } as KtNamedFunction).name)
    }

    fun testEventCompletionOmitsIncompatibleAndOverloadedMethods() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Page" Click="<caret>"/>""")
        myFixture.addFileToProject("Page.kt", """
            package sample
            fun interface ClickHandler { fun invoke(sender: Any?, args: String) }
            class Page {
                fun valid(sender: Any?, args: Any) {}
                fun invalid(sender: Any, args: String) {}
                fun overloaded(sender: Any?, args: String) {}
                fun overloaded(sender: Any?) {}
            }
        """.trimIndent())
        myFixture.addFileToProject("Button.kt", "package microsoft.ui.xaml.controls\nclass Button { fun addClick(handler: sample.ClickHandler) {} }")
        allowAnalysisOnEdt { myFixture.complete(CompletionType.BASIC) }
        val variants = myFixture.lookupElementStrings.orEmpty()
        // IntelliJ auto-inserts a sole candidate; both outcomes exercise native completion.
        assertTrue(variants.toString(), "valid" in variants || file.text.contains("Click=\"valid\""))
        assertFalse(variants.toString(), variants.any { it == "invalid" || it == "overloaded" })
    }

    fun testNativeHighlightingReportsTheEventContractAtTheAttributeValue() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.BadPage" Click="bad"/>""")
        myFixture.addFileToProject("BadPage.kt", "package sample\nclass BadPage { fun bad(sender: Any?, args: Any?): Int = 1 }")
        val diagnostics = allowAnalysisOnEdt { myFixture.doHighlighting() }
            .filter { it.description?.contains("must return Unit") == true }
        assertEquals(diagnostics.toString(), 1, diagnostics.size)
        assertEquals(file.rootTag!!.getAttribute("Click")!!.valueElement!!.valueTextRange,
            com.intellij.openapi.util.TextRange(diagnostics.single().startOffset, diagnostics.single().endOffset))
    }

    fun testNativeHandlerAndClassRenameUpdateXamlAndItsRequiredFilePair() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Shell" Click="onClick"/>""")
        val handlerValue = file.rootTag!!.getAttribute("Click")!!.valueElement!!
        val handler = allowAnalysisOnEdt { WinRTXamlEventAnalysis.forAttribute(file.rootTag!!.getAttribute("Click")!!)!!.target("onClick") }!!
        val usages = allowAnalysisOnEdt { ReferencesSearch.search(handler).findAll() }
        assertTrue(usages.toString(), usages.any { it.element == handlerValue })
        allowAnalysisOnEdt { RenameProcessor(project, handler, "onPressed", false, false).run() }
        assertEquals("onPressed", file.rootTag!!.getAttributeValue("Click"))
        val owner = WinRTXamlSymbols.ownerClass(file.rootTag!!)!!
        allowAnalysisOnEdt { RenameProcessor(project, owner, "RenamedShell", false, false).run() }
        assertEquals("sample.RenamedShell", file.rootTag!!.getAttributeValue("Class", WinRTXamlCatalog.XAML))
        assertEquals("RenamedShell.kt", owner.containingFile.name)
        assertEquals("RenamedShell.xaml", file.name)
    }

    fun testPrivateXamlHandlersHaveNativeUsagesCodeVisionAndNoUnusedWarning() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.PopupPage" Click="ShowPopupButton_Click"/>""")
        val kotlin = myFixture.addFileToProject("PopupPage.kt", "package sample\nclass PopupPage { private fun ShowPopupButton_Click(sender: Any?, args: Any?) {}\nprivate fun neverUsed() {} }")
        val handler = allowAnalysisOnEdt { WinRTXamlEventAnalysis.forAttribute(file.rootTag!!.getAttribute("Click")!!)!!.target("ShowPopupButton_Click") } as KtNamedFunction
        val references = allowAnalysisOnEdt { ReferencesSearch.search(handler).findAll() }
        assertTrue(references.toString(), references.any { it.element == file.rootTag!!.getAttribute("Click")!!.valueElement })
        val hint = com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().submit(java.util.concurrent.Callable {
            com.intellij.openapi.application.ReadAction.compute<String?, RuntimeException> {
                org.jetbrains.kotlin.idea.k2.codeinsight.hints.KotlinReferencesCodeVisionProvider().getHint(handler, kotlin)
            }
        })
        PlatformTestUtil.waitWithEventsDispatching("Native Kotlin usage hint", { hint.isDone }, 10)
        assertEquals("1 Usage", hint.get())
        myFixture.configureFromExistingVirtualFile(kotlin.virtualFile)
        val unusedInspection = com.intellij.codeInspection.LocalInspectionEP.LOCAL_INSPECTION.extensionList.single {
            it.language == "kotlin" && it.implementationClass.endsWith(".UnusedSymbolInspection") &&
                !it.implementationClass.startsWith("org.jetbrains.kotlin.idea.inspections.") // K1 implementation
        }.instantiateTool()
        myFixture.enableInspections(unusedInspection)
        val unused = allowAnalysisOnEdt { myFixture.doHighlighting() }.filter { it.description?.contains("never used") == true }
        assertFalse(unused.toString(), unused.any { kotlin.text.substring(it.startOffset, it.endOffset) == "ShowPopupButton_Click" })
        assertTrue(unused.toString(), unused.any { kotlin.text.substring(it.startOffset, it.endOffset) == "neverUsed" })
        allowAnalysisOnEdt { RenameProcessor(project, handler, "onPopupClicked", false, false).run() }
        assertEquals("onPopupClicked", file.rootTag!!.getAttributeValue("Click"))
    }

    private fun projectedClickDelegate() {
        myFixture.addFileToProject("TypedHandler.kt", "package sample\nclass RoutedEventArgs\nclass TypedHandler<T, E> { operator fun invoke(sender: T, args: E) {} }")
        myFixture.addFileToProject("ProjectedButton.kt", "package microsoft.ui.xaml.controls\nclass Button { fun addClick(handler: sample.TypedHandler<Any?, sample.RoutedEventArgs>) {} }")
    }

    fun testMissingHandlerQuickFixCreatesTheClosedDelegateSignatureAndSupportsUndo() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Shell" Click="ShowPopupButton_Click"/>""")
        projectedClickDelegate()
        val action = allowAnalysisOnEdt { myFixture.findSingleIntention("Create event handler 'ShowPopupButton_Click'") }
        myFixture.launchAction(action)
        val owner = WinRTXamlSymbols.ownerClass(file.rootTag!!)!!
        val handler = owner.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "ShowPopupButton_Click" }
        assertTrue(handler.text, handler.text.contains("private fun ShowPopupButton_Click(sender: Any?, args: RoutedEventArgs)"))
        assertNull(allowAnalysisOnEdt { WinRTXamlEventAnalysis.forAttribute(file.rootTag!!.getAttribute("Click")!!)!!.problem("ShowPopupButton_Click") })
        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedEditor
        // The platform asks to confirm a command initiated in another file.
        val previous = com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK)
        try {
            com.intellij.openapi.command.undo.UndoManager.getInstance(project).undo(editor)
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
            val undone = WinRTXamlSymbols.ownerClass(file.rootTag!!)!!
            assertFalse(undone.text, undone.declarations.filterIsInstance<KtNamedFunction>().any { it.name == "ShowPopupButton_Click" })
            com.intellij.openapi.command.undo.UndoManager.getInstance(project).redo(editor)
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
            val redone = WinRTXamlSymbols.ownerClass(file.rootTag!!)!!
            assertTrue(redone.text, redone.declarations.filterIsInstance<KtNamedFunction>().any { it.name == "ShowPopupButton_Click" })
        } finally { com.intellij.openapi.ui.TestDialogManager.setTestDialog(previous) }
        assertEquals("ShowPopupButton_Click", file.rootTag!!.getAttributeValue("Click"))
    }

    fun testEventCompletionOffersCreationUsingTheElementName() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Shell" x:Name="ShowPopupButton" Click="<caret>"/>""")
        projectedClickDelegate()
        val settings = com.intellij.codeInsight.CodeInsightSettings.getInstance()
        val automatic = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        try {
            val items = allowAnalysisOnEdt { myFixture.complete(CompletionType.BASIC) }!!
            val item = items.single { it.lookupString == "ShowPopupButton_Click" }
            val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
            item.renderElement(presentation)
            assertTrue(presentation.tailText.orEmpty(), presentation.tailText.orEmpty().contains("Create event handler"))
            myFixture.lookup.currentItem = item
            myFixture.finishLookup('\n')
            assertEquals("ShowPopupButton_Click", file.rootTag!!.getAttributeValue("Click"))
            val owner = WinRTXamlSymbols.ownerClass(file.rootTag!!)!!
            assertTrue(owner.text, owner.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "ShowPopupButton_Click" }
                .text.contains("sender: Any?, args: RoutedEventArgs"))
        } finally { settings.AUTOCOMPLETE_ON_CODE_COMPLETION = automatic }
    }

    fun testEventCompletionCreatesTheEnteredNameAndDoesNotDuplicateAnExistingMethod() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Shell" Click="openPopup<caret>"/>""")
        projectedClickDelegate()
        val settings = com.intellij.codeInsight.CodeInsightSettings.getInstance()
        val automatic = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        try {
            val item = allowAnalysisOnEdt { myFixture.complete(CompletionType.BASIC) }!!.single { it.lookupString == "openPopup" }
            myFixture.lookup.currentItem = item
            myFixture.finishLookup('\n')
            val owner = WinRTXamlSymbols.ownerClass(file.rootTag!!)!!
            val handler = owner.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "openPopup" }
            assertTrue(handler.text, handler.text.contains("sender: Any?, args: RoutedEventArgs"))
            assertEquals("openPopup", file.rootTag!!.getAttributeValue("Click"))
            val event = allowAnalysisOnEdt { WinRTXamlEventAnalysis.forAttribute(file.rootTag!!.getAttribute("Click")!!) }!!
            assertNull(WinRTXamlEventCreation.proposal(file.rootTag!!.getAttribute("Click")!!, "openPopup", event))
            assertNull(WinRTXamlEventCreation.proposal(file.rootTag!!.getAttribute("Click")!!, "onClick", event))
            assertNull(WinRTXamlEventCreation.proposal(file.rootTag!!.getAttribute("Click")!!, "reservedHandler", event))
        } finally { settings.AUTOCOMPLETE_ON_CODE_COMPLETION = automatic }
    }

    override fun tearDown() {
        try {
            root?.let { directory ->
                project.service<WinRTProjectService>().replaceBuildModels(directory.toString(), emptyList())
                check(directory.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                check(directory.fileName.toString().startsWith("winrt-ide-editor-"))
                FileUtil.delete(directory.toFile())
            }
        } finally { super.tearDown() }
    }
}
