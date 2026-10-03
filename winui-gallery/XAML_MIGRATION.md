# Gallery XAML migration

The reference is official WinUI Gallery `v2.9.3`, commit
`14a4a1a2b8ddc527dc4a7d5f7e743d7c2bc97db7`. Windows App SDK remains **2.5.1**.
All 122 existing routes, including Home, have adjacent XAML and Kotlin files.
The baseline example counts below remain unchanged.

Static layouts, styles, templates and markup move to XAML. Kotlin retains event
handlers, data models, procedural animations, dynamic control creation that an
example intentionally demonstrates, and platform integrations. Namespace and
code language changes are necessary adaptations of the original markup.
Native controls and data scopes are produced by the shared projection,
authoring and compiler pipelines; the Gallery has no private ABI shim.

The Windows JVM and `mingwX64` builds compile every listed route and the shared
resources with CI compiler `0.1.0-preview.5`, protocol 2. The original template, phased
binding, deferred element, `x:Bind` expression and substitution paths are
implemented by the compiler pipeline. The 355 independent sample documents live in
`SampleDefinitions/<Route>/*.txt`; pure markup examples display one XAML tab.
Complete page code is not repeated in each source expander.

The table records preliminary direct-executable load checks for all 122 routes
on both targets. These short checks reported a live window and no immediate
navigation or initialization error. Direct execution does not give the process
MSIX package identity, and short observation missed later layout and binding
failures. The table does not establish Start-menu launch, sustained stability,
or complete interaction acceptance.

Compilation, direct route loading and packaged activation are separate states. Route load
results below do not imply that all interactions or visual parity were checked.
The existing Button/CheckBox/RepeatButton/ToggleButton/ToggleSwitch interaction
checks passed on both targets. They cover
private event handlers, generated control properties, styles and independent
source tabs.
A complete visual
pass, platform dialogs, camera/notification/deep-link activation, light/dark/high
contrast, keyboard navigation and every sample interaction require actual UI
inspection. Native and IDE status are recorded independently.

## Existing route inventory

