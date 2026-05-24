package io.github.composefluent.winrt.samples.kmp.library

import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.Point
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import io.github.composefluent.winrt.samples.kmp.base.WinUiKmpBaseLibrarySample
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.dispatching.DispatcherQueueHandler
import microsoft.ui.dispatching.DispatcherQueueTimer
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.DependencyProperty
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.PropertyMetadata
import microsoft.ui.xaml.ResourceDictionary
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.automation.peers.AccessibilityView
import microsoft.ui.xaml.automation.peers.AutomationNavigationDirection
import microsoft.ui.xaml.automation.peers.AutomationPeer
import microsoft.ui.xaml.automation.peers.AutomationPeerAnnotation
import microsoft.ui.xaml.automation.peers.FrameworkElementAutomationPeer
import microsoft.ui.xaml.automation.peers.PatternInterface
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.ContentControl
import microsoft.ui.xaml.controls.Panel
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.XamlControlsResources
import windows.system.display.DisplayRequest
import kotlin.time.Duration.Companion.milliseconds
import windows.foundation.TypedEventHandler

object WinUiKmpLibrarySample {
    private var activeApplication: WinUiKmpLibraryApp? = null

    fun start() {
        WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
            println("winui-kmp-library: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
            RuntimeScope.initializeSingleThreaded().use {
                Application.start {
                    println("winui-kmp-library: application callback invoked")
                    activeApplication = WinUiKmpLibraryApp()
                    println("winui-kmp-library: application created")
                }
            }
            activeApplication?.close()
            activeApplication = null
        }
    }

    @Suppress("unused")
    private fun launcherProjectionCompileSmoke() {
        WinUiKmpBaseLibrarySample.launcherProjectionCompileSmoke()
        DisplayRequest().requestActive()
        DisplayRequest().requestRelease()
    }
}

