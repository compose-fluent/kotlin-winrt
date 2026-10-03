// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.code.*
import io.github.composefluent.winrt.runtime.*
import kotlin.time.Duration.Companion.milliseconds
import microsoft.ui.xaml.*
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.ScrollBar
import microsoft.ui.xaml.controls.primitives.ScrollEventArgs
import microsoft.ui.xaml.documents.Run
import microsoft.ui.xaml.media.*
import windows.applicationmodel.datatransfer.*
import windows.ui.text.FontStyle
import windows.ui.viewmanagement.AccessibilitySettings

internal class SampleCodePresenter : UserControl() {
    private var ready = false
    private var hasLoaded = false
    private var actualCode: String = ""
    private var sourceDocument: KotlinCodeDocument? = null
    fun SetDocument(value: KotlinCodeDocument) { sourceDocument = value; Refresh() }
    private val subscriptions = mutableMapOf<ControlExampleSubstitution, () -> Unit>()
    var Code: String
        get() = getValue(CodeProperty) as? String ?: ""
        set(value) { setValue(CodeProperty, value); Refresh() }
    var CodeSourceFile: String
        get() = getValue(CodeSourceFileProperty) as? String ?: ""
        set(value) { setValue(CodeSourceFileProperty, value); Refresh() }
    // Internal markup language names match Gallery's original XAML/CSharp/Inline tokens.
    var SampleType: String
        get() = getValue(SampleTypeProperty) as? String ?: "XAML"
        set(value) { setValue(SampleTypeProperty, value); Refresh() }
    var IsCopyButtonVisible: Boolean
        get() = getValue(IsCopyButtonVisibleProperty) as? Boolean ?: true
        set(value) { setValue(IsCopyButtonVisibleProperty, value); Refresh() }
    var Substitutions: MutableList<ControlExampleSubstitution> = mutableListOf()
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        listOf(CodeProperty, CodeSourceFileProperty, SampleTypeProperty, IsCopyButtonVisibleProperty).forEach { property -> registerPropertyChangedCallback(property) { _, _ -> Refresh() } }
        unloaded.add { _, _ -> hasLoaded = false; subscriptions.forEach { (value, listener) -> value.removeValueChanged(listener) }; subscriptions.clear() }
        GalleryTheme.observe(this) { Refresh() }; Refresh()
    }
    private fun Refresh() {
        // Upstream awaits the sample file before substitution. Our cached source
        // is synchronous, so wait for Loaded and the initial x:Bind assignments.
        if (!ready || !hasLoaded) return
        val original = sourceDocument ?: if (Code.isNotEmpty()) KotlinCodeDocument("inline.xaml", Code, listOf(KotlinCodeSpan(Code.length, KotlinCodeKind.Plain))) else GalleryCodeCatalog.sourceDocument(CodeSourceFile)
        visibility = if (original == null || original.source.isBlank()) Visibility.Collapsed else Visibility.Visible
        if (original == null) return
        val document = substituteGalleryCode(original, Substitutions)
        actualCode = document.source
        CopyButtonBorder.visibility = if (IsCopyButtonVisible) Visibility.Visible else Visibility.Collapsed
        AutomationProperties.setName(CopyCodeButton, "Copy $SampleType Code")
        VisualStateManager.goToState(this, when (SampleType) { "XAML" -> "XAMLSample"; "Kotlin", "CSharp" -> "KotlinSample"; else -> "InlineSample" }, false)
        val text = TextBlock().apply { fontFamily = FontFamily("Consolas, Cascadia Code"); isTextSelectionEnabled = true; textWrapping = TextWrapping.NoWrap }
        val palette = if (actualTheme == ElementTheme.Dark) KotlinCodePalette.Dark else KotlinCodePalette.Light
        val highContrast = AccessibilitySettings().highContrast
        var start = 0
        document.spans.forEach { span -> text.inlines.add(Run().apply { this.text = document.source.substring(start, span.end); foreground = if (highContrast) GalleryTheme.brush("TextFillColorPrimaryBrush") else brush(palette.style(span.kind).foreground); fontStyle = if (palette.style(span.kind).italic) FontStyle.Italic else FontStyle.Normal }); start = span.end }
        CodePresenter.content = text
        text.selectionChanged.add { _, _ -> CopyButtonBorder.visibility = if (IsCopyButtonVisible && text.selectedText.isEmpty()) Visibility.Visible else Visibility.Collapsed }
        Substitutions.filterNot(subscriptions::containsKey).forEach { value -> val listener: () -> Unit = { Refresh() }; subscriptions[value] = listener; value.addValueChanged(listener) }
    }
    private fun SampleCodePresenter_Loaded(sender: Any?, args: RoutedEventArgs) { hasLoaded = true; Refresh() }
    private fun CodePresenter_Loaded(sender: Any?, args: RoutedEventArgs) { Refresh() }
    private fun SampleCodePresenter_ActualThemeChanged(sender: FrameworkElement, args: Any?) { Refresh() }
    private fun CopyCodeButton_Click(sender: Any?, args: RoutedEventArgs) { Clipboard.setContent(DataPackage().apply { setText(actualCode) }) }
    private fun CodeScrollViewer_Loaded(sender: Any?, args: RoutedEventArgs) {
        fun Find(element: DependencyObject): ScrollBar? {
            (element as? ScrollBar)?.takeIf { it.orientation == Orientation.Horizontal }?.let { return it }
            for (i in 0 until VisualTreeHelper.getChildrenCount(element)) Find(VisualTreeHelper.getChild(element, i))?.let { return it }
            return null
        }
        val bar = Find(CodeScrollViewer) ?: return
        if (bar.tag is DispatcherTimer) return
        val timer = DispatcherTimer().apply { interval = 500.milliseconds; tick.add { _, _ -> stop(); if (IsCopyButtonVisible) CopyButtonBorder.visibility = Visibility.Visible } }
        bar.tag = timer
        bar.scroll.add { _, _ -> CopyButtonBorder.visibility = Visibility.Collapsed; timer.stop(); timer.start() }
    }
    companion object {
        val CodeProperty: DependencyProperty = DependencyProperty.register("Code", String::class, SampleCodePresenter::class, PropertyMetadata(""))
        val CodeSourceFileProperty: DependencyProperty = DependencyProperty.register("CodeSourceFile", String::class, SampleCodePresenter::class, PropertyMetadata(""))
        val SampleTypeProperty: DependencyProperty = DependencyProperty.register("SampleType", String::class, SampleCodePresenter::class, PropertyMetadata("XAML"))
        val IsCopyButtonVisibleProperty: DependencyProperty = DependencyProperty.register("IsCopyButtonVisible", Boolean::class, SampleCodePresenter::class, PropertyMetadata(true))
    }
}