| Route | Group | Kotlin / XAML | Baseline examples | JVM preliminary load | Native preliminary load |
| --- | --- | --- | ---: | --- | --- |
| Home |  | [HomePage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/pages/HomePage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/pages/HomePage.xaml) | 0 | Passed | Passed |
| SystemBackdrops | Styles | [SystemBackdropsPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/SystemBackdropsPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/SystemBackdropsPage.xaml) | 3 | Passed | Passed |
| AccessibilityColorContrast | AccessibilityItem | [AccessibilityColorContrastPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/accessibility/AccessibilityColorContrastPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/accessibility/AccessibilityColorContrastPage.xaml) | 0 | Passed | Passed |
| AccessibilityKeyboard | AccessibilityItem | [AccessibilityKeyboardPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/accessibility/AccessibilityKeyboardPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/accessibility/AccessibilityKeyboardPage.xaml) | 6 | Passed | Passed |
| AccessibilityScreenReader | AccessibilityItem | [AccessibilityScreenReaderPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/accessibility/AccessibilityScreenReaderPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/accessibility/AccessibilityScreenReaderPage.xaml) | 11 | Passed | Passed |
| Button | BasicInput | [ButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ButtonPage.xaml) | 4 | Passed | Passed |
| CheckBox | BasicInput | [CheckBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/CheckBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/CheckBoxPage.xaml) | 3 | Passed | Passed |
| ColorPicker | BasicInput | [ColorPickerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ColorPickerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ColorPickerPage.xaml) | 1 | Passed | Passed |
| ComboBox | BasicInput | [ComboBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ComboBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ComboBoxPage.xaml) | 3 | Passed | Passed |
| DropDownButton | BasicInput | [DropDownButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/DropDownButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/DropDownButtonPage.xaml) | 2 | Passed | Passed |
| HyperlinkButton | BasicInput | [HyperlinkButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/HyperlinkButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/HyperlinkButtonPage.xaml) | 2 | Passed | Passed |
| RadioButton | BasicInput | [RadioButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/RadioButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/RadioButtonPage.xaml) | 2 | Passed | Passed |
| RatingControl | BasicInput | [RatingControlPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/RatingControlPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/RatingControlPage.xaml) | 2 | Passed | Passed |
| RepeatButton | BasicInput | [RepeatButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/RepeatButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/RepeatButtonPage.xaml) | 1 | Passed | Passed |
| Slider | BasicInput | [SliderPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/SliderPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/SliderPage.xaml) | 4 | Passed | Passed |
| SplitButton | BasicInput | [SplitButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/SplitButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/SplitButtonPage.xaml) | 2 | Passed | Passed |
| ToggleButton | BasicInput | [ToggleButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ToggleButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ToggleButtonPage.xaml) | 1 | Passed | Passed |
| ToggleSplitButton | BasicInput | [ToggleSplitButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ToggleSplitButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ToggleSplitButtonPage.xaml) | 1 | Passed | Passed |
| ToggleSwitch | BasicInput | [ToggleSwitchPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ToggleSwitchPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/basicinput/ToggleSwitchPage.xaml) | 2 | Passed | Passed |
| FlipView | Collections | [FlipViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/FlipViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/FlipViewPage.xaml) | 3 | Passed | Passed |
| GridView | Collections | [GridViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/GridViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/GridViewPage.xaml) | 3 | Passed | Passed |
| ItemsRepeater | Collections | [ItemsRepeaterPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ItemsRepeaterPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ItemsRepeaterPage.xaml) | 6 | Passed | Passed |
| ItemsView | Collections | [ItemsViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ItemsViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ItemsViewPage.xaml) | 3 | Passed | Passed |
| ListBox | Collections | [ListBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ListBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ListBoxPage.xaml) | 2 | Passed | Passed |
| ListView | Collections | [ListViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ListViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/ListViewPage.xaml) | 10 | Passed | Passed |
| PullToRefresh | Collections | [PullToRefreshPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/PullToRefreshPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/PullToRefreshPage.xaml) | 2 | Passed | Passed |
| TreeView | Collections | [TreeViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/TreeViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/collections/TreeViewPage.xaml) | 4 | Passed | Passed |
| CalendarDatePicker | DateAndTime | [CalendarDatePickerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/CalendarDatePickerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/CalendarDatePickerPage.xaml) | 1 | Passed | Passed |
| CalendarView | DateAndTime | [CalendarViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/CalendarViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/CalendarViewPage.xaml) | 1 | Passed | Passed |
| DatePicker | DateAndTime | [DatePickerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/DatePickerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/DatePickerPage.xaml) | 2 | Passed | Passed |
| TimePicker | DateAndTime | [TimePickerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/TimePickerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dateandtime/TimePickerPage.xaml) | 3 | Passed | Passed |
| Color | DesignItem | [ColorPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/ColorPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/ColorPage.xaml) | 0 | Passed | Passed |
| Geometry | DesignItem | [GeometryPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/GeometryPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/GeometryPage.xaml) | 1 | Passed | Passed |
| Iconography | DesignItem | [IconographyPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/IconographyPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/IconographyPage.xaml) | 0 | Passed | Passed |
| Spacing | DesignItem | [SpacingPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/SpacingPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/SpacingPage.xaml) | 0 | Passed | Passed |
| Typography | DesignItem | [TypographyPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/TypographyPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/design/TypographyPage.xaml) | 1 | Passed | Passed |
| ContentDialog | DialogsAndFlyouts | [ContentDialogPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/ContentDialogPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/ContentDialogPage.xaml) | 2 | Passed | Passed |
| Flyout | DialogsAndFlyouts | [FlyoutPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/FlyoutPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/FlyoutPage.xaml) | 1 | Passed | Passed |
| Popup | DialogsAndFlyouts | [PopupPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/PopupPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/PopupPage.xaml) | 1 | Passed | Passed |
| TeachingTip | DialogsAndFlyouts | [TeachingTipPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/TeachingTipPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/dialogsandflyouts/TeachingTipPage.xaml) | 3 | Passed | Passed |
| Binding | FundamentalsItem | [BindingPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/BindingPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/BindingPage.xaml) | 7 | Passed | Passed |
| CustomUserControls | FundamentalsItem | [CustomUserControlsPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/CustomUserControlsPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/CustomUserControlsPage.xaml) | 3 | Passed | Passed |
| CustomXamlConditionals | FundamentalsItem | [CustomXamlConditionalsPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/CustomXamlConditionalsPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/CustomXamlConditionalsPage.xaml) | 3 | Passed | Passed |
| ScratchPad | FundamentalsItem | [ScratchPadPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/ScratchPadPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/ScratchPadPage.xaml) | 0 | Passed | Passed |
| Templates | FundamentalsItem | [TemplatesPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/TemplatesPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/TemplatesPage.xaml) | 3 | Passed | Passed |
| XamlResources | FundamentalsItem | [XamlResourcesPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/XamlResourcesPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/XamlResourcesPage.xaml) | 3 | Passed | Passed |
| XamlStyles | FundamentalsItem | [XamlStylesPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/XamlStylesPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/fundamentals/XamlStylesPage.xaml) | 2 | Passed | Passed |
| Border | Layout | [BorderPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/BorderPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/BorderPage.xaml) | 1 | Passed | Passed |
| Canvas | Layout | [CanvasPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/CanvasPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/CanvasPage.xaml) | 1 | Passed | Passed |
| Expander | Layout | [ExpanderPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/ExpanderPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/ExpanderPage.xaml) | 2 | Passed | Passed |
| Grid | Layout | [GridPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/GridPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/GridPage.xaml) | 1 | Passed | Passed |
| RelativePanel | Layout | [RelativePanelPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/RelativePanelPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/RelativePanelPage.xaml) | 1 | Passed | Passed |
| SplitView | Layout | [SplitViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/SplitViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/SplitViewPage.xaml) | 1 | Passed | Passed |
| StackPanel | Layout | [StackPanelPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/StackPanelPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/StackPanelPage.xaml) | 1 | Passed | Passed |
| VariableSizedWrapGrid | Layout | [VariableSizedWrapGridPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/VariableSizedWrapGridPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/VariableSizedWrapGridPage.xaml) | 1 | Passed | Passed |
| Viewbox | Layout | [ViewboxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/ViewboxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/layout/ViewboxPage.xaml) | 1 | Passed | Passed |
| AnimatedVisualPlayer | Media | [AnimatedVisualPlayerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/AnimatedVisualPlayerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/AnimatedVisualPlayerPage.xaml) | 1 | Passed | Passed |
| CaptureElementPreview | Media | [CaptureElementPreviewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/CaptureElementPreviewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/CaptureElementPreviewPage.xaml) | 1 | Passed | Passed |
| Image | Media | [ImagePage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/ImagePage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/ImagePage.xaml) | 6 | Passed | Passed |
| MapControl | Media | [MapControlPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/MapControlPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/MapControlPage.xaml) | 1 | Passed | Passed |
| MediaPlayerElement | Media | [MediaPlayerElementPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/MediaPlayerElementPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/MediaPlayerElementPage.xaml) | 2 | Passed | Passed |
| PersonPicture | Media | [PersonPicturePage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/PersonPicturePage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/PersonPicturePage.xaml) | 1 | Passed | Passed |
| Sound | Media | [SoundPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/SoundPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/SoundPage.xaml) | 3 | Passed | Passed |
| WebView2 | Media | [WebView2Page.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/WebView2Page.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/media/WebView2Page.xaml) | 1 | Passed | Passed |
| AppBarButton | MenusAndToolbars | [AppBarButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/AppBarButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/AppBarButtonPage.xaml) | 6 | Passed | Passed |
| AppBarSeparator | MenusAndToolbars | [AppBarSeparatorPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/AppBarSeparatorPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/AppBarSeparatorPage.xaml) | 1 | Passed | Passed |
| AppBarToggleButton | MenusAndToolbars | [AppBarToggleButtonPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/AppBarToggleButtonPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/AppBarToggleButtonPage.xaml) | 4 | Passed | Passed |
| CommandBar | MenusAndToolbars | [CommandBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/CommandBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/CommandBarPage.xaml) | 1 | Passed | Passed |
| CommandBarFlyout | MenusAndToolbars | [CommandBarFlyoutPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/CommandBarFlyoutPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/CommandBarFlyoutPage.xaml) | 1 | Passed | Passed |
| MenuBar | MenusAndToolbars | [MenuBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/MenuBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/MenuBarPage.xaml) | 3 | Passed | Passed |
| MenuFlyout | MenusAndToolbars | [MenuFlyoutPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/MenuFlyoutPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/MenuFlyoutPage.xaml) | 7 | Passed | Passed |
| StandardUICommand | MenusAndToolbars | [StandardUICommandPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/StandardUICommandPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/StandardUICommandPage.xaml) | 1 | Passed | Passed |
| SwipeControl | MenusAndToolbars | [SwipeControlPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/SwipeControlPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/SwipeControlPage.xaml) | 5 | Passed | Passed |
| XamlUICommand | MenusAndToolbars | [XamlUICommandPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/XamlUICommandPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/menusandtoolbars/XamlUICommandPage.xaml) | 1 | Passed | Passed |
| ConnectedAnimation | Motion | [ConnectedAnimationPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ConnectedAnimationPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ConnectedAnimationPage.xaml) | 4 | Passed | Passed |
| EasingFunction | Motion | [EasingFunctionPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/EasingFunctionPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/EasingFunctionPage.xaml) | 4 | Passed | Passed |
| ImplicitTransition | Motion | [ImplicitTransitionPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ImplicitTransitionPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ImplicitTransitionPage.xaml) | 6 | Passed | Passed |
| PageTransition | Motion | [PageTransitionPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/PageTransitionPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/PageTransitionPage.xaml) | 1 | Passed | Passed |
| ParallaxView | Motion | [ParallaxViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ParallaxViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ParallaxViewPage.xaml) | 2 | Passed | Passed |
| ThemeTransition | Motion | [ThemeTransitionPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ThemeTransitionPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/ThemeTransitionPage.xaml) | 5 | Passed | Passed |
| XamlCompInterop | Motion | [XamlCompInteropPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/XamlCompInteropPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/motion/XamlCompInteropPage.xaml) | 5 | Passed | Passed |
| AppWindow | MultipleWindows | [AppWindowPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/AppWindowPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/AppWindowPage.xaml) | 7 | Passed | Passed |
| AppWindowTitleBar | MultipleWindows | [AppWindowTitleBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/AppWindowTitleBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/AppWindowTitleBarPage.xaml) | 3 | Passed | Passed |
| CreateMultipleWindows | MultipleWindows | [CreateMultipleWindowsPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/CreateMultipleWindowsPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/CreateMultipleWindowsPage.xaml) | 1 | Passed | Passed |
| TitleBar | MultipleWindows | [TitleBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/TitleBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/multiplewindows/TitleBarPage.xaml) | 3 | Passed | Passed |
| BreadcrumbBar | Navigation | [BreadcrumbBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/BreadcrumbBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/BreadcrumbBarPage.xaml) | 2 | Passed | Passed |
| NavigationView | Navigation | [NavigationViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/NavigationViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/NavigationViewPage.xaml) | 8 | Passed | Passed |
| Pivot | Navigation | [PivotPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/PivotPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/PivotPage.xaml) | 1 | Passed | Passed |
| SelectorBar | Navigation | [SelectorBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/SelectorBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/SelectorBarPage.xaml) | 3 | Passed | Passed |
| TabView | Navigation | [TabViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/TabViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/navigation/TabViewPage.xaml) | 10 | Passed | Passed |
| AnnotatedScrollBar | Scrolling | [AnnotatedScrollBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/AnnotatedScrollBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/AnnotatedScrollBarPage.xaml) | 1 | Passed | Passed |
| PipsPager | Scrolling | [PipsPagerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/PipsPagerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/PipsPagerPage.xaml) | 2 | Passed | Passed |
| ScrollView | Scrolling | [ScrollViewPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/ScrollViewPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/ScrollViewPage.xaml) | 3 | Passed | Passed |
| ScrollViewer | Scrolling | [ScrollViewerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/ScrollViewerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/ScrollViewerPage.xaml) | 1 | Passed | Passed |
| SemanticZoom | Scrolling | [SemanticZoomPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/SemanticZoomPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/scrolling/SemanticZoomPage.xaml) | 1 | Passed | Passed |
| AppNotification | Shell | [AppNotificationPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/shell/AppNotificationPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/shell/AppNotificationPage.xaml) | 5 | Passed | Passed |
| BadgeNotificationManager | Shell | [BadgeNotificationManagerPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/shell/BadgeNotificationManagerPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/shell/BadgeNotificationManagerPage.xaml) | 2 | Passed | Passed |
| JumpList | Shell | [JumpListPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/shell/JumpListPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/shell/JumpListPage.xaml) | 2 | Passed | Passed |
| InfoBadge | StatusAndInfo | [InfoBadgePage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/InfoBadgePage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/InfoBadgePage.xaml) | 4 | Passed | Passed |
| InfoBar | StatusAndInfo | [InfoBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/InfoBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/InfoBarPage.xaml) | 3 | Passed | Passed |
| ProgressBar | StatusAndInfo | [ProgressBarPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/ProgressBarPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/ProgressBarPage.xaml) | 2 | Passed | Passed |
| ProgressRing | StatusAndInfo | [ProgressRingPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/ProgressRingPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/ProgressRingPage.xaml) | 2 | Passed | Passed |
| ToolTip | StatusAndInfo | [ToolTipPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/ToolTipPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/statusandinfo/ToolTipPage.xaml) | 3 | Passed | Passed |
| Acrylic | Styles | [AcrylicPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/AcrylicPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/AcrylicPage.xaml) | 3 | Passed | Passed |
| AnimatedIcon | Styles | [AnimatedIconPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/AnimatedIconPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/AnimatedIconPage.xaml) | 2 | Passed | Passed |
| CompactSizing | Styles | [CompactSizingPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/CompactSizingPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/CompactSizingPage.xaml) | 1 | Passed | Passed |
| IconElement | Styles | [IconElementPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/IconElementPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/IconElementPage.xaml) | 6 | Passed | Passed |
| Line | Styles | [LinePage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/LinePage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/LinePage.xaml) | 4 | Passed | Passed |
| RadialGradientBrush | Styles | [RadialGradientBrushPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/RadialGradientBrushPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/RadialGradientBrushPage.xaml) | 1 | Passed | Passed |
| Shape | Styles | [ShapePage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/ShapePage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/ShapePage.xaml) | 3 | Passed | Passed |
| SystemBackdropElement | Styles | [SystemBackdropElementPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/SystemBackdropElementPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/SystemBackdropElementPage.xaml) | 1 | Passed | Passed |
| ThemeShadow | Styles | [ThemeShadowPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/ThemeShadowPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/styles/ThemeShadowPage.xaml) | 1 | Passed | Passed |
| Clipboard | System | [ClipboardPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/system/ClipboardPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/system/ClipboardPage.xaml) | 6 | Passed | Passed |
| ContentIsland | System | [ContentIslandPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/system/ContentIslandPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/system/ContentIslandPage.xaml) | 1 | Passed | Passed |
| StoragePickers | System | [StoragePickersPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/system/StoragePickersPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/system/StoragePickersPage.xaml) | 5 | Passed | Passed |
| AutoSuggestBox | Text | [AutoSuggestBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/AutoSuggestBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/AutoSuggestBoxPage.xaml) | 2 | Passed | Passed |
| NumberBox | Text | [NumberBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/NumberBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/NumberBoxPage.xaml) | 3 | Passed | Passed |
| PasswordBox | Text | [PasswordBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/PasswordBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/PasswordBoxPage.xaml) | 3 | Passed | Passed |
| RichEditBox | Text | [RichEditBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/RichEditBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/RichEditBoxPage.xaml) | 5 | Passed | Passed |
| RichTextBlock | Text | [RichTextBlockPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/RichTextBlockPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/RichTextBlockPage.xaml) | 4 | Passed | Passed |
| TextBlock | Text | [TextBlockPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/TextBlockPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/TextBlockPage.xaml) | 5 | Passed | Passed |
| TextBox | Text | [TextBoxPage.kt](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/TextBoxPage.kt) / [XAML](src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/text/TextBoxPage.xaml) | 4 | Passed | Passed |

