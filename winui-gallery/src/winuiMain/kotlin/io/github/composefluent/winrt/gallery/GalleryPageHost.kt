package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.asWinRT

import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.controls.*

internal object GallerySamples {
    fun create(id: String): GalleryTheme.SamplePage = GalleryTheme.createSample {
        GalleryTheme.sampleBeingConstructed?.sourceRoute = id
        val element = GalleryPageFactories.create(id)
            ?: error("Gallery sample factory is missing for route '$id'")
        GalleryXamlValidation.onPageCreated(id, element)
        element
    }
}

// References: WinUI-Gallery/Samples/{Button,CheckBox,ToggleSwitch,Slider}.
// Controls and event handlers use the projected Kotlin API directly.
