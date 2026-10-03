package io.github.composefluent.winrt.gallery.processor

import kotlinx.serialization.json.*
import java.nio.file.Path

internal const val galleryPackage = "io.github.composefluent.winrt.gallery"

internal data class Entry(val kind: String, val args: Map<String, String>, val symbol: String, val source: String, val repositoryPath: String = "") {
    fun value(key: String): String = requireNotNull(args[key]) { "$source: @$kind is missing '$key'" }
    val route get() = value("route")
    val order get() = args["order"]?.toIntOrNull() ?: Int.MAX_VALUE
}

internal fun repositoryRelativePath(sourcePath: String, repositoryRoot: String): String {
    val root = Path.of(repositoryRoot).toAbsolutePath().normalize()
    val source = Path.of(sourcePath).toAbsolutePath().normalize()
    require(source.startsWith(root)) { "Gallery page source is outside the repository: $source" }
    return root.relativize(source).toString().replace('\\', '/')
}

internal fun generate(entries: List<Entry>, descriptions: JsonObject): String {
    val groups = entries.filter { it.kind == "GalleryGroupEntry" }.sortedWith(compareBy({ it.order }, { it.route }))
    val allPages = entries.filter { it.kind == "GalleryPage" }
    require(allPages.all { it.repositoryPath.isNotBlank() }) { "@GalleryPage requires a repository-relative source path" }
    val home = allPages.single { it.route == "Home" }
    val pages = allPages.filter { it.route != "Home" }.sortedWith(compareBy({ it.order }, { it.route }))
    val routes = (groups + pages).map { it.route }
    require(routes.distinct().size == routes.size) { "Duplicate navigation route" }
    require(routes.none { it in setOf("All", "Settings") }) { "Reserved navigation route" }
    val groupRoutes = groups.map { it.route }.toSet()
    require(pages.all { it.value("group") in groupRoutes }) { "Page references an unknown navigation group" }
    val catalogGroups = descriptions.getValue("Groups").jsonArray.map { it.jsonObject }
    val specialGroups = catalogGroups.filter { it["IsSpecialSection"]?.jsonPrimitive?.booleanOrNull == true }
        .map { it.getValue("UniqueId").jsonPrimitive.content }.toSet()
    val details = catalogGroups.flatMap { it.getValue("Items").jsonArray }
        .associate { it.jsonObject.getValue("UniqueId").jsonPrimitive.content to it.jsonObject }
    require(pages.all { it.route in details }) { "Navigation page has no descriptive catalog entry" }
    fun quote(value: String) = buildString {
        append('"')
        value.forEach { c -> append(when(c) { '\\' -> "\\\\"; '"' -> "\\\""; '$' -> "\\$"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; else -> c.toString() }) }
        append('"')
    }
    fun strings(value: JsonElement?) = value?.jsonArray.orEmpty().joinToString(", ", "listOf(", ")") { quote(it.jsonPrimitive.content) }
    return buildString {
        appendLine("// Generated from Gallery navigation annotations. Do not edit.")
        appendLine("package $galleryPackage")
        appendLine("internal actual object GalleryCatalog {")
        appendLine("  actual val groups: List<GalleryGroup> by lazy { listOf(${groups.indices.joinToString { "group$it()" }}) }")
        appendLine("  actual val pages: List<GalleryPageInfo> by lazy { groups.flatMap { it.pages } }")
        appendLine("  actual val home: GalleryPageInfo = GalleryPageInfo(${quote(home.route)}, ${quote(home.value("title"))}, \"\", \"\", \"\", \"\", false, false, emptyList(), emptyList(), emptyList(), ${quote(home.args["glyph"].orEmpty())}, repositorySourcePath = ${quote(home.repositoryPath)})")
        groups.forEachIndexed { index, group ->
            appendLine("  private fun group$index() = GalleryGroup(${quote(group.route)}, ${quote(group.value("title"))}, ${quote(group.value("glyph"))}, listOf(")
            pages.filter { it.value("group") == group.route }.forEach { page ->
                val detail = details.getValue(page.route)
                fun field(key: String) = detail[key]?.jsonPrimitive?.content.orEmpty()
                val docs = detail["Docs"]?.jsonArray.orEmpty().joinToString(", ", "listOf(", ")") { doc ->
                    "GalleryLink(${quote(doc.jsonObject.getValue("Title").jsonPrimitive.content)}, ${quote(doc.jsonObject.getValue("Uri").jsonPrimitive.content)})"
                }
                appendLine("    GalleryPageInfo(${quote(page.route)}, ${quote(page.value("title"))}, ${quote(field("Subtitle"))}, ${quote(field("Description"))}, ${quote(field("ImagePath"))}, ${quote(group.route)}, ${field("IsNew") == "true"}, ${field("IsUpdated") == "true"}, ${strings(detail["Tags"])}, $docs, ${strings(detail["RelatedControls"])}, ${quote(page.args["glyph"].orEmpty())}, ${quote(field("ApiNamespace"))}, ${strings(detail["BaseClasses"])}, ${quote(field("SourcePath"))}, ${field("IsExperimental") == "true"}, ${quote(page.repositoryPath)}),")
            }
            appendLine("  ), isSpecialSection = ${group.route in specialGroups})")
        }
        appendLine("}")
        appendLine("internal actual object GalleryPageFactories {")
        appendLine("  actual fun create(route: String): microsoft.ui.xaml.UIElement? = when (route) {")
        pages.forEach { appendLine("    ${quote(it.route)} -> ${it.symbol}()") }
        appendLine("    else -> null")
        appendLine("  }")
        appendLine("  actual val routes: Set<String> = setOf(${pages.joinToString { quote(it.route) }})")
        appendLine("}")
    }
}