## Shared surfaces

| Surface | Kotlin / XAML owner | State |
| --- | --- | --- |
| Application resources and startup | GalleryApplication | Adjacent XAML; compiled |
| Main window, navigation and search | MainWindow / GalleryNavigationHost | Original static shell XAML; compiled |
| Home | pages/HomePage / HomePageHeader / HomePageTile / HorizontalScrollContainer / OpacityMaskView | Original templates and show/hide animations; compiled |
| Page frame, header and actions | pages/ItemPage / controls/PageHeader | Adjacent XAML; compiled |
| All controls and Settings | pages/AllControlsPage / pages/SettingsPage | Adjacent XAML; compiled |
| Example layout | controls/ControlExample / SampleThemeListener | Original adaptive XAML and animations; compiled |
| Source display and copy | controls/SampleCodePresenter / processor / code-document | Original presenter with XAML/Kotlin highlighting and substitutions; compiled |
| Styles and templates | Styles dictionaries / ItemTemplates / GalleryExampleResources | XAML resources and dictionary connector scopes; compiled |
| Shared style library | resources / GalleryGridStyles / GalleryTextBlockStyles | Resource-only XBF variants consumed on both targets; original URIs preserved |
| Shared plain model | models / collections/Recipe | Inferred XAML schema and typed accessors consumed on both targets; no WinRT component export |
| Toolkit behaviors | toolkit/ImplicitAnimations / Case / SwitchPresenter | Kotlin XAML types and attached dependency properties; compiled |

