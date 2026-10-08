package io.github.composefluent.winrt.ide.nuget

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SingleSelectionLazyColumn
import org.jetbrains.jewel.foundation.lazy.rememberSingleSelectionLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.PathIconKey

internal data class WinRTNuGetListPackage(
    val id: String,
    val version: String,
    val description: String = "",
    val authors: String = "",
    val installed: String? = null,
    val group: String? = null,
)

/** Jewel owns selection colors, focus, accessibility and arrow-key navigation. */
@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun WinRTNuGetPackageList(
    packages: List<WinRTNuGetListPackage>,
    selectedId: String,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    onSelect: (WinRTNuGetListPackage) -> Unit,
) {
    val state = rememberSingleSelectionLazyListState()
    val lineHeight = with(LocalDensity.current) { JewelTheme.defaultTextStyle.fontSize.toDp() } * 1.5f
    // Headers participate in layout indices, but are never selectable.
    val rows = packages.groupBy { it.group }.flatMap { (group, items) ->
        (if (group == null) emptyList() else listOf("group:$group" to null)) + items.map { "package:${it.id.lowercase()}:${it.version}" to it }
    }
    LaunchedEffect(selectedId, packages) {
        state.selectedKeys = rows.firstOrNull { it.second?.id.equals(selectedId, true) }?.let { setOf(it.first) }.orEmpty()
    }
    SingleSelectionLazyColumn(
        modifier = modifier,
        state = state,
        onSelectedIndexesChange = { indices ->
            indices.firstOrNull()?.let { rows.getOrNull(it)?.second }?.takeUnless { it.id.equals(selectedId, true) }?.let(onSelect)
        },
    ) {
        items(rows.size, key = { rows[it].first }, selectable = { rows[it].second != null }) { index ->
            val (title, pkg) = rows[index]
            if (pkg == null) Text("${title.removePrefix("group:")} (${packages.count { it.group == title.removePrefix("group:") }})", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontWeight = FontWeight.Medium)
            else SimpleListItem(
                selected = isSelected,
                active = isActive,
                modifier = Modifier.fillMaxWidth(),
                icon = PathIconKey("nodes/ppLib.svg", AllIcons::class.java),
                iconContentDescription = null,
                height = lineHeight * (1 + (if (pkg.authors.isNotBlank()) 1 else 0) +
                    (if (pkg.description.isNotBlank()) 2 else 0) + (if (pkg.installed != null) 1 else 0)) + 14.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(pkg.id, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pkg.version, maxLines = 1)
                    }
                    if (pkg.authors.isNotBlank()) Text(pkg.authors, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pkg.description.isNotBlank()) Text(pkg.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    pkg.installed?.let { Text("Installed: $it", maxLines = 1) }
                }
            }
        }
        item(key = "package-list-footer", selectable = false) { footer() }
    }
}
