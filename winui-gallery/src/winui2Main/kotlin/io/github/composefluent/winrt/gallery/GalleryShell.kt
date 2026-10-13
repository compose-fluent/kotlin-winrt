package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.controls.NavigationView
import microsoft.ui.xaml.controls.NavigationViewItem
import microsoft.ui.xaml.controls.NavigationViewItemInvokedEventArgs
import windows.ui.xaml.ElementTheme
import windows.ui.xaml.controls.AutoSuggestBox
import windows.ui.xaml.controls.AutoSuggestBoxTextChangedEventArgs
import windows.ui.xaml.controls.Page
import windows.ui.xaml.controls.SelectionChangedEventArgs

class GalleryShell : Page() {
    private var ready = false

    override fun initializeComponent() {
        super.initializeComponent()
        ready = true
        populateNavigation("")
    }

    private fun populateNavigation(query: String) {
        val items = checkNotNull(navigation.menuItems)
        items.clear()
        winUi2Samples.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
            .forEach { sample ->
                items.add(NavigationViewItem().apply { content = sample.title; tag = sample.title })
            }
    }

    fun navigate(route: String) {
        val sample = winUi2Samples.firstOrNull { it.title.equals(route, ignoreCase = true) } ?: winUi2Samples.first()
        pageTitle.text = sample.title
        description.text = sample.description
        output.text = ""
        checkNotNull(sampleHost.children).clear()
        sourceCode.text = sample.code.trimIndent()
        sample.create(sampleHost) { message -> output.text = message; galleryLog("${sample.title}: $message") }
        galleryLog("Page ${sample.title} loaded")
    }

    // Like the desktop Gallery's --validate-xaml route, exercise actual projected
    // objects inside the application, where UWP thread affinity/resources exist.
    internal fun validateControls() {
        try {
            winUi2Samples.forEach { sample ->
                navigate(sample.title)
                sampleHost.updateLayout()
            }
            navigate("Home")
            galleryLog("Validation passed: ${winUi2Samples.size} pages constructed and laid out")
        } catch (error: Throwable) {
            galleryLog("Validation failed: ${error.stackTraceToString()}")
            throw error
        }
    }

    private fun onItemInvoked(sender: NavigationView?, args: NavigationViewItemInvokedEventArgs?) {
        val item = args?.invokedItemContainer?.asWinRT<NavigationViewItem>() ?: return
        navigate(item.tag as? String ?: return)
    }

    private fun onSearchChanged(sender: AutoSuggestBox?, args: AutoSuggestBoxTextChangedEventArgs?) {
        if (ready) populateNavigation(sender?.text.orEmpty())
    }

    private fun onThemeChanged(sender: Any?, args: SelectionChangedEventArgs?) {
        if (!ready) return
        requestedTheme = when (themePicker.selectedIndex) {
            1 -> ElementTheme.Light
            2 -> ElementTheme.Dark
            else -> ElementTheme.Default
        }
    }
}