class WinUiKmpLibraryApp : Application(), AutoCloseable {
    private var activeWindow: Window? = null
    private val activeEventTokens = mutableListOf<EventRegistrationToken>()
    private var activeTimer: DispatcherQueueTimer? = null
    private var activeTimerToken: EventRegistrationToken? = null
    private var focusSmokeCompleted = false
    @Volatile
    private var timerSmokeCompleted = false

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        launchWithResources()
    }

    override fun close() {
        activeWindow = null
        activeTimer?.stop()
        activeTimerToken?.let { token ->
            activeTimer?.tick?.remove(token)
        }
        activeTimerToken = null
        activeTimer = null
        activeEventTokens.clear()
        focusSmokeCompleted = false
        timerSmokeCompleted = false
    }

    private fun launchWithResources() {
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.timerSmoke")) {
            println("winui-kmp-library: timer smoke using current thread dispatcher")
            runTimerSmoke(DispatcherQueue.getForCurrentThread())
            return
        }
        installXamlControlsResources()
        launchCore()
    }

    private fun installXamlControlsResources() {
        println("winui-kmp-library: install resources dictionary")
        val resources = checkNotNull(this.resources) {
            "Application resources were not initialized."
        }
        println("winui-kmp-library: install resources merged dictionaries")
        val mergedDictionaries = checkNotNull(resources.mergedDictionaries) {
            "Application resources did not expose merged dictionaries."
        }
        println("winui-kmp-library: install resources create controls resources")
        val controlsResources = loadXamlControlsResources()
        println("winui-kmp-library: install resources add")
        mergedDictionaries.add(controlsResources)
        println("winui-kmp-library: install resources done")
    }

    private fun loadXamlControlsResources(): ResourceDictionary {
        return XamlControlsResources()
    }

    private fun launchCore() {
        val window = Window()
        println("winui-kmp-library: window created")
        val panel = Canvas()
        println("winui-kmp-library: canvas created")
        val button = Button()
        println("winui-kmp-library: button created")
        val textBox = TextBox()
        println("winui-kmp-library: textBox created")
        val localControl = WinUiKmpLocalContentControl()
        println("winui-kmp-library: local authored control created")
        val localPanel = WinUiKmpLocalPanel()
        println("winui-kmp-library: local authored panel created")

        button.content = "KMP library WinUI"
        println("winui-kmp-library: button content set")
        check(button.content == "KMP library WinUI") {
            "Button.content did not round-trip assigned string: ${button.content}"
        }
        println("winui-kmp-library: button content round-trip")
        button.content = null
        check(button.content == null) {
            "Button.content did not round-trip cleared null content: ${button.content}"
        }
        println("winui-kmp-library: button null content round-trip")
        button.content = "KMP library WinUI"
        textBox.text = "initial"
        println("winui-kmp-library: textBox initial text set")
        localControl.content = "Local authored control"
        localControl.sampleText = "local metadata"
        check(localControl.sampleText == "local metadata") {
            "Local authored DependencyProperty did not round-trip: ${localControl.sampleText}"
        }
        println("winui-kmp-library: local authored dependency property round-trip")
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.debugWaitBeforeChildren")) {
            println("winui-kmp-library: debug wait before canvas children; pid=${ProcessHandle.current().pid()}")
            Thread.sleep(60_000)
        }
        println("winui-kmp-library: resolving canvas children")
        val children = try {
            checkNotNull(panel.children) {
                "Canvas children collection was not initialized."
            }
        } catch (failure: Throwable) {
            println("winui-kmp-library: canvas children failed: ${failure::class.qualifiedName}: ${failure.message}")
            failure.printStackTrace()
            throw failure
        }
        println("winui-kmp-library: canvas children resolved size=${children.size}")
        println("winui-kmp-library: adding button to canvas")
        children.add(button)
        println("winui-kmp-library: button added")
        println("winui-kmp-library: adding textBox to canvas")
        children.add(textBox)
        println("winui-kmp-library: textBox added")
        println("winui-kmp-library: adding local authored control to canvas")
        children.add(localControl)
        println("winui-kmp-library: local authored control added")
        println("winui-kmp-library: adding local authored panel to canvas")
        children.add(localPanel)
        println("winui-kmp-library: local authored panel added")
        check(children[0] is Button) {
            "Canvas.children[0] did not recover the Button runtime-class wrapper: ${children[0]::class.qualifiedName}"
        }
        println("winui-kmp-library: child runtime class recovered")
        Canvas.setLeft(button, 24.0)
        Canvas.setTop(button, 12.0)
        check(Canvas.getLeft(button) == 24.0 && Canvas.getTop(button) == 12.0) {
            "Canvas attached positioning did not round-trip"
        }
        println("winui-kmp-library: canvas attached positioning set")
        AutomationProperties.setAccessibilityView(button, AccessibilityView.Raw)
        println("winui-kmp-library: detached automation accessibility view set")
        button.clearValue(checkNotNull(AutomationProperties.accessibilityViewProperty) {
            "AutomationProperties.accessibilityViewProperty was not available."
        })
        println("winui-kmp-library: detached automation accessibility view cleared")
        if (!java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipWindowContent")) {
            window.content = panel
            println("winui-kmp-library: window content set")
        } else {
            println("winui-kmp-library: window content skipped")
        }
        val localPanelPeer = FrameworkElementAutomationPeer.createPeerForElement(localPanel)
        check(WinUiKmpLocalPanel.createAutomationPeerCalls == 1) {
            "Local authored Panel OnCreateAutomationPeer was not dispatched; calls=${WinUiKmpLocalPanel.createAutomationPeerCalls}, peer=${localPanelPeer.javaClass.name}"
        }
        println("winui-kmp-library: local authored panel automation peer override dispatched")
        localPanelPeer.getPeerFromPoint(Point(24f, 24f))
        val peerPoint = WinUiKmpLocalAutomationPeer.lastPeerFromPoint
        check(peerPoint != null && peerPoint.x == 24f && peerPoint.y == 24f) {
            "Authored AutomationPeer.GetPeerFromPointCore received $peerPoint"
        }
        println("winui-kmp-library: local authored automation peer point ABI round-tripped")
        activeWindow = window
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipCallbackSmoke")) {
            println("winui-kmp-library: callbacks skipped")
            window.activate()
            println("winui-kmp-library: window activated native")
            return
        }
        registerCallbackSmoke(window, button, textBox)
        println("winui-kmp-library: callbacks registered")
        window.activate()
        println("winui-kmp-library: window activated native")
    }

    private fun registerCallbackSmoke(
        window: Window,
        button: Button,
        textBox: TextBox,
    ) {
        println("winui-kmp-library: registering window activated")
        activeEventTokens += window.activated.add { _, _ ->
            println("winui-kmp-library: window activated callback")
            textBox.text = "activated"
        }
        println("winui-kmp-library: registering window visibility")
        activeEventTokens += window.visibilityChanged.add { _, _ ->
            println("winui-kmp-library: window visibility callback")
        }
        println("winui-kmp-library: registering button loaded")
        activeEventTokens += button.loaded.add { _, _ ->
            println("winui-kmp-library: button loaded callback")
        }
        if (!java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipLayoutUpdated")) {
            println("winui-kmp-library: registering button layout")
            activeEventTokens += button.layoutUpdated.add { _, _ ->
                println("winui-kmp-library: button layout callback")
                if (!focusSmokeCompleted) {
                    focusSmokeCompleted = true
                    runFocusSmoke(window, button, textBox)
                }
            }
        } else {
            println("winui-kmp-library: button layout skipped")
        }
        println("winui-kmp-library: registering button focus")
        activeEventTokens += button.gotFocus.add { _, _ ->
            println("winui-kmp-library: button focus callback")
        }
        println("winui-kmp-library: registering button pointer")
        activeEventTokens += button.pointerPressed.add { _, _ ->
            println("winui-kmp-library: button pointer callback")
        }
        println("winui-kmp-library: registering text changed")
        activeEventTokens += textBox.textChanged.add { _, _ ->
            println("winui-kmp-library: text changed callback")
        }
        println("winui-kmp-library: registering text changing")
        activeEventTokens += textBox.textChanging.add { _, _ ->
            println("winui-kmp-library: text changing callback")
        }
        println("winui-kmp-library: registering before text changing")
        activeEventTokens += textBox.beforeTextChanging.add { _, _ ->
            println("winui-kmp-library: before text changing callback")
        }
        println("winui-kmp-library: registering text getting focus")
        activeEventTokens += textBox.gettingFocus.add { _, _ ->
            println("winui-kmp-library: text getting focus callback")
        }
        println("winui-kmp-library: registering text losing focus")
        activeEventTokens += textBox.losingFocus.add { _, _ ->
            println("winui-kmp-library: text losing focus callback")
        }
        println("winui-kmp-library: registering text unloaded")
        activeEventTokens += textBox.unloaded.add { _, _ ->
            println("winui-kmp-library: text unloaded callback")
            if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
                checkNotNull(window.dispatcherQueue) {
                    "Window dispatcher queue was not available."
                }.tryEnqueue(DispatcherQueueHandler {
                    println("winui-kmp-library: unloaded auto exit enqueued")
                    exit()
                })
            }
        }
    }

    private fun runFocusSmoke(
        window: Window,
        button: Button,
        textBox: TextBox,
    ) {
        button.isTabStop = true
        textBox.isTabStop = true
        println("winui-kmp-library: focus smoke starting")
        val buttonFocused = button.focus(FocusState.Programmatic)
        println("winui-kmp-library: button focus result=$buttonFocused state=${button.focusState}")
        val textBoxFocused = textBox.focus(FocusState.Programmatic)
        println("winui-kmp-library: textBox focus result=$textBoxFocused state=${textBox.focusState}")
        check(buttonFocused || textBoxFocused) {
            "WinUI controls rejected programmatic focus after layout"
        }
        textBox.text = "changed"
        println("winui-kmp-library: textBox changed after focus")
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
            if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.timerSmoke")) {
                runTimerSmoke(checkNotNull(window.dispatcherQueue) {
                    "Window dispatcher queue was not available."
                })
                return
            }
            checkNotNull(window.dispatcherQueue) {
                "Window dispatcher queue was not available."
            }.tryEnqueue(DispatcherQueueHandler {
                println("winui-kmp-library: auto exit enqueued")
                exit()
            })
        }
    }

    private fun runTimerSmoke(dispatcherQueue: DispatcherQueue) {
        println("winui-kmp-library: timer smoke starting")
        val timer = dispatcherQueue.createTimer()
        activeTimer = timer
        timer.interval = 16.milliseconds
        timer.isRepeating = false
        println("winui-kmp-library: timer interval=${timer.interval} repeating=${timer.isRepeating} runningBefore=${timer.isRunning}")
        activeTimerToken = timer.tick.add(TypedEventHandler { _, _ ->
            timerSmokeCompleted = true
            println("winui-kmp-library: timer tick callback running=${timer.isRunning}")
            timer.stop()
            exit()
        })
        println("winui-kmp-library: timer token=$activeTimerToken")
        timer.start()
        println("winui-kmp-library: timer started runningAfter=${timer.isRunning}")
        Thread {
            Thread.sleep(1_500)
            dispatcherQueue.tryEnqueue(DispatcherQueueHandler {
                println("winui-kmp-library: timer timeout completed=$timerSmokeCompleted running=${timer.isRunning}")
                check(timerSmokeCompleted) {
                    "DispatcherQueueTimer did not tick before timeout."
                }
                exit()
            })
        }.also { thread ->
            thread.name = "kotlin-winrt timer smoke timeout"
            thread.isDaemon = true
            thread.start()
        }
    }
}

