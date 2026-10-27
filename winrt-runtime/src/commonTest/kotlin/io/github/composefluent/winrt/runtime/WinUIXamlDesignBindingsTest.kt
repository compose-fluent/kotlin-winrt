@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinUIXamlDesignBindingsTest {
    private class Owner : WinUIXamlPageBindingOwner, WinUIXamlComponent {
        override fun _kotlinXamlInitialize() {}
        override fun _kotlinXamlCompleteConstruction() {}
        override fun _kotlinXamlDesignConnections() = "4|signature|0|0|0|0"
        override fun _kotlinXamlConnectDesign(connectionId: Int, target: Any?) { _kotlinXamlConnectBindings(connectionId, target) }
        val targets = mutableMapOf<Int, Any?>()
        var stopped = 0
        var suppressed = ""
        override fun _kotlinXamlConnectBindings(connectionId: Int, target: Any?) { targets[connectionId] = target }
        override fun stopDesignBindings() { stopped++ }
        override fun updateDesignBindings(suppressed: String) { this.suppressed = suppressed }
    }
    @Test fun connects_the_real_typed_owner_and_preserves_design_overrides() {
        // CSharpPagePass2 Connect/Update/StopTracking own the binding lifecycle.
        val owner = Owner()
        val session = WinUIXamlDesignBindingSession("Owner", owner)
        val target = Any()
        session.validateDocument("4:signature")
        assertFailsWith<WinUIXamlDesignRebuildRequired> { session.validateDocument("") }
        session.beginDocument(); session.connect(target, "4:signature:Text,Width", null); session.updatePage()
        assertSame(target, owner.targets[4]); assertEquals("4:Text\n4:Width", owner.suppressed)
        session.beginDocument(); assertEquals(2, owner.stopped)
        session.connect(target, "4:signature:", null); session.updatePage(); assertEquals("", owner.suppressed)
        assertFailsWith<WinUIXamlDesignRebuildRequired> { session.connect(target, "4:changed:", null) }
        assertFailsWith<WinUIXamlDesignRebuildRequired> { session.connect(target, "5:signature:", null) }
    }
    @Test fun design_suppression_does_not_change_normal_binding_ownership() {
        val state = WinUIXamlBindingState()
        assertTrue(state.isDesignBindingEnabled(1, "Text"))
        var removed = 0
        state.initialize { state.trackEvent(Any(), Any()) { _, _ -> removed++ } }
        state.configureDesignBindings("1:Text")
        assertEquals(1, removed)
        assertFalse(state.isDesignBindingEnabled(1, "Text"))
        assertTrue(state.isDesignBindingEnabled(2, "Text"))
        state.configureDesignBindings(""); assertTrue(state.isDesignBindingEnabled(1, "Text"))
    }
    @Test fun constructor_context_trees_are_excluded_from_visible_component_recovery() {
        val registry = WinUIXamlHotReloadRegistry({ { task -> task(); true } }, { _, text -> text })
        val owner = Any(); val hidden = Any(); val visible = Any(); val hash = "a".repeat(64)
        constructWinUIXamlDesignSource {
            registry.observe(hidden, "Control", "Control.xaml", hash, null, null)
            registry.observe(owner, "Window", "Window.xaml", hash, "Child", hidden)
            registry.observe(owner, "Window", "Window.xaml", hash, null, null)
        }
        assertTrue(registry.snapshot().roots.isEmpty())
        registry.promoteDesignSource(owner)
        registry.observe(visible, "Control", "Control.xaml", hash, null, null)
        registry.observe(owner, "Window", "Window.xaml", hash, "Child", visible)
        val controls = registry.snapshot().roots.single { it.className == "Control" }
        assertEquals(1, controls.instances)
        assertTrue(controls.stateKeys.single().isNotEmpty())
    }

}
