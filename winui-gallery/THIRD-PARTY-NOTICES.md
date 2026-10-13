# Third-party notices

WinUI Essential WinUI3 1.8.2 supplies SettingsCard, SettingsExpander, WindowEx and TenMica;
WinUIEssential.UWP 1.3.3 supplies SettingsCard, SettingsExpander and Segmented:
https://github.com/HO-COOH/WinUIEssentials
Distributed under the MIT license. The complete copyright and permission notice
is included in `licenses/WinUI-Essential-LICENSE.txt`.

`GalleryUwpStyles.xaml` adapts typography and subtle-button resources from
Microsoft.UI.Xaml `Themes/generic.xaml` (WinUI package 2.3.9):
https://github.com/microsoft/microsoft-ui-xaml
Copyright (c) Microsoft Corporation. All rights reserved. These resources use
the MIT license; the same complete Microsoft MIT notice is reproduced in
`licenses/WinUI-Gallery-LICENSE.txt`.

The Kotlin code preview palette values are adapted from JetBrains IntelliJ IDEA's
`platform/platform-resources/src/themes/expUI/expUI_lightScheme.xml` and
`expUI_darkScheme.xml` in https://github.com/JetBrains/intellij-community.
Copyright JetBrains s.r.o. and contributors. Licensed under Apache License 2.0;
see `licenses/JetBrains-Apache-2.0.txt`. Only the editor palette values are used.

The navigation catalog, control thumbnails, home and design illustrations, sample media,
and adapted Gallery layouts and sample code are derived from Microsoft WinUI Gallery, revision
`abb8cb4cef04a5080f5c0396f67a7ec502b36179`:
https://github.com/microsoft/WinUI-Gallery

Copyright (c) Microsoft Corporation. All rights reserved.
The repository distributes these files under the MIT license; the complete
notice is reproduced in `licenses/WinUI-Gallery-LICENSE.txt` and must accompany
redistribution. `src/winuiMain/appxResources/Assets/SampleMedia` contains the
upstream sample images, GIFs, SVG, videos, and contact data, distributed in that
repository under this license with no separate license notices in the media
directory. `Assets/Design` contains the upstream light/dark geometry, spacing,
and typography illustrations. The generated color palette and icon-name data
retain the same upstream MIT attribution. No additional font or external media
collection is bundled here.
The GitHub outline is translated from the Gallery's `GitHubIconPath` into
projected geometry calls. The animated logo source is translated from the
Gallery's generated `LottieLogo1.cs` into Kotlin composition calls; neither
translation loads markup at runtime. The import tools record their inputs.
The upstream DamagedHelmet model is not distributed because its provenance
includes a noncommercial license. The ContentIsland demonstration instead
constructs original procedural helmet geometry. The externally sourced
LibreICONS image is also excluded; the SVG demonstration uses the Gallery's
MIT-licensed MirrorPCConsent image.
The Microsoft Gallery application icon and tile files are not reused. The
top-level `AppList*`, `BadgeLogo*`, `StoreLogo*`, `SmallTile*`, `MedTile*`,
`LargeTile*`, `WideTile*`, and `SplashScreen*` files under
`src/winuiMain/appxResources/Assets` come from the user-supplied
`kt-winrt-Gallery-Assets.zip`.
`Assets/Tiles/GalleryIcon.ico` packages the existing `AppList.targetsize-*_altform-unplated.png`
images from that same asset set as a multi-size ICO, without using Microsoft's application icon.
`GalleryHeaderImage.png` and
`HomeHeaderTiles/*` are separate MIT-licensed Gallery illustrations covered by
the attribution above.

The navigation generator's separation of ordered groups, flat search index, and
page paths was informed by compose-fluent-ui's `gallery-processor`, revision
`9e863ae958f349c9ddc9faa4299b090b87495ef4`:
https://github.com/compose-fluent/compose-fluent-ui/tree/master/gallery-processor
No processor source is copied.

The compact density resource values are adapted from Microsoft WinUI's
`controls/dev/dll/DensityStyles/Compact.xaml`, distributed under the MIT license:
https://github.com/microsoft/microsoft-ui-xaml/blob/main/controls/dev/dll/DensityStyles/Compact.xaml
Copyright (c) Microsoft Corporation. All rights reserved. The MIT permission
and warranty terms reproduced in `licenses/WinUI-Gallery-LICENSE.txt` also
apply to these values.
