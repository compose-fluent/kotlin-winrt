package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Shared runtime configuration switches corresponding to
 * `.cswinrt/src/WinRT.Runtime/Configuration/FeatureSwitches.cs`.
 *
 * Kotlin/JVM narrows `.cswinrt`'s `AppContext`-backed lookup to JVM system properties with
 * Kotlin-owned switch names. Kotlin/Native reads process environment variables with the same names.
 */
internal object FeatureSwitches {
    const val EnableDynamicObjectsSupportPropertyName: String = "KOTLIN_WINRT_ENABLE_DYNAMIC_OBJECTS_SUPPORT"
    const val UseExceptionResourceKeysPropertyName: String = "KOTLIN_WINRT_USE_EXCEPTION_RESOURCE_KEYS"
    const val EnableDefaultCustomTypeMappingsPropertyName: String = "KOTLIN_WINRT_ENABLE_DEFAULT_CUSTOM_TYPE_MAPPINGS"
    const val EnableICustomPropertyProviderSupportPropertyName: String = "KOTLIN_WINRT_ENABLE_ICUSTOMPROPERTYPROVIDER_SUPPORT"
    const val EnableIReferenceSupportPropertyName: String = "KOTLIN_WINRT_ENABLE_IREFERENCE_SUPPORT"
    const val EnableIDynamicInterfaceCastableSupportPropertyName: String = "KOTLIN_WINRT_ENABLE_IDYNAMICINTERFACECASTABLE"
    const val EnableManifestFreeActivationPropertyName: String = "KOTLIN_WINRT_ENABLE_MANIFEST_FREE_ACTIVATION"
    const val ManifestFreeActivationReportOriginalExceptionPropertyName: String =
        "KOTLIN_WINRT_MANIFEST_FREE_ACTIVATION_REPORT_ORIGINAL_EXCEPTION"
    const val UseWindowsUIXamlProjectionsPropertyName: String = "KOTLIN_WINRT_USE_WINDOWS_UI_XAML_PROJECTIONS"
    const val SuppressCustomPropertyNotSupportedExceptionPropertyName: String =
        "KOTLIN_WINRT_SUPPRESS_CUSTOM_PROPERTY_NOT_SUPPORTED_EXCEPTION"
    const val TraceCcwPropertyName: String = "KOTLIN_WINRT_TRACE_CCW"

    private val cachedResults = ConcurrentCacheMap<String, Int>()
    private val testOverrides = ConcurrentCacheMap<String, Int>()
    @OptIn(ExperimentalAtomicApi::class)
    private val traceCcwState = AtomicInt(0)
    @OptIn(ExperimentalAtomicApi::class)
    private val defaultCustomTypeMappingsState = AtomicInt(0)
    @OptIn(ExperimentalAtomicApi::class)
    private val customPropertyProviderSupportState = AtomicInt(0)

    val enableDynamicObjectsSupport: Boolean
        get() = getConfigurationValue(EnableDynamicObjectsSupportPropertyName, defaultValue = true)

    val useExceptionResourceKeys: Boolean
        get() = getConfigurationValue(UseExceptionResourceKeysPropertyName, defaultValue = false)

    @OptIn(ExperimentalAtomicApi::class)
    val enableDefaultCustomTypeMappings: Boolean
        get() = getCachedConfigurationValue(
            EnableDefaultCustomTypeMappingsPropertyName,
            defaultCustomTypeMappingsState,
            defaultValue = true,
        )

    @OptIn(ExperimentalAtomicApi::class)
    val enableICustomPropertyProviderSupport: Boolean
        get() = getCachedConfigurationValue(
            EnableICustomPropertyProviderSupportPropertyName,
            customPropertyProviderSupportState,
            defaultValue = true,
        )

    val enableIReferenceSupport: Boolean
        get() = getConfigurationValue(EnableIReferenceSupportPropertyName, defaultValue = true)

    val enableIDynamicInterfaceCastableSupport: Boolean
        get() = getConfigurationValue(EnableIDynamicInterfaceCastableSupportPropertyName, defaultValue = true)

    val enableManifestFreeActivation: Boolean
        get() = getConfigurationValue(EnableManifestFreeActivationPropertyName, defaultValue = true)

    val manifestFreeActivationReportOriginalException: Boolean
        get() = getConfigurationValue(ManifestFreeActivationReportOriginalExceptionPropertyName, defaultValue = false)

    val useWindowsUiXamlProjections: Boolean
        get() = getConfigurationValue(UseWindowsUIXamlProjectionsPropertyName, defaultValue = false)

    val suppressCustomPropertyNotSupportedException: Boolean
        get() = getConfigurationValue(SuppressCustomPropertyNotSupportedExceptionPropertyName, defaultValue = false)

    @OptIn(ExperimentalAtomicApi::class)
    val traceCcw: Boolean
        get() {
            val cachedState = traceCcwState.load()
            if (cachedState != 0) {
                return stateToBoolean(cachedState)
            }
            val resolvedState = booleanState(
                getConfigurationValue(TraceCcwPropertyName, defaultValue = false),
            )
            traceCcwState.compareAndSet(expectedValue = 0, newValue = resolvedState)
            return stateToBoolean(resolvedState)
        }

    @OptIn(ExperimentalAtomicApi::class)
    internal fun overrideForTests(
        propertyName: String,
        value: Boolean?,
    ) {
        if (value == null) {
            testOverrides.remove(propertyName)
        } else {
            testOverrides[propertyName] = booleanState(value)
        }
        cachedResults.remove(propertyName)
        if (propertyName == TraceCcwPropertyName) {
            traceCcwState.store(0)
        }
        if (propertyName == EnableDefaultCustomTypeMappingsPropertyName) {
            defaultCustomTypeMappingsState.store(0)
        }
        if (propertyName == EnableICustomPropertyProviderSupportPropertyName) {
            customPropertyProviderSupportState.store(0)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    internal fun clearForTests() {
        cachedResults.clear()
        testOverrides.clear()
        traceCcwState.store(0)
        defaultCustomTypeMappingsState.store(0)
        customPropertyProviderSupportState.store(0)
    }

    // CsWinRT FeatureSwitches caches each switch as 0/+1/-1. Use the same publication and
    // reset behavior as traceCcw so warm CCW configuration reads avoid map/lambda work.
    @OptIn(ExperimentalAtomicApi::class)
    @Suppress("NOTHING_TO_INLINE")
    private inline fun getCachedConfigurationValue(
        propertyName: String,
        cachedState: AtomicInt,
        defaultValue: Boolean,
    ): Boolean {
        val state = cachedState.load()
        if (state != 0) {
            return stateToBoolean(state)
        }
        val resolvedState = booleanState(getConfigurationValue(propertyName, defaultValue))
        cachedState.compareAndSet(expectedValue = 0, newValue = resolvedState)
        return stateToBoolean(resolvedState)
    }

    private fun getConfigurationValue(
        propertyName: String,
        defaultValue: Boolean,
    ): Boolean {
        testOverrides[propertyName]?.let { return stateToBoolean(it) }
        return stateToBoolean(
            cachedResults.computeIfAbsent(propertyName) { key ->
                booleanState(platformReadFeatureSwitch(key) ?: defaultValue)
            },
        )
    }

    private fun stateToBoolean(state: Int): Boolean = state > 0

    private fun booleanState(value: Boolean): Int = if (value) 1 else -1
}

internal fun parseFeatureSwitchValue(value: String?): Boolean? =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { trimmed ->
            when (trimmed.lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
        }

internal expect fun platformReadFeatureSwitch(propertyName: String): Boolean?
