package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinUiXamlVisualInspectorTest {
    private class Visual(val name: String, val children: List<Visual> = emptyList())
    private open class PropertyBase
    private class PropertyControl : PropertyBase()
    @Test fun control_properties_are_kept_before_the_inherited_inspection_limit() {
        // CsWinRT's source-generated ICustomProperty ownership remains on the
        // declaring type; inspect the derived control before its base UI surface.
        try {
            WinUiAuthoredTypeMetadata.registerPropertyAccessors(WinUiXamlTypeDefinition(
                PropertyBase::class, "probe.PropertyBase", baseName = "System.Object", members = (0 until 80).map { index ->
                    WinUiXamlMemberDefinition("Base$index", "String", String::class, get = { "base" })
                }))
            WinUiAuthoredTypeMetadata.registerPropertyAccessors(WinUiXamlTypeDefinition(
                PropertyControl::class, "probe.PropertyControl", baseName = "probe.PropertyBase", baseType = PropertyBase::class,
                members = listOf(WinUiXamlMemberDefinition("Text", "String", String::class, get = { "selected control" }))))
            val properties = winUiXamlInspectionProperties(PropertyControl())
            assertEquals("Text", properties.first().name)
            assertEquals("selected control", properties.first().value)
            assertEquals(64, properties.size)
        } finally { WinUiAuthoredTypeMetadata.clearForTests() }
    }
    @Test fun actual_visual_paths_are_inspected_on_the_owning_dispatcher_without_changing_hot_reload_version() {
        // CsWinRT generated accessor/runtime boundary; WinUI VisualTreeHelper owns the adapter's children.
        val leaf = Visual("TemplateChild"); val root = Visual("Root", listOf(Visual("Control", listOf(leaf))))
        val queue = mutableListOf<() -> Unit>()
        val inspector = WinUiXamlVisualInspector({ it }, { (it as Visual).children }, { value, _ ->
            WinUiXamlVisualNode(emptyList(), "probe.Visual", (value as Visual).name, WinUiXamlVisualBounds(0.0, 0.0, 100.0, 80.0))
        }, { listOf(WinUiXamlVisualProperty("Name", (it as Visual).name)) }, { _, complete ->
            complete(Result.success(WinUiXamlVisualImage(1, 1, byteArrayOf(0, 0, -1, -1))))
        }, { _, _ -> error("Live inspection never invokes static preview") })
        val registry = WinUiXamlHotReloadRegistry({ { action -> queue += action; true } }, { _, value -> value }, inspector = inspector)
        registry.observe(root, "probe.Page", "Page.xaml", "a".repeat(64), null, null)
        var result: WinUiXamlHotReloadReply? = null
        registry.inspect(WinUiXamlInspectionRequest("probe.Page", "Page.xaml", selectedPath = listOf(0, 0))) { result = it }
        assertNull(result)
        queue.removeAt(0).invoke()
        assertEquals(WinUiXamlHotReloadProtocol.APPLIED, result!!.status)
        assertEquals(listOf(emptyList(), listOf(0), listOf(0, 0)), result!!.inspection!!.nodes.map { it.path })
        assertEquals("TemplateChild", result!!.inspection!!.properties.single().value)
        assertContentEquals(byteArrayOf(0, 0, -1, -1), result!!.inspection!!.image!!.pixels)
        assertEquals(0L, registry.snapshot().roots.single().version)
        registry.inspect(WinUiXamlInspectionRequest("probe.Page", "Page.xaml", previewMarkup = "<Grid/>")) { result = it }
        assertEquals(WinUiXamlHotReloadProtocol.REJECTED, result!!.status)
        assertTrue(queue.isEmpty())
        registry.close()
    }
    @Test fun removed_visuals_fail_locally() {
        val root = Visual("Root")
        val inspector = WinUiXamlVisualInspector({ it }, { emptyList() }, { _, _ ->
            WinUiXamlVisualNode(emptyList(), "probe.Visual", "", WinUiXamlVisualBounds(0.0, 0.0, 0.0, 0.0))
        }, { emptyList() }, { _, _ -> error("No capture requested") }, { _, _ -> })
        var result: Result<WinUiXamlVisualSnapshot>? = null
        inspector.inspect(root, WinUiXamlInspectionRequest("probe.Page", "", selectedPath = listOf(0), capture = false)) { result = it }
        assertTrue(result!!.isFailure)
        inspector.inspect(root, WinUiXamlInspectionRequest("probe.Page", "", capture = false)) { result = it }
        assertEquals(1, result!!.getOrThrow().nodes.size)
    }
}
