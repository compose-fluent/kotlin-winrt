package io.github.composefluent.winrt.gallery.validation

import io.github.composefluent.winrt.gallery.GalleryXamlValidation
import io.github.composefluent.winrt.gallery.basicinput.*
import microsoft.ui.xaml.automation.peers.RepeatButtonAutomationPeer
import microsoft.ui.xaml.automation.peers.ToggleButtonAutomationPeer
import microsoft.ui.xaml.automation.peers.ToggleSwitchAutomationPeer

internal fun validateCheckBoxPage(page: CheckBoxPage) = with(page) {
    GalleryXamlValidation.onLoadedSteps("CheckBox", this, listOf({
        check(allOptions.isChecked == null)
        check(option1.isChecked == false && option2.isChecked == true && option3.isChecked == false)
        ToggleButtonAutomationPeer(twoStateCheckBox).toggle()
        ToggleButtonAutomationPeer(threeStateCheckBox).toggle()
    }, {
        check(twoStateOutput.text == "Checked" && threeStateOutput.text == "Checked")
        ToggleButtonAutomationPeer(twoStateCheckBox).toggle()
        ToggleButtonAutomationPeer(threeStateCheckBox).toggle()
        ToggleButtonAutomationPeer(option1).toggle()
    }, {
        check(twoStateOutput.text == "Unchecked" && threeStateOutput.text == "Indeterminate")
        check(allOptions.isChecked == null)
        ToggleButtonAutomationPeer(threeStateCheckBox).toggle()
        ToggleButtonAutomationPeer(option3).toggle()
    }, {
        check(threeStateOutput.text == "Unchecked" && allOptions.isChecked == true)
        allOptions.isChecked = null
        ToggleButtonAutomationPeer(allOptions).toggle()
    }, {
        check(allOptions.isChecked == false && listOf(option1, option2, option3).all { it.isChecked == false })
        ToggleButtonAutomationPeer(allOptions).toggle()
    }, {
        check(allOptions.isChecked == true && listOf(option1, option2, option3).all { it.isChecked == true })
        ToggleButtonAutomationPeer(option2).toggle()
        threeStateExample.sourcePresenter.isExpanded = true
    }, {
        check(allOptions.isChecked == null && option2.isChecked == false)
        check(threeStateExample.sourcePresenter.content != null)
    }))
}

internal fun validateRepeatButtonPage(page: RepeatButtonPage) = with(page) {
    GalleryXamlValidation.onLoadedSteps("RepeatButton", this, listOf({
        RepeatButtonAutomationPeer(repeatButton).invoke()
        RepeatButtonAutomationPeer(repeatButton).invoke()
    }, {
        check(output.text == "Number of clicks: 2")
        ToggleButtonAutomationPeer(disableRepeat).toggle()
    }, {
        check(!repeatButton.isEnabled && output.text == "Number of clicks: 2")
        ToggleButtonAutomationPeer(disableRepeat).toggle()
    }, {
        check(repeatButton.isEnabled)
        RepeatButtonAutomationPeer(repeatButton).invoke()
    }, {
        check(output.text == "Number of clicks: 3")
    }))
}

internal fun validateToggleButtonPage(page: ToggleButtonPage) = with(page) {
    GalleryXamlValidation.onLoadedSteps("ToggleButton", this, listOf({
        check(output.text == "Off")
        ToggleButtonAutomationPeer(toggleButton).toggle()
    }, {
        check(toggleButton.isChecked == true && output.text == "On")
        ToggleButtonAutomationPeer(toggleButton).toggle()
    }, {
        check(toggleButton.isChecked == false && output.text == "Off")
        ToggleButtonAutomationPeer(disableToggle).toggle()
    }, {
        check(!toggleButton.isEnabled && output.text == "Off")
    }))
}

internal fun validateToggleSwitchPage(page: ToggleSwitchPage) = with(page) {
    GalleryXamlValidation.onLoadedSteps("ToggleSwitch", this, listOf({
        check(!simpleToggle.isOn && workToggle.isOn && progress.isActive)
        ToggleSwitchAutomationPeer(workToggle).toggle()
        ToggleSwitchAutomationPeer(simpleToggle).toggle()
    }, {
        check(simpleToggle.isOn && !workToggle.isOn && !progress.isActive)
        check(workToggle.offContent == "Do work" && workToggle.onContent == "Working")
        ToggleSwitchAutomationPeer(workToggle).toggle()
        workExample.sourcePresenter.isExpanded = true
    }, {
        check(workToggle.isOn && progress.isActive && workExample.sourcePresenter.content != null)
    }))
}
