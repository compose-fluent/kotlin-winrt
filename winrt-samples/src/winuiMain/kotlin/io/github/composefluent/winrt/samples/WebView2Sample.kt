package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Orientation
import microsoft.ui.xaml.controls.StackPanel
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.WebView2
import windows.foundation.Uri

internal fun shouldRunWebView2Sample(): Boolean =
    winRTSampleOption("kotlin.winrt.samples.runWebView2Sample")

internal class WebView2SampleFailures(
    private val autoExit: Boolean,
) {
    private var failure: IllegalStateException? = null

    fun recordForSmoke(message: String, cause: Throwable? = null): Boolean {
        if (!autoExit) {
            return false
        }
        record(message, cause)
        return true
    }

    fun recordFatal(message: String, cause: Throwable) {
        record(message, cause)
    }

    fun throwIfPresent() {
        failure?.let { throw it }
    }

    private fun record(message: String, cause: Throwable?) {
        val error = IllegalStateException(message, cause)
        val current = failure
        if (current == null) {
            failure = error
        } else {
            current.addSuppressed(error)
        }
    }
}

internal fun normalizeWebView2Address(address: String): String? {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    if (trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
    ) {
        return trimmed
    }
    if (WEBVIEW2_URI_SCHEME.containsMatchIn(trimmed) && !WEBVIEW2_HOST_WITH_PORT.matches(trimmed)) {
        return null
    }
    return "https://$trimmed"
}

private val WEBVIEW2_URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val WEBVIEW2_HOST_WITH_PORT = Regex(
    "^(?:localhost|(?:[^\\s/:?#.]+\\.)+[^\\s/:?#.]+|\\[[0-9A-Fa-f:.]+]):\\d{1,5}(?:[/?#].*)?$",
    RegexOption.IGNORE_CASE,
)

internal fun executeWebView2Cleanup(actions: List<() -> Unit>) {
    var failure: Exception? = null
    actions.forEach { action ->
        try {
            action()
        } catch (error: Exception) {
            val current = failure
            if (current == null) {
                failure = error
            } else if (current !== error) {
                current.addSuppressed(error)
            }
        }
    }
    failure?.let { throw it }
}

internal fun closeWebView2Resources(
    closeInitializationAction: (() -> Unit)?,
    closeWebView: (() -> Unit)?,
) {
    executeWebView2Cleanup(listOfNotNull(closeInitializationAction, closeWebView))
}

internal fun reportWebView2Failure(
    failures: WebView2SampleFailures,
    message: String,
    cause: Throwable? = null,
    renderStatus: (String) -> Unit,
    exit: () -> Unit,
) {
    val detail = cause?.message?.takeIf(String::isNotBlank)
    val statusMessage = detail?.let { "$message: $it" } ?: message
    val exitForSmoke = failures.recordForSmoke(statusMessage, cause)
    var exitForStatusFailure = false
    try {
        renderStatus(statusMessage)
    } catch (error: Exception) {
        failures.recordFatal("WebView2 status update failed", error)
        exitForStatusFailure = true
    }
    if (exitForSmoke || exitForStatusFailure) {
        try {
            exit()
        } catch (error: Exception) {
            failures.recordFatal("WebView2 automatic exit failed", error)
        }
    }
}

internal fun executeWebView2Callback(
    failures: WebView2SampleFailures,
    name: String,
    exit: () -> Unit,
    action: () -> Unit,
) {
    try {
        action()
    } catch (error: Exception) {
        failures.recordFatal("WebView2 $name failed", error)
        try {
            exit()
        } catch (exitError: Exception) {
            failures.recordFatal("WebView2 automatic exit failed", exitError)
        }
    }
}

object WebView2Sample {
    private var activeApplication: WebView2SampleApp? = null

    fun start() {
        val autoExit = winRTSampleOption("kotlin.winrt.samples.autoExitWinUi")
        val failures = WebView2SampleFailures(autoExit)
        try {
            Application.start {
                executeWebView2Callback(
                    failures = failures,
                    name = "application composition callback",
                    exit = {
                        checkNotNull(Application.current) {
                            "Expected current WinUI application while handling a composition failure."
                        }.exit()
                    },
                ) {
                    activeApplication = WebView2SampleApp(autoExit, failures)
                    println("webview2: application composed")
                }
            }
        } catch (error: Exception) {
            failures.recordFatal("WebView2 application start failed", error)
        } finally {
            activeApplication = null
        }
        failures.throwIfPresent()
    }
}

