package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.pages.ItemsPageBase
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "ItemsRepeater", title = "ItemsRepeater", group = "Collections", order = 2)
internal class ItemsRepeaterPage : ItemsPageBase() {
    private var ready = false
    private val MaxLength = 425
    private val tasks = GalleryPageTasks(this)
    val Numbers: MutableList<Int> = WinRTObservableList((0 until 500).toList())
    val BarItems: MutableList<Bar> = WinRTObservableList(listOf(Bar(300.0, 425), Bar(25.0, 425), Bar(175.0, 425)))
    val ColorList: List<String> = listOf("Blue", "BlueViolet", "Crimson", "DarkCyan", "DarkGoldenrod", "DarkMagenta", "DarkOliveGreen", "DarkRed", "DarkSlateBlue", "DeepPink", "IndianRed", "MediumSlateBlue", "Maroon", "MidnightBlue", "Peru", "SaddleBrown", "SteelBlue", "OrangeRed", "Firebrick", "DarkKhaki")
    private val foodGroups: List<NestedCategory> = listOf(
        NestedCategory("Fruits", listOf("Apricots", "Bananas", "Grapes", "Strawberries", "Watermelon", "Plums", "Blueberries")),
        NestedCategory("Vegetables", listOf("Broccoli", "Spinach", "Sweet potato", "Cauliflower", "Onion", "Brussels sprouts", "Carrots")),
        NestedCategory("Grains", listOf("Rice", "Quinoa", "Pasta", "Bread", "Farro", "Oats", "Barley")),
        NestedCategory("Proteins", listOf("Steak", "Chicken", "Tofu", "Salmon", "Pork", "Chickpeas", "Eggs")))
    private val staticRecipeData: List<Recipe> = (0 until 1000).map { index ->
        val ingredients = foodGroups.map { it.CategoryItems.random() }.toMutableList()
        repeat(kotlin.random.Random.nextInt(4)) { val extra = listOf("Garlic", "Lemon", "Butter", "Lime", "Feta Cheese", "Parmesan Cheese", "Breadcrumbs").random(); if (extra !in ingredients) ingredients.add(extra) }
        Recipe(index, "Recipe $index", ColorList.random(), ingredients)
    }
    private val filteredRecipeData: MyItemsSource = MyItemsSource(staticRecipeData)
    private var IsSortDescending = false
    private var LastSelectedColorButton: Button? = null
    private var PreviouslyFocusedAnimatedScrollRepeaterIndex = -1
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        MixedTypeRepeater.itemsSource = listOf(64, "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", 128, "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.", 256, "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.", 512, "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.", 1024)
        outerRepeater.itemsSource = foodGroups; animatedScrollRepeater.itemsSource = ColorList
        animatedScrollRepeater.elementPrepared.add(::OnElementPrepared)
        VariedImageSizeRepeater.itemsSource = filteredRecipeData
        SetCode("VerticalStackLayout", "HorizontalBarTemplate", "MyFeedLayout")
    }
    private fun SetCode(layoutKey: String, templateKey: String, secondLayout: String? = null) {
        SampleCodeLayout.Value = GalleryCodeCatalog.sourceDocument("ItemsRepeater/$layoutKey.txt")?.source ?: ""
        SampleCodeDT.Value = GalleryCodeCatalog.sourceDocument("ItemsRepeater/$templateKey.txt")?.source ?: ""
        if (secondLayout != null) SampleCodeLayout2.Value = GalleryCodeCatalog.sourceDocument("ItemsRepeater/$secondLayout.txt")?.source ?: ""
    }
    private fun AddBtn_Click(sender: Any?, args: RoutedEventArgs) { BarItems.add(Bar(kotlin.random.Random.nextInt(MaxLength).toDouble(), MaxLength)); DeleteBtn.isEnabled = true }
    private fun DeleteBtn_Click(sender: Any?, args: RoutedEventArgs) { if (BarItems.isNotEmpty()) BarItems.removeAt(0); DeleteBtn.isEnabled = BarItems.isNotEmpty() }
    private fun RadioBtn_Click(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val key = checkNotNull(sender).asWinRT<RadioButtons>().selectedItem?.asWinRT<FrameworkElement>()?.tag?.toString() ?: return
        val template = when (key) { "HorizontalStackLayout" -> "VerticalBarTemplate"; "UniformGridLayout" -> "CircularTemplate"; else -> "HorizontalBarTemplate" }
        repeater.maxWidth = when (key) { "HorizontalStackLayout" -> 6000.0; "UniformGridLayout" -> 540.0; else -> MaxLength + 12.0 }
        repeater.layout = checkNotNull(resources[key]).asWinRT<VirtualizingLayout>(); repeater.itemTemplate = checkNotNull(resources[template]).asWinRT<DataTemplate>(); repeater.itemsSource = BarItems
        layout.Value = key; elementGenerator.Value = template; SetCode(key, template)
    }
    private fun LayoutBtn_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val key = checkNotNull(sender).asWinRT<RadioButtons>().selectedItem?.asWinRT<RadioButton>()?.tag?.toString() ?: return
        repeater2.layout = checkNotNull(resources[key]).asWinRT<VirtualizingLayout>(); layout2.Value = key
        SampleCodeLayout2.Value = GalleryCodeCatalog.sourceDocument("ItemsRepeater/$key.txt")?.source ?: ""
    }
    private fun OnAnimatedItemGotFocus(sender: Any?, args: RoutedEventArgs) { val item = checkNotNull(sender).asWinRT<FrameworkElement>(); PreviouslyFocusedAnimatedScrollRepeaterIndex = animatedScrollRepeater.getElementIndex(item); item.startBringIntoView(BringIntoViewOptions().apply { verticalAlignmentRatio = 0.5; animationDesired = true }) }
    private fun OnAnimatedScrollRepeaterGettingFocus(sender: UIElement, args: microsoft.ui.xaml.input.GettingFocusEventArgs) { val old = args.oldFocusedElement?.asWinRT<UIElement>() ?: return; if (PreviouslyFocusedAnimatedScrollRepeaterIndex >= 0 && animatedScrollRepeater.getElementIndex(old) == -1) args.newFocusedElement = animatedScrollRepeater.tryGetElement(PreviouslyFocusedAnimatedScrollRepeaterIndex) }
    private fun OnAnimatedItemClicked(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>(); colorRectangle.fill = button.background
        announce(button, "Rectangle color set to ${button.content}", "RectangleChangedNotificationActivityId")
        LastSelectedColorButton?.let { microsoft.ui.xaml.automation.AutomationProperties.setName(it, it.content.toString()) }
        microsoft.ui.xaml.automation.AutomationProperties.setName(button, "${button.content} , selected"); LastSelectedColorButton = button
    }
    private fun OnElementPrepared(sender: ItemsRepeater, args: ItemsRepeaterElementPreparedEventArgs) {
        val item = microsoft.ui.xaml.hosting.ElementCompositionPreview.getElementVisual(checkNotNull(args.element))
        val viewport = microsoft.ui.xaml.hosting.ElementCompositionPreview.getElementVisual(Animated_ScrollViewer)
        val properties = microsoft.ui.xaml.hosting.ElementCompositionPreview.getScrollViewerManipulationPropertySet(Animated_ScrollViewer)
        val scale = checkNotNull(properties.compositor).createExpressionAnimation("1 - abs((svVisual.Size.Y/2 - scrollProperties.Translation.Y) - (item.Offset.Y + item.Size.Y/2))*(.25/(svVisual.Size.Y/2))").apply { setReferenceParameter("svVisual", viewport); setReferenceParameter("scrollProperties", properties); setReferenceParameter("item", item) }
        item.startAnimation("Scale.X", scale); item.startAnimation("Scale.Y", scale)
        item.startAnimation("CenterPoint", checkNotNull(properties.compositor).createExpressionAnimation("Vector3(item.Size.X/2, item.Size.Y/2, 0)").apply { setReferenceParameter("item", item) })
    }
    private fun FilterRecipes_FilterChanged(sender: Any?, args: RoutedEventArgs) { UpdateSortAndFilter() }
    private fun OnSortAscClick(sender: Any?, args: RoutedEventArgs) { if (IsSortDescending) { IsSortDescending = false; UpdateSortAndFilter() } }
    private fun OnSortDesClick(sender: Any?, args: RoutedEventArgs) { if (!IsSortDescending) { IsSortDescending = true; UpdateSortAndFilter() } }
    private fun UpdateSortAndFilter() {
        if (!ready) return
        val filtered = staticRecipeData.filter { it.Ingredients.contains(FilterRecipes.text, true) }
        filteredRecipeData.InitializeCollection(if (IsSortDescending) filtered.sortedByDescending { it.NumIngredients } else filtered.sortedBy { it.NumIngredients })
        announce(VariedImageSizeRepeater, "Filtered recipes, ${filtered.size} results.", "RecipesFilteredNotificationActivityId")
    }
    private fun OnAnimatedScrollRepeaterKeyDown(sender: Any?, args: microsoft.ui.xaml.input.KeyRoutedEventArgs) {
        if (args.handled) return
        val index = when (args.key) { windows.system.VirtualKey.Home -> 0; windows.system.VirtualKey.End -> ColorList.lastIndex; else -> return }
        if (index == PreviouslyFocusedAnimatedScrollRepeaterIndex) return
        animatedScrollRepeater.getOrCreateElement(index).let { it.startBringIntoView(); it.asWinRT<Control>().focus(FocusState.Programmatic) }; args.handled = true
    }
}
