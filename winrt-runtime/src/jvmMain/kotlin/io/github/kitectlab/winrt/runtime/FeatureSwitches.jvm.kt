package io.github.kitectlab.winrt.runtime

internal actual fun platformReadFeatureSwitch(propertyName: String): Boolean? =
    System.getProperty(propertyName)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { value ->
            when (value.lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
        }