internal class WinUiKmpLocalContentControl : ContentControl() {
    init {
        println("winui-kmp-library: local authored control init")
    }

    var sampleText: String?
        get() = getValue(SampleTextProperty) as String?
        set(value) {
            setValue(SampleTextProperty, value)
        }

    companion object {
        val SampleTextProperty: DependencyProperty = run {
            println("winui-kmp-library: local authored control register SampleTextProperty start")
            val property = DependencyProperty.register(
                "SampleText",
                String::class,
                WinUiKmpLocalContentControl::class,
                PropertyMetadata(null),
            )
            println("winui-kmp-library: local authored control register SampleTextProperty done")
            property
        }
    }
}

internal class WinUiKmpLocalPanel : Panel() {
    override fun onCreateAutomationPeer(): AutomationPeer {
        createAutomationPeerCalls += 1
        return WinUiKmpLocalAutomationPeer(exposeHitTestChild = true)
    }

    companion object {
        var createAutomationPeerCalls: Int = 0
            private set
    }
}

internal class WinUiKmpLocalAutomationPeer(
    private val exposeHitTestChild: Boolean = false,
) : AutomationPeer() {
    private val hitTestChild: AutomationPeer? =
        if (exposeHitTestChild) WinUiKmpLocalAutomationPeer() else null

    override fun getPeerFromPointCore(point: Point): AutomationPeer {
        println("winui-kmp-library: local authored automation peer getPeerFromPointCore point=${point.x},${point.y}")
        lastPeerFromPoint = point
        return hitTestChild ?: this
    }

    override fun getPatternCore(patternInterface: PatternInterface): Any? = null

    override fun navigateCore(direction: AutomationNavigationDirection): Any? = null

    override fun getElementFromPointCore(pointInWindowCoordinates: Point): Any? = null

    override fun getFocusedElementCore(): Any? = null

    override fun getChildrenCore(): MutableList<AutomationPeer> =
        hitTestChild?.let { mutableListOf(it) } ?: mutableListOf()

    override fun getControlledPeersCore(): List<AutomationPeer> = emptyList()

    override fun getAnnotationsCore(): MutableList<AutomationPeerAnnotation> = mutableListOf()

    override fun getDescribedByCore(): Iterable<AutomationPeer> = emptyList()

    override fun getFlowsToCore(): Iterable<AutomationPeer> = emptyList()

    override fun getFlowsFromCore(): Iterable<AutomationPeer> = emptyList()

    companion object {
        var lastPeerFromPoint: Point? = null
    }
}
