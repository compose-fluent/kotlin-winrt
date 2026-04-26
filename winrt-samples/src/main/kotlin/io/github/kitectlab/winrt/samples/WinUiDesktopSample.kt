package io.github.kitectlab.winrt.samples

data class WinUiDesktopSampleResult(
    val started: Boolean,
    val initialWindowContent: String,
    val windowActivated: Boolean,
    val buttonClickHandled: Boolean,
    val finalWindowContent: String,
    val tappedHandlerRegistered: Boolean,
)

object WinUiDesktopSample {
    const val initialButtonContent: String = "Click me to load MainPage"
    const val mainPageText: String = "Hello from WinUI Desktop!"
    const val tappedEventName: String = "UIElement.TappedEvent"

    fun runHeadlessFlow(): WinUiDesktopSampleResult {
        val app = App()
        val window = app.onLaunched()
        val initialContent = window.content.description
        val activated = window.isActivated
        val clickHandled = (window.content as Button).click()
        val page = window.content as MainPage
        return WinUiDesktopSampleResult(
            started = true,
            initialWindowContent = initialContent,
            windowActivated = activated,
            buttonClickHandled = clickHandled,
            finalWindowContent = page.description,
            tappedHandlerRegistered = tappedEventName in page.registeredHandlers,
        )
    }

    private class App {
        private lateinit var window: Window

        fun onLaunched(): Window {
            val button = Button(
                content = initialButtonContent,
                horizontalAlignment = "Center",
                verticalAlignment = "Center",
            )
            button.onClick = ::buttonClick
            window = Window(content = button)
            window.activate()
            return window
        }

        private fun buttonClick(): Boolean {
            window.content = MainPage()
            return true
        }
    }

    private class Window(
        var content: Content,
    ) {
        var isActivated: Boolean = false
            private set

        fun activate() {
            isActivated = true
        }
    }

    private interface Content {
        val description: String
    }

    private class Button(
        val content: String,
        val horizontalAlignment: String,
        val verticalAlignment: String,
    ) : Content {
        var onClick: (() -> Boolean)? = null

        override val description: String
            get() = content

        fun click(): Boolean = onClick?.invoke() == true
    }

    private class MainPage : Content {
        val registeredHandlers: Set<String> = setOf(tappedEventName)

        override val description: String = mainPageText
    }
}