## Target and IDE acceptance

- JVM: complete XBF/Kotlin/authoring/PRI package build passes. The current package opens ItemsRepeater with the shared Recipe model and style dictionaries; all five existing basic-control interaction checks pass.
- mingwX64: both clean Windows CI builds produce signed Release MSIX packages with the released compiler. The CI Native package opens ItemsRepeater on the local Windows host and passes all five existing basic-control interaction checks. Local semantic/XBF compilation and XAML ABI checks pass; local Release linking encountered a shared-heap exhaustion and then an unexplained daemon exit, so the current Native runtime check uses the CI binary.
- IDE: CLI FIR/IR integration exists. This repository does not ship a Kotlin IDE plugin for no-annotation discovery, a XAML designer or hot reload. Gradle success is not IDE completion/navigation validation.

The real resource-library build graph passes no-change, dictionary edit, rename,
last-XAML removal and restoration checks on both targets. The real Gallery JVM
graph also passes a batch change of control type, element name and handler
parameter type, followed by restoration of the original sources. No additional
validation harness is shipped for these checks.

The current Computer Use runtime cannot initialize its Windows helper. The
earlier route activation results and current CLI interaction checks therefore
remain separate from the outstanding complete UI interaction, visual parity and
close/reclaim acceptance. Those plan items remain incomplete.

The root `PLAN.md` is unchanged. The independent approved plan tracks completed
implementation items separately from acceptance that has not been performed.
