package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import microsoft.ui.xaml.input.ICommand
import microsoft.ui.xaml.interop.NotifyCollectionChangedAction
import microsoft.ui.xaml.IXamlServiceProvider
import microsoft.ui.xaml.data.INotifyPropertyChanged
import microsoft.ui.xaml.data.PropertyChangedEventArgs
import windows.foundation.Uri

class RuntimeConfigurationTest {
    @Test
    fun exception_resource_keys_follow_feature_switch() {
        withFeatureSwitch(FeatureSwitches.UseExceptionResourceKeysPropertyName, false) {
            assertEquals(
                "Enumeration has not started. Call MoveNext.",
                WinRTRuntimeErrorStrings.InvalidOperation_EnumNotStarted,
            )
        }

        withFeatureSwitch(FeatureSwitches.UseExceptionResourceKeysPropertyName, true) {
            assertEquals(
                "InvalidOperation_EnumNotStarted",
                WinRTRuntimeErrorStrings.InvalidOperation_EnumNotStarted,
            )
        }
    }

    @Test
    fun feature_switch_parser_accepts_runtime_boolean_spellings() {
        listOf("true", "1", "yes", "on", " TRUE ").forEach { value ->
            assertEquals(true, parseFeatureSwitchValue(value))
        }
        listOf("false", "0", "no", "off", " FALSE ").forEach { value ->
            assertEquals(false, parseFeatureSwitchValue(value))
        }
        listOf("", "maybe", "enabled").forEach { value ->
            assertNull(parseFeatureSwitchValue(value))
        }
        assertNull(parseFeatureSwitchValue(null))
    }

    @Test
    fun xaml_projection_switch_moves_canonical_names_between_mux_and_wux() {
        withFeatureSwitch(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, false) {
            resetProjectionState()

            assertFalse(XamlProjectionConfiguration.useWindowsUiXamlProjections)
            assertEquals("mux", XamlProjectionConfiguration.select("mux", "wux"))
            assertEquals(
                "Microsoft.UI.Xaml.Data.INotifyPropertyChanged",
                TypeNameSupport.getNameForType(INotifyPropertyChanged::class),
            )
            assertEquals(
                "Microsoft.UI.Xaml.Input.ICommand",
                TypeNameSupport.getNameForType(ICommand::class),
            )
            assertEquals(ICommand::class, TypeNameSupport.findKClassByNameCached("Microsoft.UI.Xaml.Interop.ICommand"))
            assertEquals(ICommand::class, TypeNameSupport.findKClassByNameCached("Windows.UI.Xaml.Input.ICommand"))
            assertEquals(ICommand::class, TypeNameSupport.findKClassByNameCached("Windows.UI.Xaml.Interop.ICommand"))
            assertEquals(
                "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedAction",
                TypeNameSupport.getNameForType(NotifyCollectionChangedAction::class),
            )
            assertEquals(
                "Microsoft.UI.Xaml.Data.PropertyChangedEventArgs",
                TypeNameSupport.getNameForType(PropertyChangedEventArgs::class),
            )
            assertEquals(
                Guid("90B17601-B065-586E-83D9-9ADC3A695284"),
                GuidGenerator.getIID(INotifyPropertyChangedProjection::class),
            )
            assertEquals(
                "Microsoft.UI.Xaml.Data.INotifyPropertyChanged",
                WinRTTypeRegistry.findByClass(INotifyPropertyChangedProjection::class)?.projectedTypeName,
            )
            assertNull(TypeNameSupport.findKClassByNameCached(INotifyPropertyChangedProjection::class.typeDisplayName()))
            assertEquals(
                "Microsoft.UI.Xaml.IXamlServiceProvider",
                TypeNameSupport.getNameForType(IXamlServiceProvider::class),
            )
        }

        withFeatureSwitch(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, true) {
            resetProjectionState()

            assertTrue(XamlProjectionConfiguration.useWindowsUiXamlProjections)
            assertEquals("wux", XamlProjectionConfiguration.select("mux", "wux"))
            assertEquals(
                "Windows.UI.Xaml.Data.INotifyPropertyChanged",
                TypeNameSupport.getNameForType(INotifyPropertyChanged::class),
            )
            assertEquals(
                "Windows.UI.Xaml.Input.ICommand",
                TypeNameSupport.getNameForType(ICommand::class),
            )
            assertEquals(ICommand::class, TypeNameSupport.findKClassByNameCached("Microsoft.UI.Xaml.Input.ICommand"))
            assertEquals(ICommand::class, TypeNameSupport.findKClassByNameCached("Microsoft.UI.Xaml.Interop.ICommand"))
            assertEquals(ICommand::class, TypeNameSupport.findKClassByNameCached("Windows.UI.Xaml.Interop.ICommand"))
            assertEquals(
                "Windows.UI.Xaml.Interop.NotifyCollectionChangedAction",
                TypeNameSupport.getNameForType(NotifyCollectionChangedAction::class),
            )
            assertEquals(
                "Windows.UI.Xaml.Data.PropertyChangedEventArgs",
                TypeNameSupport.getNameForType(PropertyChangedEventArgs::class),
            )
            assertEquals(
                Guid("CF75D69C-F2F4-486B-B302-BB4C09BAEBFA"),
                GuidGenerator.getIID(INotifyPropertyChangedProjection::class),
            )
            assertEquals(
                "Windows.UI.Xaml.Data.INotifyPropertyChanged",
                WinRTTypeRegistry.findByClass(INotifyPropertyChangedProjection::class)?.projectedTypeName,
            )
            assertNull(TypeNameSupport.findKClassByNameCached(INotifyPropertyChangedProjection::class.typeDisplayName()))
            assertNull(TypeNameSupport.findKClassByNameCached("Microsoft.UI.Xaml.IXamlServiceProvider"))
        }
    }

    @Test
    fun default_custom_type_mappings_can_be_disabled_while_type_projection_stays_available() {
        withFeatureSwitch(FeatureSwitches.EnableDefaultCustomTypeMappingsPropertyName, false) {
            resetProjectionState()

            assertEquals("Windows.UI.Xaml.Interop.TypeName", TypeNameSupport.getNameForType(KClass::class))
            assertEquals(
                "windows.foundation.Uri",
                TypeNameSupport.getNameForType(Uri::class),
            )
            assertNull(Projections.findCustomAbiTypeNameForType(Uri::class))
            assertNull(TypeNameSupport.findKClassByNameCached("Microsoft.UI.Xaml.Data.INotifyPropertyChanged"))
        }
    }

    private fun withFeatureSwitch(
        propertyName: String,
        value: Boolean,
        block: () -> Unit,
    ) {
        FeatureSwitches.overrideForTests(propertyName, value)
        try {
            block()
        } finally {
            FeatureSwitches.overrideForTests(propertyName, null)
            resetProjectionState()
            FeatureSwitches.clearForTests()
        }
    }

    private fun resetProjectionState() {
        ComWrappersSupport.clearRegistriesForTests()
        ActivationFactory.clearRuntimeCache()
    }
}
