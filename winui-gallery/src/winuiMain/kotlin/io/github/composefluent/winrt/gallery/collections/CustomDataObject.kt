package io.github.composefluent.winrt.gallery.collections

internal class CustomDataObject {
    var Title: String = ""
    var ImageLocation: String = ""
    var Views: String = ""
    var Likes: String = ""
    var Description: String = ""
    companion object {
        private val descriptions: List<String> = listOf("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer id facilisis lectus. Cras nec convallis ante, quis pulvinar tellus. Integer dictum accumsan pulvinar. Pellentesque eget enim sodales sapien vestibulum consequat.",
            "Nullam eget mattis metus. Donec pharetra, tellus in mattis tincidunt, magna ipsum gravida nibh, vitae lobortis ante odio vel quam.",
            "Quisque accumsan pretium ligula in faucibus. Mauris sollicitudin augue vitae lorem cursus condimentum quis ac mauris. Pellentesque quis turpis non nunc pretium sagittis. Nulla facilisi. Maecenas eu lectus ante. Proin eleifend vel lectus non tincidunt. Fusce condimentum luctus nisi, in elementum ante tincidunt nec.",
            "Aenean in nisl at elit venenatis blandit ut vitae lectus. Praesent in sollicitudin nunc. Pellentesque justo augue, pretium at sem lacinia, scelerisque semper erat. Ut cursus tortor at metus lacinia dapibus.",
            "Ut consequat magna luctus justo egestas vehicula. Integer pharetra risus libero, et posuere justo mattis et.",
            "Proin malesuada, libero vitae aliquam venenatis, diam est faucibus felis, vitae efficitur erat nunc non mauris. Suspendisse at sodales erat.",
            "Aenean vulputate, turpis non tincidunt ornare, metus est sagittis erat, id lobortis orci odio eget quam. Suspendisse ex purus, lobortis quis suscipit a, volutpat vitae turpis.",
            "Duis facilisis, quam ut laoreet commodo, elit ex aliquet massa, non varius tellus lectus et nunc. Donec vitae risus ut ante pretium semper. Phasellus consectetur volutpat orci, eu dapibus turpis. Fusce varius sapien eu mattis pharetra.")
        fun GetDataObjects(includeAllItems: Boolean = false): List<CustomDataObject> =
            (1..if (includeAllItems) 13 else 8).map { index -> CustomDataObject().apply {
                Title = "Item $index"; ImageLocation = "ms-appx:///Assets/SampleMedia/LandscapeImage$index.jpg"
                Views = kotlin.random.Random.nextInt(100, 999).toString(); Likes = kotlin.random.Random.nextInt(10, 99).toString()
                Description = descriptions[(index - 1) % descriptions.size]
            } }
    }
}
