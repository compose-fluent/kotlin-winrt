package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeConfigurationTest {
    @Test
    fun exception_resource_keys_follow_feature_switch() {
        withFeatureSwitch(FeatureSwitches.UseExceptionResourceKeysPropertyName, false) {
            assertEquals(
                "Enumeration has not started. Call MoveNext.",
                WinRtRuntimeErrorStrings.InvalidOperation_EnumNotStarted,
            )
        }

        withFeatureSwitch(FeatureSwitches.UseExceptionResourceKeysPropertyName, true) {
            assertEquals(
                "InvalidOperation_EnumNotStarted",
                WinRtRuntimeErrorStrings.InvalidOperation_EnumNotStarted,
            )
        }
    }

    @Test
    fun xaml_projection_switch_moves_canonical_names_between_mux_and_wux() {
        withFeatureSwitch(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, false) {
            resetProjectionState()

            assertFalse(XamlProjectionConfiguration.useWindowsUiXamlProjections)
            assertEquals("mux", XamlProjectionConfiguration.select("mux", "wux"))
            assertEquals(
                "Microsoft.UI.Xaml.Data.INotifyPropertyChanged",
                TypeNameSupport.getNameForType(WinRtPropertyChangedNotifier::class),
            )
            assertEquals(
                "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedAction",
                TypeNameSupport.getNameForType(WinRtNotifyCollectionChangedAction::class),
            )
            assertEquals(
                "Microsoft.UI.Xaml.Data.PropertyChangedEventArgs",
                TypeNameSupport.getNameForType(WinRtPropertyChangedEventArgs::class),
            )
            assertEquals(
                Guid("90B17601-B065-586E-83D9-9ADC3A695284"),
                GuidGenerator.getIID(WinRtPropertyChangedNotifierProjection::class),
            )
            assertEquals(
                "Microsoft.UI.Xaml.IXamlServiceProvider",
                TypeNameSupport.getNameForType(WinRtServiceProvider::class),
            )
        }

        withFeatureSwitch(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, true) {
            resetProjectionState()

            assertTrue(XamlProjectionConfiguration.useWindowsUiXamlProjections)
            assertEquals("wux", XamlProjectionConfiguration.select("mux", "wux"))
            assertEquals(
                "Windows.UI.Xaml.Data.INotifyPropertyChanged",
                TypeNameSupport.getNameForType(WinRtPropertyChangedNotifier::class),
            )
            assertEquals(
                "Windows.UI.Xaml.Interop.NotifyCollectionChangedAction",
                TypeNameSupport.getNameForType(WinRtNotifyCollectionChangedAction::class),
            )
            assertEquals(
                "Windows.UI.Xaml.Data.PropertyChangedEventArgs",
                TypeNameSupport.getNameForType(WinRtPropertyChangedEventArgs::class),
            )
            assertEquals(
                Guid("CF75D69C-F2F4-486B-B302-BB4C09BAEBFA"),
                GuidGenerator.getIID(WinRtPropertyChangedNotifierProjection::class),
            )
            assertNull(TypeNameSupport.findKClassByNameCached("Microsoft.UI.Xaml.IXamlServiceProvider"))
        }
    }

    @Test
    fun default_custom_type_mappings_can_be_disabled_while_type_projection_stays_available() {
        withFeatureSwitch(FeatureSwitches.EnableDefaultCustomTypeMappingsPropertyName, false) {
            resetProjectionState()

            assertEquals("Windows.UI.Xaml.Interop.TypeName", TypeNameSupport.getNameForType(KClass::class))
            assertEquals(
                "io.github.kitectlab.winrt.runtime.WinRtUri",
                TypeNameSupport.getNameForType(WinRtUri::class),
            )
            assertNull(Projections.findCustomAbiTypeNameForType(WinRtUri::class))
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
