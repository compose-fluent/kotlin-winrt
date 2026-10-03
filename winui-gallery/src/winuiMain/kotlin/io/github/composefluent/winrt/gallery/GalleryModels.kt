package io.github.composefluent.winrt.gallery

internal data class GalleryLink(val title: String, val uri: String)
internal data class GalleryGroup(val id: String, val title: String, val glyph: String, val pages: List<GalleryPageInfo>, val isSpecialSection: Boolean = false)
internal data class GalleryPageInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val image: String,
    val group: String,
    val isNew: Boolean,
    val isUpdated: Boolean,
    val tags: List<String>,
    val docs: List<GalleryLink>,
    val related: List<String>,
    val glyph: String = "",
    val apiNamespace: String = "",
    val baseClasses: List<String> = emptyList(),
    val sourcePath: String = "",
    val isExperimental: Boolean = false,
    val repositorySourcePath: String = "",
)
