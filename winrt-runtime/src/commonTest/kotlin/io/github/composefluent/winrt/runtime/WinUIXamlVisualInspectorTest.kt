package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinUIXamlVisualInspectorTest {
    @Test fun canvas_capture_does_not_read_properties_until_requested() {
        // Inspector tooling contract: SDK rendering/visual-tree ownership stays in
        // the generated adapter; ICustomProperty getters run only on demand.
        var reads = 0
        val inspector = WinUIXamlVisualInspector({ it }, { emptyList() }, { _, _ ->
            WinUIXamlVisualNode(emptyList(), "Grid", "Root", WinUIXamlVisualBounds(0.0, 0.0, 1.0, 1.0))
        }, { reads++; listOf(WinUIXamlVisualProperty("Text", "current")) }, { _, _, done ->
            done(Result.success(WinUIXamlVisualImage(1, 1, ByteArray(4))))
        }, { _, _ -> })
        val request = WinUIXamlInspectionRequest("probe.Page", "", readProperties = false)
        assertEquals(request, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeInspectionRequest("token", request)).inspection)
        val owner = Any()
        var snapshot: WinUIXamlVisualSnapshot? = null
        inspector.inspect(owner, request) { snapshot = it.getOrThrow() }
        assertEquals(0, reads)
        assertNotNull(snapshot!!.image)
        assertEquals(1, snapshot!!.nodes.size)
        assertTrue(snapshot!!.properties.isEmpty())
        inspector.inspect(owner, request.copy(capture = false, readProperties = true)) { snapshot = it.getOrThrow() }
        assertEquals(1, reads)
        assertEquals("current", snapshot!!.properties.single().value)
        assertNull(snapshot!!.image)
    }
    @Test fun first_bitmap_and_selected_properties_skip_unrelated_visuals_and_template_getters() {
        // CsWinRT's generated ICustomProperty.GetValue remains demand-driven;
        // SDK visual traversal belongs to the adapter and is unnecessary to paint.
        val target = Visual("Target")
        val branch = Visual("Branch", listOf(target))
        val root = Visual("Root", listOf(Visual("Unrelated"), branch))
        val traversed = mutableListOf<String>()
        var descriptions = 0
        var reads = 0
        val inspector = WinUIXamlVisualInspector({ it }, {
            val visual = it as Visual
            check(visual === root || visual === branch) { "Unrelated subtrees must not be walked" }
            traversed += visual.name
            visual.children
        }, { value, _ ->
            assertTrue(value === root || value === target); descriptions++
            WinUIXamlVisualNode(emptyList(), "Grid", (value as Visual).name, WinUIXamlVisualBounds(0.0, 0.0, 800.0, 600.0))
        }, { value ->
            assertSame(target, value); reads++
            listOf(WinUIXamlVisualProperty("Text", "Selected value"))
        }, { _, _, done -> done(Result.success(WinUIXamlVisualImage(1, 1, ByteArray(4)))) },
            { _, _ -> }, template = { error("Template getters must wait for a tree request") })
        val request = WinUIXamlInspectionRequest("probe.Page", "", readProperties = false, readTree = false)
        assertEquals(request, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeInspectionRequest("token", request)).inspection)
        var result: Result<WinUIXamlVisualSnapshot>? = null
        inspector.inspect(root, request) { result = it }
        assertNotNull(result!!.getOrThrow().image)
        assertEquals(1, result!!.getOrThrow().nodes.size)
        assertEquals(1, descriptions)
        assertEquals(0, reads)
        assertTrue(traversed.isEmpty())
        inspector.inspect(root, request.copy(capture = false, selectedPath = listOf(1, 0), readProperties = true)) { result = it }
        assertEquals(listOf("Root", "Branch"), traversed)
        assertEquals(1, reads)
        assertEquals("Selected value", result!!.getOrThrow().properties.single().value)
        assertEquals("Target", result!!.getOrThrow().nodes.single { it.path == listOf(1, 0) }.name)
        assertNull(result!!.getOrThrow().image)
        inspector.inspect(root, request.copy(capture = false, selectedPath = listOf(2), readProperties = true)) { result = it }
        assertTrue(result!!.isFailure)
        inspector.inspect(root, request.copy(capture = false, selectedPath = List(129) { 0 })) { result = it }
        assertTrue(result!!.isFailure)
    }
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
        // The IDE transport is a development extension, not a CsWinRT ABI.
        // Preserve its existing frame contract across the shared JVM/Native writer.
        val frame = WinUIXamlHotReloadCodec.writeReply(reply)
        assertEquals(frame.size - 12, WinUIXamlHotReloadCodec.frameSize(frame.copyOfRange(0, 12)))
        assertFailsWith<IllegalArgumentException> { WinUIXamlHotReloadCodec.readReply(frame.copyOf(frame.size - 1)) }
        val decoded = WinUIXamlHotReloadCodec.readReply(frame).inspection!!.image!!
        assertEquals(1600, decoded.width)
        assertContentEquals(pixels, decoded.pixels)
    }
    @Test fun template_clock_targets_one_realized_identity_and_rejects_recycled_slots() {
        // CsWinRT WindowsRuntimeObject identity is separate from template source identity.
        val first = Any(); val second = Any(); val root = Any()
        val children = mutableListOf(first, second)
        val animated = mutableListOf<Any>()
        val inspector = WinUIXamlVisualInspector({ it }, { if (it === root) children else emptyList() }, { value, _ ->
            WinUIXamlVisualNode(emptyList(), "Grid", "Repeated", WinUIXamlVisualBounds(0.0, 0.0, 1.0, 1.0),
                sourceId = if (value === root) "root" else "shared-template")
        }, { emptyList() }, { _, _, done -> done(Result.success(WinUIXamlVisualImage(1, 1, ByteArray(4)))) },
            { _, _ -> }, animate = { value, _ -> animated += value })
        val request = WinUIXamlInspectionRequest(WinUIXamlHotReloadProtocol.PREVIEW_CLASS, "", capture = false, readProperties = false)
        var result: Result<WinUIXamlVisualSnapshot>? = null
        inspector.inspect(root, request) { result = it }
        val command = WinUIXamlPreviewAnimation("", "Fade", 1000, instancePath = listOf(1), sourceId = "shared-template",
            instanceTreeToken = result!!.getOrThrow().treeToken)
        val seek = request.copy(readTree = false, animation = command)
        assertEquals(seek, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeInspectionRequest("token", seek)).inspection)
        inspector.inspect(root, seek) { result = it }
        assertTrue(result!!.isSuccess); assertSame(second, animated.single())
        children.reverse()
        inspector.inspect(root, seek) { result = it }
        assertTrue(result!!.isFailure); assertEquals(1, animated.size)
        children.reverse(); children[1] = Any()
        inspector.inspect(root, seek) { result = it }
        assertTrue(result!!.isFailure); assertEquals(1, animated.size)
        inspector.inspect(root, seek.copy(animation = command.copy(sourceId = "wrong"))) { result = it }
        assertTrue(result!!.isFailure)
        inspector.inspect(root, request) { result = it }
        inspector.inspect(root, seek) { result = it }
        assertTrue(result!!.isFailure)
        inspector.inspect(root, seek.copy(previewMarkup = "<Grid/>")) { result = it }
        assertTrue(result!!.isFailure); assertEquals(1, animated.size)
        assertFailsWith<IllegalArgumentException> { command.copy(instanceTreeToken = 0).validate() }
        assertFailsWith<IllegalArgumentException> { command.copy(instancePath = listOf(-1)).validate() }
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
    @Test fun large_trees_are_paged_from_one_immutable_generation_and_popup_paths_select_the_overlay() {
        val root = Visual("Root", (0 until 5000).map { Visual("Item$it") })
        val popup = Visual("Popup", listOf(Visual("Popup input")))
        var walks = 0
        val inspector = WinUIXamlVisualInspector({ it }, { walks++; (it as Visual).children }, { value, _ ->
            WinUIXamlVisualNode(emptyList(), "Grid", (value as Visual).name, WinUIXamlVisualBounds(0.0, 0.0, 100.0, 100.0))
        }, { listOf(WinUIXamlVisualProperty("Name", (it as Visual).name)) }, { _, _, done -> done(Result.success(WinUIXamlVisualImage(1, 1, ByteArray(4)))) },
            { _, _ -> }, overlays = { listOf(popup) })
        var result: Result<WinUIXamlVisualSnapshot>? = null
        val request = WinUIXamlInspectionRequest("probe.Page", "", capture = false, readProperties = false)
        inspector.inspect(root, request) { result = it }
        val first = result!!.getOrThrow()
        assertEquals(2048, first.nodes.size)
        assertEquals(5004, first.totalNodes)
        val all = first.nodes.toMutableList()
        var next = first.nextOffset
        val initialWalks = walks
        while (next != 0) {
            val pageRequest = request.copy(treeToken = first.treeToken, treeOffset = next)
            assertEquals(pageRequest, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeInspectionRequest("token", pageRequest)).inspection)
            inspector.inspect(root, pageRequest) { result = it }
            val page = result!!.getOrThrow()
            val decoded = WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(WinUIXamlHotReloadReply(0, "", inspection = page))).inspection!!
            assertEquals(first.treeToken, decoded.treeToken)
            all += decoded.nodes
            next = page.nextOffset
        }
        assertEquals(initialWalks, walks)
        assertEquals(5004, all.size)
        val popupNode = all.last()
        assertEquals(1, popupNode.layer)
        assertEquals(listOf(65536, 0, 0), popupNode.path)
        inspector.inspect(root, request.copy(selectedPath = popupNode.path, readProperties = true, readTree = false)) { result = it }
        assertEquals("Popup input", result!!.getOrThrow().properties.single().value)
        inspector.inspect(Visual("Other"), request.copy(treeToken = first.treeToken, treeOffset = 2048)) { result = it }
        assertTrue(result!!.isFailure)
    }
    @Test fun viewport_pixels_roundtrip_and_popup_alpha_blends_at_the_logical_offset() {
        val region = WinUIXamlVisualBounds(5000.0, 6000.0, 2.0, 1.0)
        val request = WinUIXamlInspectionRequest("probe.Page", "", width = 12000, height = 10000, captureBounds = region)
        assertEquals(request, WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeInspectionRequest("token", request)).inspection)
        val base = WinUIXamlVisualImage(2, 1, byteArrayOf(0, 0, 0, -1, 0, 0, 0, -1), region)
        val layer = WinUIXamlVisualImage(1, 1, byteArrayOf(0, 0, -128, -128))
        compositeWinUIXamlOverlay(base, region, layer, WinUIXamlVisualBounds(5001.0, 6000.0, 1.0, 1.0))
        assertContentEquals(byteArrayOf(0, 0, 0, -1, 0, 0, -128, -1), base.pixels)
        val roundtrip = WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(WinUIXamlHotReloadReply(0, "", inspection = WinUIXamlVisualSnapshot(emptyList(), emptyList(), base)))).inspection!!.image!!
        assertEquals(region, roundtrip.bounds)
        assertContentEquals(base.pixels, roundtrip.pixels)
        assertFailsWith<IllegalArgumentException> { validateWinUIXamlCaptureBounds(region.copy(width = Double.NaN)) }
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
    @Test fun inspection_formats_sdk_values_and_isolates_getter_or_formatter_failures() {
        // Generated SDK adaptation supplies CsWinRT-compatible value display;
        // ICustomProperty access, per-property errors and size bounds remain common.
        try {
            WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(
                PropertyControl::class, "probe.PropertyControl", "System.Object", members = listOf(
                    WinUIXamlMemberDefinition("Formatted", "String", String::class, get = { "raw" }),
                    WinUIXamlMemberDefinition("GetterFailure", "String", String::class, get = { error("getter failed") }),
                    WinUIXamlMemberDefinition("FormatFailure", "String", String::class, get = { "bad" }),
                    WinUIXamlMemberDefinition("Bounded", "String", String::class, get = { "long" }),
                )))
            val values = winUIXamlInspectionProperties(PropertyControl()) {
                when (it) { "raw" -> "1,2,3,4"; "bad" -> error("format failed"); else -> "x".repeat(5000) }
            }.associate { it.name to it.value }
            assertEquals("1,2,3,4", values["Formatted"])
            assertTrue(values.getValue("GetterFailure").contains("getter failed"))
            assertTrue(values.getValue("FormatFailure").contains("format failed"))
            assertEquals(4096, values.getValue("Bounded").length)
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
    @Test fun preferred_inherited_properties_stay_inside_the_getter_budget() {
        // CsWinRT ICustomProperty lookup still resolves the declaring owner.
        // Designer ordering must not invoke getters beyond the bounded selection.
        try {
            var reads = 0
            WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(
                PropertyBase::class, "probe.PropertyBase", baseName = "System.Object",
                members = listOf(WinUIXamlMemberDefinition("VerticalAlignment", "String", String::class,
                    get = { reads++; "Stretch" }))))
            WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(
                PropertyControl::class, "probe.PropertyControl", baseName = "probe.PropertyBase", baseType = PropertyBase::class,
                members = (0 until 80).map { index -> WinUIXamlMemberDefinition("Own$index", "String", String::class,
                    get = { reads++; "own" }) }))
            val values = winUIXamlInspectionProperties(PropertyControl(), listOf("Missing", "VerticalAlignment"))
            assertEquals("VerticalAlignment", values.first().name)
            assertEquals("Stretch", values.first().value)
            assertEquals("Own0", values[1].name)
            assertEquals(64, values.size)
            assertEquals(64, reads)
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
