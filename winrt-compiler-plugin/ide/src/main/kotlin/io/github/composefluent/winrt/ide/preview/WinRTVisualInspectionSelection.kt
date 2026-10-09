package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.components.Service
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow

@Service(Service.Level.PROJECT)
internal class WinRTVisualInspectionSelection {
    val root = MutableStateFlow<String?>(null)
    val instance = MutableStateFlow(0)
    val path = MutableStateFlow<List<Int>>(emptyList())
    fun selectRoot(value: String) { root.value = value; instance.value = 0; path.value = emptyList() }
    fun reset() { root.value = null; instance.value = 0; path.value = emptyList() }
    fun resolve(roots: List<WinRTXamlHotReloadRoot>): WinRTXamlHotReloadRoot? =
        roots.firstOrNull { key(it) == root.value } ?:
        roots.firstOrNull { it.className.substringAfterLast('.').equals("MainWindow", true) } ?: roots.firstOrNull()
    companion object {
        fun key(root: WinRTXamlHotReloadRoot) = root.className + "\n" + root.resourcePath
        fun hit(nodes: List<WinRTXamlVisualNode>, x: Double, y: Double): WinRTXamlVisualNode? = nodes.filter { node ->
            val r = node.bounds
            r.width > 0 && r.height > 0 && x >= r.x && y >= r.y && x < r.x + r.width && y < r.y + r.height
        }.maxWithOrNull(compareBy<WinRTXamlVisualNode> { it.path.size }.thenBy { nodes.indexOf(it) })
    }
}
