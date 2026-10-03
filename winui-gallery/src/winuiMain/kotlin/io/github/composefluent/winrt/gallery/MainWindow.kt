package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.asWinRT

import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.imaging.BitmapImage
import microsoft.ui.xaml.input.KeyboardAccelerator
import windows.foundation.Uri
import windows.system.VirtualKey
import windows.system.VirtualKeyModifiers

/** XAML shell with the existing WindowEx hosting and Kotlin navigation contract. */
internal class MainWindow : winui3package.WindowEx() {
    val root: Grid get() = RootGrid
    private val tasks by lazy { GalleryPageTasks(root) }
    private val navigation: NavigationView get() = NavigationViewControl
    private val search: AutoSuggestBox get() = controlsSearchBox
    private val host: Frame get() = rootFrame
    private val history = mutableListOf<String>()
    private val focusTargets = mutableMapOf<String, String>()
    private val pageIds = GalleryCatalog.pages.mapTo(mutableSetOf()) { it.id }
    private val recent = GalleryPreferences.routes("Recent").filterTo(mutableListOf()) { it in pageIds }
    private val favorites = GalleryPreferences.routes("Favorites").filterTo(mutableSetOf()) { it in pageIds }
    private val menuItems = mutableMapOf<String, NavigationViewItem>()
    private var current = "Home"
    private var selecting = false
    private var theme = when (GalleryPreferences.text("Theme")) {
        "Light" -> ElementTheme.Light
        "Dark" -> ElementTheme.Dark
        else -> ElementTheme.Default
    }

    override fun initializeComponent() {
        super.initializeComponent()
        GalleryWindows.track(checkNotNull(window))
        title = "Kotlin WinUI Gallery"
        systemBackdrop = winui3package.TenMicaBackdrop().apply { bindThemeTo = root }
        contextMenu = winui3package.ModernStandardWindowContextMenu()
        GalleryNavigationHost.navigate = ::navigate
        root.requestedTheme = theme
        GalleryTheme.attach(root, checkNotNull(window))
        navigation.paneDisplayMode = if (GalleryPreferences.flag("TopNavigation")) NavigationViewPaneDisplayMode.Top else NavigationViewPaneDisplayMode.Auto
        ElementSoundPlayer.state = if (GalleryPreferences.flag("Sound")) ElementSoundPlayerState.On else ElementSoundPlayerState.Off
        ElementSoundPlayer.spatialAudioMode = if (GalleryPreferences.flag("SpatialAudio")) ElementSpatialAudioMode.On else ElementSpatialAudioMode.Off
        titleBar.isPaneToggleButtonVisible = !GalleryPreferences.flag("TopNavigation")
        GalleryCatalog.home.let { home ->
            navigation.menuItems.add(menu(home.id, home.title, home.glyph.ifBlank { null }))
        }
        var controlsHeaderAdded = false
        GalleryCatalog.groups.forEach { group ->
            if (!group.isSpecialSection && !controlsHeaderAdded) {
                navigation.menuItems.add(NavigationViewItemHeader().apply { content = "Controls" })
                navigation.menuItems.add(menu("All", "All", "\uE8A9"))
                controlsHeaderAdded = true
            }
            val parent = menu(group.id, group.title, group.glyph, expandable = true)
            navigation.menuItems.add(parent)
            group.pages.forEach { page ->
                parent.menuItems.add(menu(page.id, page.title, page.glyph.ifBlank { null }))
            }
        }
        search.maxWidth = 580.0
        search.horizontalAlignment = HorizontalAlignment.Stretch
        search.keyboardAccelerators.firstOrNull()?.invoked?.add { _,args -> search.focus(FocusState.Programmatic); args.handled = true }
        navigation.selectionChanged.add { _, args ->
            if (!selecting) {
                if (args.isSettingsSelected) navigate("Settings")
                else {
                    // NavigationView reports the selected data item and its realized
                    // container separately.  The container is the stable source of the
                    // route tag when nested group items are selected.
                    navigationRoute(args.selectedItemContainer, args.selectedItem)?.let(::navigate)
                }
            }
        }
        // ItemInvoked is raised for nested NavigationViewItem entries even when
        // SelectionChanged only exposes the parent container.  Resolve the route
        // from the invoked container first so every sample leaf is actionable.
        navigation.itemInvoked.add { _, args ->
            if (!selecting) navigationRoute(args.invokedItemContainer, args.invokedItem)?.let(::navigate)
        }
        titleBar.paneToggleRequested.add { _, _ -> navigation.isPaneOpen = !navigation.isPaneOpen }
        titleBar.backRequested.add { _, _ -> if (history.isNotEmpty()) show(history.removeAt(history.lastIndex)) }
        // The accelerator belongs to the page-wide Grid for key routing, but
        // its default placement would show an Alt+Left tooltip on empty content.
        root.keyboardAcceleratorPlacementMode = microsoft.ui.xaml.input.KeyboardAcceleratorPlacementMode.Hidden
        root.keyboardAccelerators.add(KeyboardAccelerator().apply {
            key = VirtualKey.Left; modifiers = VirtualKeyModifiers.Menu
            invoked.add { _, args -> if (history.isNotEmpty()) { show(history.removeAt(history.lastIndex)); args.handled = true } }
        })
        root.pointerPressed.add { _, args ->
            if (args.getCurrentPoint(root).properties?.isXButton1Pressed == true && history.isNotEmpty()) {
                show(history.removeAt(history.lastIndex)); args.handled = true
            }
        }
        search.textChanged.add { _, args ->
            if (args.reason != AutoSuggestionBoxTextChangeReason.UserInput) return@add
            val suggestions = results(search.text).take(12).map { it.title }
            search.itemsSource = suggestions.ifEmpty { listOf("No results found") }
        }
        search.querySubmitted.add { _, args ->
            val page = GalleryCatalog.pages.firstOrNull { it.title == args.chosenSuggestion?.toString() }
            navigate(page?.id ?: "Search:${args.queryText}")
        }
        extendsContentIntoTitleBar = true
        setTitleBar(titleBar)
        fun captionTheme() {
            val dark = root.actualTheme == ElementTheme.Dark
            appWindow?.titleBar?.apply {
                buttonForegroundColor = rgb(if (dark) 0xFFFFFFu else 0x000000u)
                buttonBackgroundColor = windows.ui.Color(0u, 0u, 0u, 0u)
                buttonInactiveBackgroundColor = windows.ui.Color(0u, 0u, 0u, 0u)
            }
        }
        root.actualThemeChanged.add { _, _ -> captionTheme() }
        root.loaded.add { _, _ -> captionTheme(); updateJumpList() }
        show("Home")
    }

