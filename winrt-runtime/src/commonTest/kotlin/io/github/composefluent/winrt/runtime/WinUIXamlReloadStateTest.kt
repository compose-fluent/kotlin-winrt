package io.github.composefluent.winrt.runtime

import kotlin.test.*

/** Generated typed access mirrors CsWinRT GeneratedCustomPropertyProvider; the
 * runtime owns dispatch and instance lifetime, never SDK-specific serialization. */
class WinUIXamlReloadStateTest {
    private class Control(var text: String)
    @Test fun state_roundtrips_with_bounded_unique_names() {
        val state = listOf(WinUIXamlSavedElement("Input", "TextBox", listOf(WinUIXamlVisualProperty("Text", "输入 🌍"))))
        val request = WinUIXamlStateRequest("Page", "Page.xaml", restore = state)
        val command = WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeStateRequest("token", request))
        assertEquals(request, command.state)
        assertNull(command.patch)
        val reply = WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.APPLIED, "saved", savedState = state)
        assertEquals(reply, WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(reply)))
        val marked = WinUIXamlVisualNode(emptyList(), "Grid", "", WinUIXamlVisualBounds(0.0, 0.0, 1.0, 1.0), sourceId = "d1:source:0.1")
        val inspected = reply.copy(inspection = WinUIXamlVisualSnapshot(listOf(marked), emptyList()))
        assertEquals(inspected, WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(inspected)))

        assertFailsWith<IllegalArgumentException> { WinUIXamlHotReloadCodec.writeStateRequest("token", request.copy(restore = state + state)) }
        assertFailsWith<IllegalArgumentException> { validateWinUIXamlSavedState(listOf(state.single().copy(values = listOf(WinUIXamlVisualProperty("Text", "x".repeat(131073)))))) }
    }
    @Test fun compiled_binding_ownership_is_supplied_for_both_capture_and_restore() {
        val queue = mutableListOf<() -> Unit>()
        val calls = mutableListOf<Set<String>>()
        val registry = WinUIXamlHotReloadRegistry({ { task -> queue += task; true } }, { _, text -> text },
            stateAdapter = WinUIXamlStateAdapter({ _, protectedProperties ->
                calls += protectedProperties
                WinUIXamlSavedElement("", "TextBox", listOf(WinUIXamlVisualProperty("SelectionStart", "0")))
            }, { _, _, protectedProperties -> calls += protectedProperties }))
        val owner = Any(); val control = Control("bound")
        registry.observe(owner, "Page", "Page.xaml", "a".repeat(64), "Input", control, compiledBindings = setOf("Text"))
        registry.observeEvent(owner, "Page", "Page.xaml", "a".repeat(64), "Input", control, "Changed", WinUIXamlEventRoute("Handler", arrayOf("Handler")))
        registry.observe(owner, "Page", "Page.xaml", "a".repeat(64), null, null)
        var state: List<WinUIXamlSavedElement>? = null
        registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml")) { state = it.savedState }
        queue.removeAt(0).invoke()
        registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml", restore = assertNotNull(state))) {}
        queue.removeAt(0).invoke()
        assertEquals(listOf(setOf("Text"), setOf("Text"), setOf("Text")), calls)
    }
    @Test fun restores_only_selected_instance_and_rolls_back_without_replay() {
        val queue = mutableListOf<() -> Unit>()
        val adapter = WinUIXamlStateAdapter({ value, _ -> (value as? Control)?.let {
            WinUIXamlSavedElement("", "Control", listOf(WinUIXamlVisualProperty("Text", it.text)))
        } }, { value, state, _ ->
            (value as Control).text = state.values.single().value
            check(value.text != "reject")
        })
        val registry = WinUIXamlHotReloadRegistry({ { task -> queue += task; true } }, { _, text -> text }, stateAdapter = adapter)
        val owner = Any(); val first = Control("first"); val second = Control("second")
        val hash = "a".repeat(64)
        registry.observe(owner, "Page", "Page.xaml", hash, "First", first)
        registry.observe(owner, "Page", "Page.xaml", hash, "Second", second)
        registry.observe(owner, "Page", "Page.xaml", hash, null, null)
        var reply: WinUIXamlHotReloadReply? = null
        fun transfer(state: List<WinUIXamlSavedElement>? = null) {
            registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml", restore = state)) { reply = it }
            assertTrue(queue.isNotEmpty()); queue.removeAt(0).invoke()
        }
        transfer()
        val saved = assertNotNull(reply?.savedState)
        val invalid = saved.map { it.copy(values = listOf(WinUIXamlVisualProperty("Text", if (it.name == "Second") "reject" else "updated"))) }
        transfer(invalid)
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, reply?.status)
        assertEquals("first", first.text); assertEquals("second", second.text)
        first.text = "edited"; transfer(saved)
        assertEquals("first", first.text)
        val laterOwner = Any(); val later = Control("fresh")
        registry.observe(laterOwner, "Page", "Page.xaml", hash, "First", later)
        registry.observe(laterOwner, "Page", "Page.xaml", hash, null, null)
        assertEquals("fresh", later.text)
        assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        transfer(saved.map { it.copy(kind = "ChangedType") })
        assertEquals("first", first.text)
    }
    @Test fun named_component_identity_resolves_when_parent_connects_before_child_load_completes() {
        // XAMLC Connect can precede the child's InitializeComponent completion;
        // CsWinRT's generated connector owns the namescope edge in either order.
        val registry = WinUIXamlHotReloadRegistry({ { task -> task(); true } }, { _, text -> text })
        val window = Any(); val left = Any(); val right = Any(); val hash = "a".repeat(64)
        registry.observe(window, "Window", "Window.xaml", hash, "Left", left)
        registry.observe(window, "Window", "Window.xaml", hash, "Right", right)
        registry.observe(window, "Window", "Window.xaml", hash, null, null)
        registry.observe(right, "Control", "Control.xaml", hash, null, null)
        registry.observe(left, "Control", "Control.xaml", hash, null, null)
        val keys = registry.snapshot().roots.single { it.className == "Control" }.stateKeys
        assertEquals(2, keys.size)
        assertTrue(keys[0].endsWith("/5:Right"))
        assertTrue(keys[1].endsWith("/4:Left"))
        registry.observe(window, "Window", "Window.xaml", hash, "OtherLeft", left)
        assertEquals("", registry.snapshot().roots.single { it.className == "Control" }.stateKeys[1])
    }

    @Test fun named_component_state_survives_reordered_creation_and_rejects_ambiguous_aliases() {
        fun registry() = WinUIXamlHotReloadRegistry({ { task -> task(); true } }, { _, text -> text },
            stateAdapter = WinUIXamlStateAdapter({ value, _ -> (value as? Control)?.let {
                WinUIXamlSavedElement("", "Control", listOf(WinUIXamlVisualProperty("Text", it.text)))
            } }, { value, state, _ -> (value as Control).text = state.values.single().value }))
        val hash = "a".repeat(64)
        fun connect(registry: WinUIXamlHotReloadRegistry, owner: Any, input: Control) {
            registry.observe(owner, "Control", "Control.xaml", hash, "Input", input)
            registry.observe(owner, "Control", "Control.xaml", hash, null, null)
        }
        val old = registry(); val window = Any(); val leftOwner = Any(); val rightOwner = Any()
        val left = Control("left input"); val right = Control("right input")
        connect(old, leftOwner, left); connect(old, rightOwner, right)
        old.observe(window, "Window", "Window.xaml", hash, "Left", leftOwner)
        old.observe(window, "Window", "Window.xaml", hash, "Right", rightOwner)
        old.observe(window, "Window", "Window.xaml", hash, null, null)
        val root = old.snapshot().roots.single { it.className == "Control" }
        assertEquals(2, root.stateKeys.distinct().size)
        assertTrue(root.stateKeys.all { it.isNotEmpty() })
        val saved = root.stateKeys.map { key ->
            var reply: WinUIXamlHotReloadReply? = null
            old.transferState(WinUIXamlStateRequest("Control", "Control.xaml", componentKey = key)) { reply = it }
            key to assertNotNull(reply?.savedState)
        }
        assertEquals(root, WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(old.snapshot())).roots.single { it.className == "Control" })
        val replacement = registry(); val nextWindow = Any(); val nextLeftOwner = Any(); val nextRightOwner = Any()
        val nextLeft = Control("initial left"); val nextRight = Control("initial right")
        connect(replacement, nextRightOwner, nextRight); connect(replacement, nextLeftOwner, nextLeft)
        replacement.observe(nextWindow, "Window", "Window.xaml", hash, "Right", nextRightOwner)
        replacement.observe(nextWindow, "Window", "Window.xaml", hash, "Left", nextLeftOwner)
        replacement.observe(nextWindow, "Window", "Window.xaml", hash, null, null)
        saved.forEach { (key, state) ->
            val request = WinUIXamlStateRequest("Control", "Control.xaml", restore = state, componentKey = key)
            assertEquals(request, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeStateRequest("token", request)).state)
            replacement.transferState(request) { assertEquals(WinUIXamlHotReloadProtocol.APPLIED, it.status) }
        }
        assertEquals("left input", nextLeft.text); assertEquals("right input", nextRight.text)
        replacement.observe(nextWindow, "Window", "Window.xaml", hash, "AliasOfLeft", nextLeftOwner)
        assertEquals("", replacement.snapshot().roots.single { it.className == "Control" }.stateKeys[1])
        replacement.transferState(WinUIXamlStateRequest("Control", "Control.xaml", componentKey = saved[0].first, restore = saved[0].second)) {
            assertEquals(WinUIXamlHotReloadProtocol.UNAVAILABLE, it.status)
        }
    }

    @Test fun dynamic_controls_and_versioned_business_checkpoints_restore_transactionally() {
        // CsWinRT generated custom-property delegates remain the typed UI boundary;
        // application checkpoints contain data rather than serialized continuations.
        class Model : WinUIXamlRestartParticipant {
            override var restartStateSchema = "Search/v1"
            var query = "old query"
            var resumes = 0
            override fun saveRestartState() = mapOf("Query" to query, "ResumeFrom" to "42")
            override fun restoreRestartState(values: Map<String, String>) {
                query = values.getValue("Query"); resumes++
            }
        }
        val model = Model(); val dynamic = Control("dynamic input"); val owner = Any()
        val registry = WinUIXamlHotReloadRegistry({ { task -> task(); true } }, { _, text -> text },
            stateAdapter = WinUIXamlStateAdapter({ value, _ -> (value as? Control)?.let {
                WinUIXamlSavedElement("", "Control", listOf(WinUIXamlVisualProperty("Text", it.text)))
            } }, { value, state, _ -> (value as Control).text = state.values.single().value },
                discover = { mapOf("\$id/Search" to dynamic, "\$context/root" to model) }))
        registry.observe(owner, "Page", "Page.xaml", "a".repeat(64), null, null)
        var saved: List<WinUIXamlSavedElement>? = null
        registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml")) { saved = it.savedState }
        val checkpoint = assertNotNull(saved)
        assertEquals(2, checkpoint.size)
        assertTrue(checkpoint.any { it.kind == "Participant:Search/v1" })
        dynamic.text = "reset"; model.query = "reset"
        registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml", restore = checkpoint)) {
            assertEquals(WinUIXamlHotReloadProtocol.APPLIED, it.status)
        }
        assertEquals("dynamic input", dynamic.text); assertEquals("old query", model.query)
        assertEquals(1, model.resumes)
        model.restartStateSchema = "Search/v2"; model.query = "new schema"
        registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml", restore = checkpoint)) {}
        assertEquals("new schema", model.query)
        assertEquals(1, model.resumes)
    }

    @Test fun business_checkpoint_limits_are_shared_by_wire_and_runtime() {
        val many = (0 until 17).map { WinUIXamlVisualProperty("key$it", "value$it") }
        val saved = listOf(WinUIXamlSavedElement("\$participant/\$context/root", "Participant:Model/v1", many))
        val reply = WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.APPLIED, "saved", savedState = saved)
        assertEquals(reply, WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(reply)))
        assertFailsWith<IllegalArgumentException> {
            validateWinUIXamlSavedState(saved.map { it.copy(values = List(129) { n -> WinUIXamlVisualProperty("key$n", "v") }) })
        }
    }

    @Test fun navigation_recreates_the_target_before_restoring_dynamic_inputs() {
        class Frame(var route: String)
        val frame = Frame("page2"); var input = Control("saved input")
        val owner = Any()
        val adapter = WinUIXamlStateAdapter({ value, _ -> when (value) {
            is Frame -> WinUIXamlSavedElement("", "Frame", listOf(WinUIXamlVisualProperty("NavigationState", value.route)))
            is Control -> WinUIXamlSavedElement("", "Control", listOf(WinUIXamlVisualProperty("Text", value.text)))
            else -> null
        } }, { value, saved, _ -> when (value) {
            is Frame -> { value.route = saved.values.single().value; input = Control("freshly navigated") }
            is Control -> value.text = saved.values.single().value
        } }, discover = { mapOf("\$id/input" to input, "\$id/frame" to frame) })
        val registry = WinUIXamlHotReloadRegistry({ { task -> task(); true } }, { _, value -> value }, stateAdapter = adapter)
        registry.observe(owner, "Page", "Page.xaml", "a".repeat(64), null, null)
        var state: List<WinUIXamlSavedElement>? = null
        registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml")) { state = it.savedState }
        frame.route = "page1"; input.text = "reset"
        registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml", restore = assertNotNull(state))) {
            assertEquals(WinUIXamlHotReloadProtocol.APPLIED, it.status)
        }
        assertEquals("page2", frame.route); assertEquals("saved input", input.text)
    }

}
