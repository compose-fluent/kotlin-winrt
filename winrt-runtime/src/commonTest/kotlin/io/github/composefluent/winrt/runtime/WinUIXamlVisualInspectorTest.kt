package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinUIXamlVisualInspectorTest {
    @Test fun capture_density_preserves_layout_and_bounds_physical_memory() {
        assertEquals(800 to 600, winUIXamlCaptureSize(800.0, 600.0, 1.0, 1.0))
        assertEquals(1600 to 1200, winUIXamlCaptureSize(800.0, 600.0, 1.0, 2.0))
        assertEquals(800 to 600, winUIXamlCaptureSize(800.0, 600.0, 2.0, 2.0))
        assertEquals(2048 to 1536, winUIXamlCaptureSize(800.0, 600.0, 2.0, 32.0))
        assertFailsWith<IllegalArgumentException> { winUIXamlCaptureSize(800.0, 600.0, 1.0, Double.NaN) }
        val request = WinUIXamlInspectionRequest("probe.Page", "", renderScale = 2.5)
        assertEquals(request, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeInspectionRequest("token", request)).inspection)
        assertFailsWith<IllegalArgumentException> { WinUIXamlHotReloadCodec.writeInspectionRequest("token", request.copy(renderScale = Double.POSITIVE_INFINITY)) }
        val pixels = ByteArray(1600 * 1200 * 4) { (it % 251).toByte() }
        val reply = WinUIXamlHotReloadReply(0, "", inspection = WinUIXamlVisualSnapshot(emptyList(), emptyList(), WinUIXamlVisualImage(1600, 1200, pixels)))
        val decoded = WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(reply)).inspection!!.image!!
        assertEquals(1600, decoded.width)
        assertContentEquals(pixels, decoded.pixels)
    }
    @Test fun storyboard_commands_are_bounded_and_confined_to_preview_before_capture() {
        // SDK Storyboard.Begin/Pause/SeekAlignedToLastTick stays in generated adaptation;
        // common runtime only validates and dispatches, on JVM and mingwX64.
        val animation = WinUIXamlPreviewAnimation("Panel", "Motion", 1500)
        val request = WinUIXamlInspectionRequest(WinUIXamlHotReloadProtocol.PREVIEW_CLASS, "", animation = animation)
        assertEquals(request, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeInspectionRequest("token", request)).inspection)
        assertFailsWith<IllegalArgumentException> { animation.copy(positionMillis = -1).validate() }
        assertFailsWith<IllegalArgumentException> { animation.copy(action = "Execute").validate() }
        val calls = mutableListOf<String>()
        val inspector = WinUIXamlVisualInspector({ it }, { emptyList() }, { _, _ ->
            WinUIXamlVisualNode(emptyList(), "Grid", "", WinUIXamlVisualBounds(0.0, 0.0, 1.0, 1.0))
        }, { emptyList() }, { _, _, done -> calls += "capture"; done(Result.success(WinUIXamlVisualImage(1, 1, ByteArray(4)))) },
            { _, _ -> calls += "preview" }, clock = { WinUIXamlPreviewClock(1500, false) }, animate = { _, command -> assertEquals(animation, command); calls += "seek" })
        var result: Result<WinUIXamlVisualSnapshot>? = null
        inspector.inspect(Any(), request.copy(previewMarkup = "<Grid/>")) { result = it }
        assertTrue(result!!.isSuccess)
        assertEquals(WinUIXamlPreviewClock(1500, false), result!!.getOrThrow().clock)
        val reply = WinUIXamlHotReloadReply(0, "", inspection = result!!.getOrThrow())
        assertEquals(reply.inspection!!.clock, WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(reply)).inspection!!.clock)
        assertEquals(listOf("preview", "seek", "capture"), calls)
        calls.clear()
        inspector.inspect(Any(), request.copy(className = "live.Page")) { result = it }
        assertTrue(result!!.isFailure)
        assertTrue(calls.isEmpty())
    }
    private class Visual(val name: String, val children: List<Visual> = emptyList())
    private open class PropertyBase
    private class PropertyControl : PropertyBase()
    @Test fun inspection_preserves_exact_registered_dependency_type_identity() {
        // CsWinRT WindowsRuntimeComWrappers uses exact managed type metadata;
        // generated XamlUserType supplies the qualified name for dependency controls.
        try {
            WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(
                PropertyBase::class, "Microsoft.UI.Xaml.Controls.UserControl", "System.Object"))
            WinUIAuthoredTypeMetadata.registerDefinition(WinUIXamlTypeDefinition(
                PropertyControl::class, "dependency.controls.GreetingControl",
                "Microsoft.UI.Xaml.Controls.UserControl", baseType = PropertyBase::class))
            assertEquals("dependency.controls.GreetingControl", winUIXamlInspectionTypeName(PropertyControl()))
            assertNull(winUIXamlInspectionTypeName(PropertyBase()))
            assertNull(winUIXamlInspectionTypeName(Any()))
        } finally { WinUIAuthoredTypeMetadata.clearForTests() }
    }
    @Test fun control_properties_are_kept_before_the_inherited_inspection_limit() {
        // CsWinRT's source-generated ICustomProperty ownership remains on the
        // declaring type; inspect the derived control before its base UI surface.
        try {
            WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(
                PropertyBase::class, "probe.PropertyBase", baseName = "System.Object", members = (0 until 80).map { index ->
                    WinUIXamlMemberDefinition("Base$index", "String", String::class, get = { "base" })
                }))
            WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(
                PropertyControl::class, "probe.PropertyControl", baseName = "probe.PropertyBase", baseType = PropertyBase::class,
                members = listOf(WinUIXamlMemberDefinition("Text", "String", String::class, get = { "selected control" }))))
            val properties = winUIXamlInspectionProperties(PropertyControl())
            assertEquals("Text", properties.first().name)
            assertEquals("selected control", properties.first().value)
            assertEquals(64, properties.size)
        } finally { WinUIAuthoredTypeMetadata.clearForTests() }
    }
    @Test fun actual_visual_paths_are_inspected_on_the_owning_dispatcher_without_changing_hot_reload_version() {
        // CsWinRT generated accessor/runtime boundary; WinUI VisualTreeHelper owns the adapter's children.
        val leaf = Visual("TemplateChild"); val root = Visual("Root", listOf(Visual("Control", listOf(leaf))))
        val queue = mutableListOf<() -> Unit>()
        val inspector = WinUIXamlVisualInspector({ it }, { (it as Visual).children }, { value, _ ->
            WinUIXamlVisualNode(emptyList(), "probe.Visual", (value as Visual).name, WinUIXamlVisualBounds(0.0, 0.0, 100.0, 80.0))
        }, { listOf(WinUIXamlVisualProperty("Name", (it as Visual).name)) }, { _, _, complete ->
            complete(Result.success(WinUIXamlVisualImage(1, 1, byteArrayOf(0, 0, -1, -1))))
        }, { _, _ -> error("Live inspection never invokes static preview") })
        val registry = WinUIXamlHotReloadRegistry({ { action -> queue += action; true } }, { _, value -> value }, inspector = inspector)
        registry.observe(root, "probe.Page", "Page.xaml", "a".repeat(64), null, null)
        var result: WinUIXamlHotReloadReply? = null
        registry.inspect(WinUIXamlInspectionRequest("probe.Page", "Page.xaml", selectedPath = listOf(0, 0))) { result = it }
        assertNull(result)
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, result!!.status)
        assertEquals(listOf(emptyList(), listOf(0), listOf(0, 0)), result!!.inspection!!.nodes.map { it.path })
        assertEquals("TemplateChild", result!!.inspection!!.properties.single().value)
        assertContentEquals(byteArrayOf(0, 0, -1, -1), result!!.inspection!!.image!!.pixels)
        assertEquals(0L, registry.snapshot().roots.single().version)
        registry.inspect(WinUIXamlInspectionRequest("probe.Page", "Page.xaml", previewMarkup = "<Grid/>")) { result = it }
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, result!!.status)
        assertTrue(queue.isEmpty())
        registry.close()
    }
    @Test fun repeated_template_realizations_keep_exact_sdk_owner_and_root_paths() {
        // CsWinRT object references own COM identity; the SDK adapter supplies
        // ContentTemplateRoot, common traversal only resolves bounded visual paths.
        val first = Visual("SameName"); val second = Visual("SameName")
        val container1 = Visual("", listOf(first)); val container2 = Visual("", listOf(second))
        val root = Visual("Items", listOf(container1, container2))
        val inspector = WinUIXamlVisualInspector({ it }, { (it as Visual).children }, { value, _ ->
            WinUIXamlVisualNode(emptyList(), "probe.Visual", (value as Visual).name, WinUIXamlVisualBounds(0.0, 0.0, 10.0, 10.0))
        }, { emptyList() }, { _, _, _ -> error("No capture") }, { _, _ -> }, template = { value ->
            when (value) {
                container1 -> WinUIXamlTemplateReference(first, root, "ItemTemplate")
                container2 -> WinUIXamlTemplateReference(second, root, "ItemTemplate")
                else -> null
            }
        })
        var snapshot: WinUIXamlVisualSnapshot? = null
        inspector.inspect(root, WinUIXamlInspectionRequest("probe.Page", "", capture = false)) { snapshot = it.getOrThrow() }
        val realized = snapshot!!.nodes.filter { it.templateOwner != null }
        assertEquals(listOf(listOf(0, 0), listOf(1, 0)), realized.map { it.path })
        assertTrue(realized.all { it.templateOwner == emptyList<Int>() && it.templateProperty == "ItemTemplate" })
        val reply = WinUIXamlHotReloadReply(0, "", inspection = snapshot)
        assertEquals(snapshot, WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(reply)).inspection)
        assertFailsWith<IllegalArgumentException> {
            WinUIXamlHotReloadCodec.writeReply(reply.copy(inspection = snapshot!!.copy(nodes = listOf(realized.first().copy(templateOwner = listOf(1))))))
        }
    }
    @Test fun removed_visuals_fail_locally() {
        val root = Visual("Root")
        val inspector = WinUIXamlVisualInspector({ it }, { emptyList() }, { _, _ ->
            WinUIXamlVisualNode(emptyList(), "probe.Visual", "", WinUIXamlVisualBounds(0.0, 0.0, 0.0, 0.0))
        }, { emptyList() }, { _, _, _ -> error("No capture requested") }, { _, _ -> })
        var result: Result<WinUIXamlVisualSnapshot>? = null
        inspector.inspect(root, WinUIXamlInspectionRequest("probe.Page", "", selectedPath = listOf(0), capture = false)) { result = it }
        assertTrue(result!!.isFailure)
        inspector.inspect(root, WinUIXamlInspectionRequest("probe.Page", "", capture = false)) { result = it }
        assertEquals(1, result!!.getOrThrow().nodes.size)
    }
}
