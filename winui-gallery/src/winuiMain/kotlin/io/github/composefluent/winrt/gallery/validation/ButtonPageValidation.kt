package io.github.composefluent.winrt.gallery.validation

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.basicinput.ButtonPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.TextWrapping
import microsoft.ui.xaml.automation.peers.ButtonAutomationPeer
import microsoft.ui.xaml.automation.peers.ToggleButtonAutomationPeer
import microsoft.ui.xaml.controls.StackPanel
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.Image
import microsoft.ui.xaml.controls.Grid
import microsoft.ui.xaml.controls.SelectorBar

internal fun validateButtonPage(page: ButtonPage) = with(page) {
    GalleryXamlValidation.onLoaded("Button", this, trigger = {
        ButtonAutomationPeer(standardButton).invoke()
        ButtonAutomationPeer(imageButton).invoke()
        ToggleButtonAutomationPeer(disableButton).toggle()
        textExample.sourcePresenter.isExpanded = true
    }, verify = {
        check(textOutput.text == "You clicked: Standard XAML button")
        check(imageOutput.text == "You clicked: Image button")
        check(disableButton.isChecked == true && !standardButton.isEnabled)
        check(checkNotNull(content).asWinRT<StackPanel>().children.size == 4)
        check(accentButton.style != null && subtleButton.style != null)
        check(wrappedFirst.maxWidth == 240.0 && wrappedSecond.maxWidth == 240.0)
        check(checkNotNull(wrappedFirst.content).asWinRT<TextBlock>().textWrapping == TextWrapping.WrapWholeWords)
        check(checkNotNull(imageButton.content).asWinRT<Image>().source != null)
        check(textExample.sourcePresenter.content != null)
        check(checkNotNull(GalleryCodeCatalog.xamlDocument("Button")).source.contains("Click=\"onStandardClick\""))
        check(checkNotNull(GalleryCodeCatalog.document("Button", "", 0)).source.contains("private fun onStandardClick"))
        val textMarkup = checkNotNull(GalleryCodeCatalog.xamlDocument("Button", "A simple Button with text content.", 0)).source
        val imageMarkup = checkNotNull(GalleryCodeCatalog.xamlDocument("Button", "A Button with image content.", 1)).source
        check("imageButton" !in textMarkup && "<Page" !in textMarkup)
        check("Slices.png" in imageMarkup && "standardButton" !in imageMarkup)
        check("onImageClick" !in checkNotNull(GalleryCodeCatalog.document("Button", "", 0)).source)
        stylesExample.sourcePresenter.isExpanded = true
        val tabs = checkNotNull(stylesExample.sourcePresenter.content).asWinRT<Grid>().children[0].asWinRT<SelectorBar>()
        check(tabs.items.size == 1 && tabs.items[0].text == "XAML")
    })
}