    private fun menu(id: String, title: String, icon: String? = null, expandable: Boolean = false) = NavigationViewItem().apply {
        tag = id
        selectsOnInvoked = true
        content = title
        icon?.let { this.icon = glyph(it) }
        named(this, id)
        // Keep route activation on the item itself as a fallback for projected
        // NavigationView event args that expose only the item's content string.
        tapped.add { _, args ->
            if (!selecting) navigate(id)
            if (!expandable) args.handled = true
        }
        this@MainWindow.menuItems[id] = this
    }

    private fun navigationRoute(container: NavigationViewItemBase?, value: Any?): String? {
        fun routeOf(candidate: Any?): String? {
            val element = runCatching { candidate?.asWinRT<FrameworkElement>() }.getOrNull() ?: return null
            val tag = element.tag?.toString()?.takeIf { it.isNotBlank() }
            if (tag != null && menuItems.containsKey(tag)) return tag
            val content = runCatching { element.asWinRT<ContentControl>().content }.getOrNull()
            val contentText = content?.toString()?.takeIf { it.isNotBlank() }
            return menuItems.entries.firstOrNull { it.value.content?.toString() == contentText }?.key
        }

        fun routeFromText(candidate: Any?): String? {
            val text = candidate?.toString()?.takeIf { it.isNotBlank() } ?: return null
            return menuItems.entries.firstOrNull { it.value.content?.toString() == text }?.key
        }

        // For nested entries WinUI can report the leaf's content string while
        // SelectedItemContainer still points at its group. Resolve the leaf
        // string before falling back to the realized parent container.
        val valueRoute = routeOf(value) ?: routeFromText(value)
        val containerRoute = routeOf(container) ?: routeFromText(container)
        if (valueRoute != null) return valueRoute
        if (containerRoute != null) return containerRoute
        return routeFromText(value)
    }

