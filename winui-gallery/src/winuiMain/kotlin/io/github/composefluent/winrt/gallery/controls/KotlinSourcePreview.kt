package io.github.composefluent.winrt.gallery.controls
import io.github.composefluent.winrt.gallery.code.KotlinCodeDocument
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.controls.*
internal class KotlinSourcePreview(private val kotlin: KotlinCodeDocument,private val xaml: KotlinCodeDocument?) : UserControl() {
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent()
        XamlTab.visibility = if (xaml == null || xaml.source.isBlank()) Visibility.Collapsed else Visibility.Visible
        KotlinTab.visibility = if (kotlin.source.isBlank()) Visibility.Collapsed else Visibility.Visible
        ready = true
        Languages.selectedItem = if (XamlTab.visibility == Visibility.Visible) XamlTab else KotlinTab
        ShowDocument()
    }
    private fun Languages_SelectionChanged(sender: SelectorBar,args: SelectorBarSelectionChangedEventArgs) { if (ready) ShowDocument() }
    private fun ShowDocument() {
        val document = if (Languages.selectedItem == XamlTab) xaml ?: kotlin else kotlin
        Code.SampleType = if (document.fileName.endsWith(".xaml",true)) "XAML" else "Kotlin"
        Code.SetDocument(document)
    }
}
