package io.github.composefluent.winrt.gallery

/** Navigation declarations mirror compose-fluent-ui's Component / ComponentGroup. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class GalleryGroupEntry(
    val route: String,
    val title: String,
    val glyph: String,
    val order: Int,
)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class GalleryPage(
    val route: String,
    val title: String,
    val group: String,
    val order: Int,
    val glyph: String = "",
)

/** Marks one sample control factory as the source shown in its page's CodeView. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class GallerySample(val route: String, val title: String)
