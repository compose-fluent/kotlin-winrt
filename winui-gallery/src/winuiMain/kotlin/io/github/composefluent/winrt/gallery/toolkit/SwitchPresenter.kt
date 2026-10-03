// Port of CommunityToolkit/Windows components/Primitives/src/SwitchPresenter/SwitchPresenter.cs (MIT).
package io.github.composefluent.winrt.gallery.toolkit
import io.github.composefluent.winrt.runtime.WinRTXamlContentProperty
import io.github.composefluent.winrt.runtime.WinRTObservableList
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.ContentPresenter
@WinRTXamlContentProperty("SwitchCases")
internal class SwitchPresenter : ContentPresenter() {
    val SwitchCases: MutableList<Case> = WinRTObservableList()
    var Value: Any?
        get() = getValue(ValueProperty)
        set(value) { setValue(ValueProperty,value) }
    var CurrentCase: Case? = null
        private set
    init { loaded.add { _,_ -> EvaluateCases() } }
    private fun EvaluateCases() {
        val selected = SwitchCases.firstOrNull { !it.IsDefault && it.Value == Value } ?: SwitchCases.firstOrNull { it.IsDefault }
        if (selected !== CurrentCase) { CurrentCase = selected; content = selected?.Content }
    }
    companion object {
        val ValueProperty: DependencyProperty = DependencyProperty.register("Value",Any::class,SwitchPresenter::class,
            PropertyMetadata(null,PropertyChangedCallback { sender,_ -> checkNotNull(sender).asWinRT<SwitchPresenter>().EvaluateCases() }))
    }
}
