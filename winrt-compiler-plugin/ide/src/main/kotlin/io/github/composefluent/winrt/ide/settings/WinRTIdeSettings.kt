package io.github.composefluent.winrt.ide.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.*
import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import javax.swing.JComponent

@Service(Service.Level.APP)
@State(name = "KotlinWinRTIdeSettings", storages = [Storage("kotlin-winrt.xml")])
class WinRTIdeSettings : SimplePersistentStateComponent<WinRTIdeSettings.State>(State()) {
    class State : BaseState() {
        var hotReloadEnabled by property(true)
    }
    var hotReloadEnabled: Boolean
        get() = state.hotReloadEnabled
        set(value) { state.hotReloadEnabled = value }
}

class WinRTIdeConfigurable : Configurable {
    private var hotReload by mutableStateOf(true)
    private var component: JComponent? = null
    override fun getDisplayName() = "Kotlin WinRT"
    override fun createComponent(): JComponent = compose(focusOnClickInside = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckboxRow("Enable XAML Hot Reload when running applications", hotReload, { hotReload = it })
            Text("Run and Debug connect automatically for supported JVM WinUI applications, including packaged applications.")
        }
    }.also { component = it }
    override fun isModified() = hotReload != service<WinRTIdeSettings>().hotReloadEnabled
    override fun reset() { hotReload = service<WinRTIdeSettings>().hotReloadEnabled }
    override fun apply() { service<WinRTIdeSettings>().hotReloadEnabled = hotReload }
    @OptIn(ExperimentalComposeUiApi::class)
    override fun disposeUIResources() {
        component?.let { UIUtil.findComponentOfType(it, ComposePanel::class.java)?.dispose() }
        component = null
    }
}