internal class WebView2SampleApp(
    private val autoExit: Boolean,
    private val failures: WebView2SampleFailures,
) : Application(), AutoCloseable {
    private var closed = false
    private var window: Window? = null
    private var browser: WebView2? = null
    private var initializationAction: WinRTAsyncActionReference? = null

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        executeWebView2Callback(
            failures = failures,
            name = "launch callback",
            exit = ::exitAfterFatalFailure,
            action = ::launch,
        )
    }

    private fun launch() {
        println("webview2: onLaunched")

        println("webview2: creating navigation controls")
        val address = TextBox().apply {
            text = DEFAULT_ADDRESS
            width = 560.0
        }
        val status = TextBlock().apply {
            text = "Loading embedded page..."
        }
        val back = navigationButton("Back").apply { isEnabled = false }
        val forward = navigationButton("Forward").apply { isEnabled = false }
        val reload = navigationButton("Reload").apply { isEnabled = false }
        val go = navigationButton("Go").apply { isEnabled = false }
        println("webview2: navigation controls created")
        println("webview2: creating WebView2 control")
        val webView = WebView2()
        browser = webView
        webView.width = 960.0
        webView.height = 600.0
        println("webview2: WebView2 control created")

        println("webview2: registering navigation events")
        webView.navigationStarting += { _, eventArgs ->
            executeWebView2Callback(
                failures = failures,
                name = "navigation starting callback",
                exit = ::exitAfterFatalFailure,
            ) {
                status.text = "Loading ${eventArgs.uri}"
                println("webview2: navigation starting uri=${eventArgs.uri}")
            }
        }
        webView.navigationCompleted += { _, eventArgs ->
            executeWebView2Callback(
                failures = failures,
                name = "navigation completed callback",
                exit = ::exitAfterFatalFailure,
            ) {
                back.isEnabled = webView.canGoBack
                forward.isEnabled = webView.canGoForward
                println("webview2: navigation completed success=${eventArgs.isSuccess}")
                if (eventArgs.isSuccess) {
                    status.text = "Ready"
                    if (autoExit) {
                        exitAfterSmoke()
                    }
                } else {
                    reportFailure(status, "WebView2 navigation failed: ${eventArgs.webErrorStatus}")
                }
            }
        }
        webView.coreWebView2Initialized += { _, eventArgs ->
            executeWebView2Callback(
                failures = failures,
                name = "core initialization callback",
                exit = ::exitAfterFatalFailure,
            ) {
                if (webView.coreWebView2 == null) {
                    val error = eventArgs.exception
                    println("webview2: core initialization failed error=${error.message}")
                    reportFailure(status, "WebView2 initialization failed", error)
                } else {
                    println("webview2: core initialized")
                    reload.isEnabled = true
                    go.isEnabled = true
                    println("webview2: loading embedded page")
                    try {
                        webView.navigateToString(INITIAL_DOCUMENT)
                        println("webview2: embedded page requested")
                    } catch (error: Exception) {
                        println("webview2: embedded page failed error=${error.message}")
                        reportFailure(status, "Embedded WebView2 navigation failed", error)
                    }
                }
            }
        }
        println("webview2: navigation events registered")

        println("webview2: registering command handlers")
        registerClick(back, "back command") {
            if (webView.canGoBack) {
                webView.goBack()
            }
        }
        registerClick(forward, "forward command") {
            if (webView.canGoForward) {
                webView.goForward()
            }
        }
        registerClick(reload, "reload command") {
            webView.reload()
        }
        registerClick(go, "go command") {
            navigate(webView, address, status)
        }
        println("webview2: command handlers registered")

        println("webview2: composing content")
        val toolbar = StackPanel().apply {
            orientation = Orientation.Horizontal
            spacing = 8.0
            children.add(back)
            children.add(forward)
            children.add(reload)
            children.add(address)
            children.add(go)
        }
        val root = StackPanel().apply {
            padding = Thickness(16.0, 16.0, 16.0, 16.0)
            spacing = 10.0
            children.add(toolbar)
            children.add(status)
            children.add(webView)
        }
        println("webview2: content composed")

        println("webview2: creating window")
        val mainWindow = Window()
        window = mainWindow
        mainWindow.closed += { _, _ ->
            executeWebView2Callback(
                failures = failures,
                name = "window closed callback",
                exit = ::exitAfterFatalFailure,
            ) {
                close()
            }
        }
        mainWindow.title = "Kotlin WinRT WebView2"
        mainWindow.content = root
        println("webview2: window content assigned")
        mainWindow.activate()
        println("webview2: window activated")
        println("webview2: requesting core initialization")
        try {
            initializationAction = webView.ensureCoreWebView2Async()
            println("webview2: core initialization requested")
        } catch (error: Exception) {
            println("webview2: core initialization request failed error=${error.message}")
            reportFailure(status, "WebView2 initialization request failed", error)
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        val webView = browser
        val pendingInitialization = initializationAction
        try {
            closeWebView2Resources(
                closeInitializationAction = pendingInitialization?.let { it::close },
                closeWebView = webView?.let { it::close },
            )
        } finally {
            initializationAction = null
            browser = null
            window = null
        }
    }

    private fun navigationButton(label: String): Button =
        Button().apply {
            content = label
        }

    private fun registerClick(button: Button, name: String, handler: () -> Unit) {
        button.click +=
            RoutedEventHandler { _, _ ->
                executeWebView2Callback(
                    failures = failures,
                    name = "$name callback",
                    exit = ::exitAfterFatalFailure,
                    action = handler,
                )
            }
    }

    private fun navigate(webView: WebView2, address: TextBox, status: TextBlock) {
        val normalized = normalizeWebView2Address(address.text)
        if (normalized == null) {
            status.text = "Enter an HTTP or HTTPS address."
            return
        }
        address.text = normalized
        try {
            webView.source = Uri(normalized)
        } catch (error: Exception) {
            reportFailure(status, "WebView2 navigation failed", error)
        }
    }

    private fun reportFailure(status: TextBlock, message: String, cause: Throwable? = null) {
        reportWebView2Failure(
            failures = failures,
            message = message,
            cause = cause,
            renderStatus = { statusMessage ->
                println("webview2: failure message=$statusMessage")
                status.text = statusMessage
            },
            exit = ::exitAfterSmoke,
        )
    }

    private fun exitAfterSmoke() {
        exitApplication("WebView2 cleanup failed before smoke exit")
    }

    private fun exitAfterFatalFailure() {
        exitApplication("WebView2 cleanup failed after a fatal failure")
    }

    private fun exitApplication(cleanupFailureMessage: String) {
        try {
            close()
        } catch (error: Exception) {
            failures.recordFatal(cleanupFailureMessage, error)
        }
        try {
            checkNotNull(Application.current) {
                "Expected current WinUI application while exiting the WebView2 sample."
            }.exit()
        } catch (error: Exception) {
            failures.recordFatal("WebView2 application exit failed", error)
        }
    }

    private companion object {
        const val DEFAULT_ADDRESS = "https://example.com"

        val INITIAL_DOCUMENT: String =
            """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Kotlin WinRT WebView2</title>
                <style>
                  body { margin: 0; font-family: "Segoe UI", sans-serif; color: #17202a; background: #f4f7f9; }
                  main { max-width: 720px; margin: 96px auto; padding: 0 32px; }
                  h1 { font-size: 42px; font-weight: 650; margin: 8px 0 16px; }
                  p { max-width: 560px; font-size: 18px; line-height: 1.6; color: #46515c; }
                  .label { color: #0067c0; font-size: 14px; font-weight: 600; }
                  a { color: #0067c0; }
                </style>
              </head>
              <body>
                <main>
                  <div class="label">KOTLIN / WINRT</div>
                  <h1>WebView2</h1>
                  <p>This offline document is rendered by the projected WinUI WebView2 control.</p>
                  <p><a href="https://example.com">Open example.com</a></p>
                </main>
              </body>
            </html>
            """.trimIndent()
    }
}
