package io.github.composefluent.winrt.gallery.controls
import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.code.*
import io.github.composefluent.winrt.runtime.WinRTXamlContentProperty
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.*
@WinRTXamlContentProperty("Example")
internal class ControlExample : UserControl() {
    private var ready = false
    private var sourceReady = false
    private var sourceSample: GallerySampleCode? = null
    val Substitutions: MutableList<ControlExampleSubstitution> = mutableListOf()
    var WebViewHeight: Int = 400
    var WebViewWidth: Int = 800
    var HeaderText: String
        get() = getValue(HeaderTextProperty) as? String ?: ""
        set(value) { setValue(HeaderTextProperty,value) }
    var Example: UIElement?
        get() = getValue(ExampleProperty) as UIElement?
        set(value) { setValue(ExampleProperty,value) }
    var Output: UIElement?
        get() = getValue(OutputProperty) as UIElement?
        set(value) { setValue(OutputProperty,value) }
    var Options: UIElement?
        get() = getValue(OptionsProperty) as UIElement?
        set(value) { setValue(OptionsProperty,value) }
    var Xaml: String
        get() = getValue(XamlProperty) as? String ?: ""
        set(value) { setValue(XamlProperty,value) }
    var XamlSource: String
        get() = getValue(XamlSourceProperty) as? String ?: ""
        set(value) { setValue(XamlSourceProperty,value) }
    var Kotlin: String
        get() = getValue(KotlinProperty) as? String ?: ""
        set(value) { setValue(KotlinProperty,value) }
    var KotlinSource: String
        get() = getValue(KotlinSourceProperty) as? String ?: ""
        set(value) { setValue(KotlinSourceProperty,value) }
    var SampleDefinition: String
        get() = getValue(SampleDefinitionProperty) as? String ?: ""
        set(value) { setValue(SampleDefinitionProperty,value) }
    var SourceCodeVisibility: Visibility
        get() = getValue(SourceCodeVisibilityProperty) as Visibility
        set(value) { setValue(SourceCodeVisibilityProperty,value) }
    var ExampleHeight: GridLength
        get() = getValue(ExampleHeightProperty) as GridLength
        set(value) { setValue(ExampleHeightProperty,value) }
    var IsExperimental: Boolean
        get() = getValue(IsExperimentalProperty) as Boolean
        set(value) { setValue(IsExperimentalProperty,value) }
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        RefreshSampleDefinition()
        UpdateHeader()
        val state = GalleryTheme.sampleBeingConstructed
        if (state != null) { state.sourceExampleIndex++; state.sampleBodies.add(ControlPresenter) }
        if (ExampleHeight.gridUnitType == GridUnitType.Pixel) ControlPresenter.height = ExampleHeight.value
        sourcePresenter.expanding.add { _,_ -> RefreshSource(); sourceReady = true }
        SelectorBarControl.selectedItem = SelectorBarXamlItem
    }
    private fun UpdateHeader() { if (ready) HeaderTextPresenter.visibility = if (HeaderText.isBlank()) Visibility.Collapsed else Visibility.Visible }
    private fun RefreshSampleDefinition() {
        sourceSample = GalleryCodeCatalog.sampleDefinition(SampleDefinition)
        sourceSample?.header?.takeIf(String::isNotBlank)?.let { HeaderText = it }
        // Update the language tabs immediately, as Gallery's PrepareSelectorBarItem
        // does. XAML sets this property before filling Substitutions, so render
        // the source only after Loaded or when the expander has already opened.
        if (ready && (sourceSample != null || sourceReady)) RefreshSource(refreshDocuments = sourceReady)
    }
    private fun RefreshSource(refreshDocuments: Boolean = true) {
        if (!ready) return
        fun inline(name: String,source: String): KotlinCodeDocument = KotlinCodeDocument(name,source,listOf(KotlinCodeSpan(source.length,KotlinCodeKind.Plain)))
        val xaml = if (Xaml.isNotEmpty()) inline("sample.xaml",Xaml) else GalleryCodeCatalog.sourceDocument(XamlSource) ?: sourceSample?.xaml
        val kotlin = if (Kotlin.isNotEmpty()) inline("sample.kt",Kotlin) else GalleryCodeCatalog.sourceDocument(KotlinSource) ?: sourceSample?.kotlin
        if (refreshDocuments) {
            XamlPresenter.Substitutions = Substitutions
            KotlinPresenter.Substitutions = Substitutions
            xaml?.let { XamlPresenter.SetDocument(it) }
            kotlin?.let { KotlinPresenter.SetDocument(it) }
        }
        fun showLanguage(item: SelectorBarItem, present: Boolean) {
            if (!present) SelectorBarControl.items.remove(item)
            else if (!SelectorBarControl.items.contains(item)) {
                if (item == SelectorBarXamlItem) SelectorBarControl.items.add(0,item) else SelectorBarControl.items.add(item)
            }
        }
        showLanguage(SelectorBarXamlItem,!xaml?.source.isNullOrBlank())
        showLanguage(SelectorBarKotlinItem,!kotlin?.source.isNullOrBlank())
        if (!SelectorBarControl.items.contains(SelectorBarControl.selectedItem))
            SelectorBarControl.selectedItem = SelectorBarControl.items.firstOrNull()
        UpdateLanguage()
    }
    private fun UpdateLanguage() {
        XamlContentPresenter.visibility = if (SelectorBarControl.selectedItem == SelectorBarXamlItem) Visibility.Visible else Visibility.Collapsed
        KotlinContentPresenter.visibility = if (SelectorBarControl.selectedItem == SelectorBarKotlinItem) Visibility.Visible else Visibility.Collapsed
    }
    private fun SelectorBarControl_SelectionChanged(sender: SelectorBar,args: SelectorBarSelectionChangedEventArgs) { if (ready) UpdateLanguage() }
    private fun SelectorBarItem_Loaded(sender: Any?,args: RoutedEventArgs) { if (ready) RefreshSource() }
    companion object {
        val HeaderTextProperty: DependencyProperty = DependencyProperty.register("HeaderText",String::class,ControlExample::class,PropertyMetadata("", PropertyChangedCallback { sender,_ -> checkNotNull(sender).asWinRT<ControlExample>().UpdateHeader() }))
        val ExampleProperty: DependencyProperty = DependencyProperty.register("Example",UIElement::class,ControlExample::class,PropertyMetadata(null))
        val OutputProperty: DependencyProperty = DependencyProperty.register("Output",UIElement::class,ControlExample::class,PropertyMetadata(null))
        val OptionsProperty: DependencyProperty = DependencyProperty.register("Options",UIElement::class,ControlExample::class,PropertyMetadata(null))
        val XamlProperty: DependencyProperty = DependencyProperty.register("Xaml",String::class,ControlExample::class,PropertyMetadata("", PropertyChangedCallback { sender,_ -> checkNotNull(sender).asWinRT<ControlExample>().apply { if (sourceReady) RefreshSource() } }))
        val XamlSourceProperty: DependencyProperty = DependencyProperty.register("XamlSource",String::class,ControlExample::class,PropertyMetadata("", PropertyChangedCallback { sender,_ -> checkNotNull(sender).asWinRT<ControlExample>().apply { if (sourceReady) RefreshSource() } }))
        val KotlinProperty: DependencyProperty = DependencyProperty.register("Kotlin",String::class,ControlExample::class,PropertyMetadata("", PropertyChangedCallback { sender,_ -> checkNotNull(sender).asWinRT<ControlExample>().apply { if (sourceReady) RefreshSource() } }))
        val KotlinSourceProperty: DependencyProperty = DependencyProperty.register("KotlinSource",String::class,ControlExample::class,PropertyMetadata("", PropertyChangedCallback { sender,_ -> checkNotNull(sender).asWinRT<ControlExample>().apply { if (sourceReady) RefreshSource() } }))
        val SampleDefinitionProperty: DependencyProperty = DependencyProperty.register("SampleDefinition",String::class,ControlExample::class,PropertyMetadata("", PropertyChangedCallback { sender,_ -> checkNotNull(sender).asWinRT<ControlExample>().RefreshSampleDefinition() }))
        val SourceCodeVisibilityProperty: DependencyProperty = DependencyProperty.register("SourceCodeVisibility",Visibility::class,ControlExample::class,PropertyMetadata(Visibility.Visible))
        val ExampleHeightProperty: DependencyProperty = DependencyProperty.register("ExampleHeight",GridLength::class,ControlExample::class,PropertyMetadata(GridLength(1.0,GridUnitType.Star)))
        val IsExperimentalProperty: DependencyProperty = DependencyProperty.register("IsExperimental",Boolean::class,ControlExample::class,PropertyMetadata(false))
    }
}
