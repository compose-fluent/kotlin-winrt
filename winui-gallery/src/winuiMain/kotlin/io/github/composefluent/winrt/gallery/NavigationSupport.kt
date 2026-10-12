// Ported from WinUI Gallery Samples/{BreadcrumbBar,Pivot,SelectorBar,NavigationView} (MIT).
package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.animation.SlideNavigationTransitionInfo
import microsoft.ui.xaml.media.animation.SlideNavigationTransitionEffect

internal fun sampleFrame() = Frame().apply {
    navigated.add { _, args ->
        checkNotNull(args.content).asWinRT<Page>().content = if (args.parameter.toString() == "0") Grid().apply {
            children!!.add(label("Sample Settings Page", 28.0).apply {
                horizontalAlignment = HorizontalAlignment.Center; verticalAlignment = VerticalAlignment.Center
            })
        } else sampleContent(args.parameter.toString().toInt())
    }
}
