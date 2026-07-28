package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference
import microsoft.ui.composition.systembackdrops.MicaKind
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ProgressBar
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.WebView2
import microsoft.ui.xaml.controls.XamlControlsResources
import microsoft.ui.xaml.input.KeyEventHandler
import microsoft.ui.xaml.media.MicaBackdrop
import windows.foundation.Uri
import windows.graphics.SizeInt32
import windows.system.VirtualKey

internal fun shouldRunWebView2Sample(): Boolean =
    winRTSampleOption("kotlin.winrt.samples.runWebView2Sample")

internal fun shouldSubmitWebView2Address(
    key: VirtualKey,
    navigationReady: Boolean,
): Boolean = key == VirtualKey.Enter && navigationReady

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
            configureWebView2TransparentBackground()
            println("webview2: transparent default background configured before application start")
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

        println("webview2: installing standard control resources")
        val application = checkNotNull(Application.current) {
            "Expected current WinUI application while installing standard control resources."
        }
        application.resources.mergedDictionaries.add(XamlControlsResources())
        val subtleButtonStyle = checkNotNull(application.resources["SubtleButtonStyle"]) {
            "Expected SubtleButtonStyle after installing standard control resources."
        }
        val selectedSurfaceBrush =
            checkNotNull(application.resources["LayerOnMicaBaseAltFillColorDefaultBrush"]) {
                "Expected the layer-on-Mica surface brush after installing standard control resources."
            }
        val cardStrokeBrush =
            checkNotNull(application.resources["CardStrokeColorDefaultBrush"]) {
                "Expected the default card stroke brush after installing standard control resources."
            }
        val transparentBrush =
            checkNotNull(application.resources["SubtleFillColorTransparentBrush"]) {
                "Expected the transparent subtle-fill brush after installing standard control resources."
            }
        println("webview2: standard control resources installed")

        println("webview2: creating browser shell")
        val shell =
            createWebView2BrowserShell(
                subtleButtonStyle,
                selectedSurfaceBrush,
                cardStrokeBrush,
                transparentBrush,
            )
        val address = shell.address.apply { text = DEFAULT_ADDRESS }
        val loading = shell.loading
        val status = shell.status
        val back = shell.back
        val forward = shell.forward
        val reload = shell.reload
        val home = shell.home
        val go = shell.go
        val webView = shell.webView
        browser = webView
        home.visibility = webView2HomeVisibility(INITIAL_WINDOW_WIDTH.toDouble())
        println("webview2: browser shell created")

        println("webview2: registering navigation events")
        webView.navigationStarting += { _, eventArgs ->
            executeWebView2Callback(
                failures = failures,
                name = "navigation starting callback",
                exit = ::exitAfterFatalFailure,
            ) {
                loading.visibility = Visibility.Visible
                status.visibility = Visibility.Collapsed
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
                    loading.visibility = Visibility.Collapsed
                    status.visibility = Visibility.Collapsed
                    if (autoExit) {
                        exitAfterSmoke()
                    }
                } else {
                    reportFailure(loading, status, "WebView2 navigation failed: ${eventArgs.webErrorStatus}")
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
                    reportFailure(loading, status, "WebView2 initialization failed", error)
                } else {
                    println("webview2: core initialized")
                    reload.isEnabled = true
                    home.isEnabled = true
                    go.isEnabled = true
                    println("webview2: loading embedded page")
                    try {
                        webView.navigateToString(INITIAL_DOCUMENT)
                        println("webview2: embedded page requested")
                    } catch (error: Exception) {
                        println("webview2: embedded page failed error=${error.message}")
                        reportFailure(loading, status, "Embedded WebView2 navigation failed", error)
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
        registerClick(home, "home command") {
            address.text = DEFAULT_ADDRESS
            navigate(webView, address, loading, status)
        }
        registerClick(go, "go command") {
            navigate(webView, address, loading, status)
        }
        address.keyDown +=
            KeyEventHandler { _, eventArgs ->
                executeWebView2Callback(
                    failures = failures,
                    name = "address key down callback",
                    exit = ::exitAfterFatalFailure,
                ) {
                    if (shouldSubmitWebView2Address(eventArgs.key, go.isEnabled)) {
                        eventArgs.handled = true
                        navigate(webView, address, loading, status)
                    }
                }
            }
        println("webview2: command handlers registered")

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
        mainWindow.sizeChanged += { _, eventArgs ->
            executeWebView2Callback(
                failures = failures,
                name = "window size changed callback",
                exit = ::exitAfterFatalFailure,
            ) {
                home.visibility = webView2HomeVisibility(eventArgs.size.width.toDouble())
            }
        }
        mainWindow.title = "Kotlin WinRT WebView2"
        if (!winRTSampleOption("kotlin.winrt.samples.skipMica")) {
            mainWindow.systemBackdrop = MicaBackdrop().apply { kind = MicaKind.BaseAlt }
        }
        mainWindow.extendsContentIntoTitleBar = true
        mainWindow.content = shell.root
        mainWindow.setTitleBar(shell.titleBarDragRegion)
        checkNotNull(mainWindow.appWindow) {
            "Expected AppWindow while sizing the WebView2 sample."
        }.resizeClient(SizeInt32(INITIAL_WINDOW_WIDTH, INITIAL_WINDOW_HEIGHT))
        println("webview2: window content assigned")
        mainWindow.activate()
        println("webview2: window activated")
        println("webview2: requesting core initialization")
        try {
            initializationAction = webView.ensureCoreWebView2Async()
            println("webview2: core initialization requested")
        } catch (error: Exception) {
            println("webview2: core initialization request failed error=${error.message}")
            reportFailure(loading, status, "WebView2 initialization request failed", error)
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

    private fun navigate(
        webView: WebView2,
        address: TextBox,
        loading: ProgressBar,
        status: TextBlock,
    ) {
        val normalized = normalizeWebView2Address(address.text)
        if (normalized == null) {
            loading.visibility = Visibility.Collapsed
            status.text = "Enter an HTTP or HTTPS address."
            status.visibility = Visibility.Visible
            return
        }
        address.text = normalized
        loading.visibility = Visibility.Visible
        status.visibility = Visibility.Collapsed
        try {
            webView.source = Uri(normalized)
        } catch (error: Exception) {
            reportFailure(loading, status, "WebView2 navigation failed", error)
        }
    }

    private fun reportFailure(
        loading: ProgressBar,
        status: TextBlock,
        message: String,
        cause: Throwable? = null,
    ) {
        reportWebView2Failure(
            failures = failures,
            message = message,
            cause = cause,
            renderStatus = { statusMessage ->
                println("webview2: failure message=$statusMessage")
                loading.visibility = Visibility.Collapsed
                status.text = statusMessage
                status.visibility = Visibility.Visible
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
        const val INITIAL_WINDOW_WIDTH = 1200
        const val INITIAL_WINDOW_HEIGHT = 800

        val INITIAL_DOCUMENT: String =
            """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Kotlin WinRT WebView2</title>
                <style>
                  html, body { background: transparent; }
                  body { margin: 0; font-family: "Segoe UI", sans-serif; color: #17202a; }
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
