package io.github.composefluent.winrt.ide.resources

/** Windows SDK UapManifestSchema and UapManifestSchema_v10: optional values
 * exist in the designer without being written into the document until edited. */
internal object WinRTManifestFields {
    const val UAP10 = "${WinRTXmlForms.UAP}/10"
    private val root = WinRTXmlStep(WinRTXmlForms.FOUNDATION, "Package", 0)
    private fun step(name: String, namespace: String = WinRTXmlForms.FOUNDATION) = WinRTXmlStep(namespace, name, 0)
    fun application(index: Int) = listOf(root, step("Applications"), WinRTXmlStep(WinRTXmlForms.FOUNDATION, "Application", index))
    fun visual(index: Int) = application(index) + step("VisualElements", WinRTXmlForms.UAP)
    fun properties(name: String) = listOf(root, step("Properties"), step(name))

    fun fields(applications: Int): List<WinRTXmlField> = buildList {
        listOf("DisplayName", "PublisherDisplayName", "Description", "Logo").forEach { name ->
            add(WinRTXmlField(properties(name), null, name, "", createIfMissing = true))
        }
        add(WinRTXmlField(listOf(root, step("Resources"), step("Resource")), "Language", "Default language", "", createIfMissing = true))
        repeat(applications) { index ->
            fun field(path: List<WinRTXmlStep>, attribute: String, choices: List<String> = emptyList(), namespace: String = "") {
                add(WinRTXmlField(path, attribute, attribute, "", namespace, true, choices))
            }
            val app = application(index)
            field(app, "TrustLevel", listOf("", "appContainer", "mediumIL"), UAP10)
            field(app, "RuntimeBehavior", listOf("", "windowsApp", "packagedClassicApp", "win32App"), UAP10)
            field(app, "ResourceGroup")
            val visual = visual(index)
            listOf("DisplayName", "Description", "BackgroundColor", "Square150x150Logo", "Square44x44Logo").forEach { field(visual, it) }
            field(visual, "AppListEntry", listOf("", "default", "none"))
            val tile = visual + step("DefaultTile", WinRTXmlForms.UAP)
            listOf("ShortName", "Square71x71Logo", "Wide310x150Logo", "Square310x310Logo").forEach { field(tile, it) }
            val update = tile + step("TileUpdate", WinRTXmlForms.UAP)
            field(update, "Recurrence", listOf("", "halfHour", "hour", "sixHours", "twelveHours", "daily"))
            field(update, "UriTemplate")
            val lockScreen = visual + step("LockScreen", WinRTXmlForms.UAP)
            field(lockScreen, "Notification", listOf("", "badge", "badgeAndTileText"))
            field(lockScreen, "BadgeLogo")
            val splash = visual + step("SplashScreen", WinRTXmlForms.UAP)
            field(splash, "Image")
            field(splash, "BackgroundColor")
        }
    }
}