    private fun updateJumpList() {
        tasks.launch {
            // Shell integration is best effort, as in WinUI Gallery's JumpListHelper.
            try { updateGalleryJumpList() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { println("Kotlin WinUI Gallery: JumpList update failed: ${error.message}") }
        }
    }

    private fun results(query: String) = GalleryCatalog.pages.filter { page ->
        query.isNotBlank() && (page.title.contains(query.trim(), true) || page.tags.any { it.contains(query.trim(), true) })
    }.sortedWith(compareBy({ !it.title.startsWith(query.trim(), true) }, { it.title }))

    private fun updateNavigationSelection(route: String) {
        if (route.startsWith("Search:")) return
        val page = GalleryCatalog.pages.firstOrNull { it.id == route }
        val groupItem = page?.group?.let(menuItems::get)
        val selectedItem: Any? = when {
            route == "Settings" -> navigation.settingsItem
            page != null && navigation.paneDisplayMode == NavigationViewPaneDisplayMode.Top -> groupItem
            else -> menuItems[route]
        }
        if (selectedItem == null) return

        selecting = true
        try {
            if (page != null) {
                groupItem?.isExpanded = true
                if (navigation.paneDisplayMode != NavigationViewPaneDisplayMode.Top) navigation.updateLayout()
            }
            navigation.selectedItem = selectedItem
            if (route != "Settings") {
                val item = selectedItem.asWinRT<NavigationViewItem>()
                item.isSelected = true
                item.startBringIntoView()
            }
        } finally {
            selecting = false
        }
    }

    private fun navigate(route: String) {
        val canonical = GalleryNavigationHost.resolveRoute(route) ?: route.trim().trim('/')
        if (canonical == current) {
            updateNavigationSelection(canonical)
            return
        }
        val previous = current
        try {
            show(canonical)
            if (GalleryCatalog.pages.any { it.id == canonical }) focusTargets[previous] = canonical
            history += previous
            titleBar.isBackButtonVisible = history.isNotEmpty()
        } catch (error: Throwable) {
            println("Kotlin WinUI Gallery: navigation failed for $canonical\n${error.stackTraceToString()}")
            current = previous
        }
    }

    private fun show(route: String) {
        val content = when {
            route == "Home" -> io.github.composefluent.winrt.gallery.pages.HomePage()
            route == "All" -> index("All controls", GalleryCatalog.pages.sortedBy { it.title })
            route == "Settings" -> settings()
            route.startsWith("Search:") -> index("Search results for \"${route.removePrefix("Search:")}\"", results(route.removePrefix("Search:")))
            else -> GalleryCatalog.groups.firstOrNull { it.id == route }?.let { index(it.title, it.pages) }
                ?: GalleryCatalog.pages.firstOrNull { it.id == route }?.let { page ->
                    detail(page)
                }
                ?: error("Unknown Gallery route: $route")
        }
        (content as? io.github.composefluent.winrt.gallery.pages.ItemsPageBase)?.FocusItemId = focusTargets[route]
        current = route
        titleBar.isBackButtonVisible = history.isNotEmpty()
        // The projected Frame navigation overload requires a generated Page
        // type and can leave the old Page visible when the parameter is a
        // code-only UIElement.  Assigning the projected content directly keeps
        // the same single host while making every sample route deterministic.
        host.content = content
        updateNavigationSelection(route)
    }

    private fun index(title: String,pages: List<GalleryPageInfo>): UIElement = io.github.composefluent.winrt.gallery.pages.AllControlsPage(title,pages)

    private fun detail(page: GalleryPageInfo): UIElement {
        recent.remove(page.id)
        recent.add(0, page.id)
        while (recent.size > 20) recent.removeAt(recent.lastIndex)
        GalleryPreferences.putRoutes("Recent", recent)
        updateJumpList()
        return io.github.composefluent.winrt.gallery.pages.ItemPage(page) { selected ->
            if (selected) favorites.add(page.id) else favorites.remove(page.id)
            GalleryPreferences.putRoutes("Favorites",favorites)
            updateJumpList()
        }
    }

    private fun settings(): UIElement = io.github.composefluent.winrt.gallery.pages.SettingsPage(root, theme,
        setTheme = {
            theme = it; GalleryPreferences.put("Theme", it.toString()); root.requestedTheme = it
        },
        setTopNavigation = {
            navigation.paneDisplayMode = if (it) NavigationViewPaneDisplayMode.Top else NavigationViewPaneDisplayMode.Auto
            titleBar.isPaneToggleButtonVisible = !it
            GalleryPreferences.putFlag("TopNavigation", it)
        },
        clearRecents = { recent.clear(); GalleryPreferences.putRoutes("Recent", recent); updateJumpList() },
        clearFavorites = { favorites.clear(); GalleryPreferences.putRoutes("Favorites", favorites); updateJumpList() },
    )

}
