package io.github.composefluent.winrt.runtime

object WinUiXamlInterfaceIds {
    val IXamlMetadataProvider: Guid = Guid("A96251F0-2214-5D53-8746-CE99A2593CD7")
    val IXamlType: Guid = Guid("D24219DF-7EC9-57F1-A27B-6AF251D9C5BC")
    val IXamlControlsXamlMetaDataProviderStatics: Guid = Guid("2D7EB3FD-ECDB-5084-B7E0-12F9598381EF")
    val IApplicationOverrides: Guid = Guid("A33E81EF-C665-503B-8827-D27EF1720A06")
}

object WinUiXamlMetadataProviderSlots {
    const val GetXamlType = 6
    const val GetXamlTypeByFullName = 7
    const val GetXmlnsDefinitions = 8
}

object WinUiXamlControlsXamlMetadataProviderStaticsSlots {
    const val Initialize = 6
}

object WinUiXamlMetadataProviderInfo {
    const val runtimeClassName: String = "Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider"
}
