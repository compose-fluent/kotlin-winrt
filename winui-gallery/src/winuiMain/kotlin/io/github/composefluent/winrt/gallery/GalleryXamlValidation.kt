package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.UIElement
import io.github.composefluent.winrt.gallery.basicinput.*
import io.github.composefluent.winrt.gallery.validation.*

/** Explicit native integration checks; ordinary launches never synthesize interactions. */
internal object GalleryXamlValidation {
    private var targetRoute: String? = null

    fun routeArgument(value: String): String {
        if (!value.startsWith("--validate-xaml=")) return value
        return value.removePrefix("--validate-xaml=").also { targetRoute = it }
    }

    fun onPageCreated(route: String, element: UIElement) {
        if (!route.equals(targetRoute, ignoreCase = true)) return
        when (route) {
            "Button" -> validateButtonPage(element as ButtonPage)
            "CheckBox" -> validateCheckBoxPage(element as CheckBoxPage)
            "RepeatButton" -> validateRepeatButtonPage(element as RepeatButtonPage)
            "ToggleButton" -> validateToggleButtonPage(element as ToggleButtonPage)
            "ToggleSwitch" -> validateToggleSwitchPage(element as ToggleSwitchPage)
            else -> error("No native XAML validation registered for '$route'")
        }
    }

    fun onLoaded(route: String, element: FrameworkElement, trigger: () -> Unit, verify: () -> Unit) =
        onLoadedSteps(route, element, listOf(trigger, verify))

    fun onLoadedSteps(route: String, element: FrameworkElement, steps: List<() -> Unit>) {
        if (!route.equals(targetRoute, ignoreCase = true)) return
        require(steps.isNotEmpty())
        var started = false
        fun runStep(index: Int): Unit = checked(route) {
            steps[index]()
            if (index == steps.lastIndex) {
                println("Gallery XAML validation passed: $route")
            } else {
                check(checkNotNull(element.dispatcherQueue).tryEnqueue { runStep(index + 1) })
            }
        }
        element.loaded.add { _, _ ->
            if (!started) {
                started = true
                runStep(0)
            }
        }
    }

    private fun checked(route: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            println("Gallery XAML validation failed: $route\n${error.stackTraceToString()}")
            throw error
        }
    }
}
