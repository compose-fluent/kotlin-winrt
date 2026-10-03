package io.github.composefluent.winrt.gallery

/** App-owned route dispatch for samples that link to another Gallery page. */
internal object GalleryNavigationHost {
    var navigate: (String) -> Unit = { error("Gallery navigation has not been initialized") }

    /** The shell and every activation entry point share the generated catalog. */
    fun resolveRoute(value: String): String? {
        val route = value.trim().trim('/')
        return when (route.lowercase()) {
            "", "home" -> GalleryCatalog.home.id
            "all" -> "All"
            "settings" -> "Settings"
            "style", "xamlstyle", "xamlstyles" -> "XamlStyles"
            "tooltip", "tool-tip", "tool tip" -> "ToolTip"
            else -> if (route.startsWith("Search:", ignoreCase = true)) {
                "Search:${route.substringAfter(':')}"
            } else GalleryCatalog.pages.firstOrNull {
                it.id.equals(route, ignoreCase = true) || it.title.equals(route, ignoreCase = true)
            }?.id ?: GalleryCatalog.groups.firstOrNull {
                it.id.equals(route, ignoreCase = true) || it.title.equals(route, ignoreCase = true)
            }?.id
        }
    }
}
