package io.github.kitectlab.winrt.projections.windows.data.json

import io.github.kitectlab.winrt.runtime.JvmComRuntime
import io.github.kitectlab.winrt.runtime.JvmWinRtRuntime
import io.github.kitectlab.winrt.runtime.KnownHResults
import io.github.kitectlab.winrt.runtime.PlatformRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class JsonObjectProjectionTest {
    @Test
    fun can_parse_json_and_read_named_string() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.parse("""{"name":"codex","kind":"winrt"}""").use { jsonObject ->
                assertEquals("Windows.Data.Json.JsonObject", jsonObject.getRuntimeClassName())
                assertEquals("codex", jsonObject.getNamedString("name"))
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_read_named_value_type_and_named_boolean() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.parse("""{"phone":null,"verified":true}""").use { jsonObject ->
                jsonObject.getNamedValue("phone").use { phoneValue ->
                    assertEquals(JsonValueType.Null, phoneValue.valueType)
                }
                assertTrue(jsonObject.getNamedBoolean("verified"))
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_traverse_named_array_and_nested_object() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.parse("""{"education":[{"type":"High School"}],"nickname":"codex"}""").use { jsonObject ->
                jsonObject.getNamedArray("education").use { education ->
                    education.getObjectAt(0u).use { firstEntry ->
                        assertEquals("High School", firstEntry.getNamedString("type"))
                    }
                }
                jsonObject.getNamedValue("nickname").use { nickname ->
                    assertEquals(JsonValueType.String, nickname.valueType)
                    assertEquals("codex", nickname.getString())
                    assertEquals("\"codex\"", nickname.stringify())
                }
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_read_numbers_from_object_array_and_value() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.parse("""{"timezone":-8,"scores":[3,4.5,true]}""").use { jsonObject ->
                assertEquals(-8.0, jsonObject.getNamedNumber("timezone"), 0.0)
                jsonObject.getNamedValue("timezone").use { timezone ->
                    assertEquals(JsonValueType.Number, timezone.valueType)
                    assertEquals(-8.0, timezone.getNumber(), 0.0)
                }
                jsonObject.getNamedArray("scores").use { scores ->
                    assertEquals(3.0, scores.getNumberAt(0u), 0.0)
                    assertEquals(4.5, scores.getNumberAt(1u), 0.0)
                    assertTrue(scores.getBooleanAt(2u))
                }
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_parse_json_array_directly() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonArray.parse("""["alpha", 2, false]""").use { jsonArray ->
                assertEquals("alpha", jsonArray.getStringAt(0u))
                assertEquals(2.0, jsonArray.getNumberAt(1u), 0.0)
                assertEquals(false, jsonArray.getBooleanAt(2u))
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_parse_json_value_and_project_object() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonValue.parse("""{"kind":"winrt"}""").use { jsonValue ->
                assertEquals(JsonValueType.Object, jsonValue.valueType)
                jsonValue.getObject().use { jsonObject ->
                    assertEquals("winrt", jsonObject.getNamedString("kind"))
                }
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_create_boolean_number_and_string_json_values() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonValue.createBooleanValue(true).use { booleanValue ->
                assertEquals(JsonValueType.Boolean, booleanValue.valueType)
                assertTrue(booleanValue.getBoolean())
                assertEquals("true", booleanValue.stringify())
            }
            JsonValue.createNumberValue(4.5).use { numberValue ->
                assertEquals(JsonValueType.Number, numberValue.valueType)
                assertEquals(4.5, numberValue.getNumber(), 0.0)
            }
            JsonValue.createStringValue("codex").use { stringValue ->
                assertEquals(JsonValueType.String, stringValue.valueType)
                assertEquals("codex", stringValue.getString())
                assertEquals("\"codex\"", stringValue.stringify())
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_try_parse_json_surfaces() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.tryParse("""{"name":"codex"}""").use { jsonObject ->
                assertEquals("codex", jsonObject?.getNamedString("name"))
            }
            JsonArray.tryParse("""[1,2,3]""").use { jsonArray ->
                assertEquals(2.0, jsonArray!!.getNumberAt(1u), 0.0)
            }
            JsonValue.tryParse("""true""").use { jsonValue ->
                assertEquals(JsonValueType.Boolean, jsonValue!!.valueType)
                assertTrue(jsonValue.getBoolean())
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun invalid_try_parse_returns_null() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            assertEquals(null, JsonObject.tryParse("not json"))
            assertEquals(null, JsonArray.tryParse("{"))
            assertEquals(null, JsonValue.tryParse("{"))
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun can_set_named_values_and_read_them_back() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.parse("""{}""").use { jsonObject ->
                JsonValue.createStringValue("codex").use { stringValue ->
                    jsonObject.setNamedValue("name", stringValue)
                }
                JsonValue.createBooleanValue(true).use { booleanValue ->
                    jsonObject.setNamedValue("enabled", booleanValue)
                }
                JsonValue.createNumberValue(7.5).use { numberValue ->
                    jsonObject.setNamedValue("ratio", numberValue)
                }

                assertEquals("codex", jsonObject.getNamedString("name"))
                assertTrue(jsonObject.getNamedBoolean("enabled"))
                assertEquals(7.5, jsonObject.getNamedNumber("ratio"), 0.0)
                jsonObject.getNamedValue("name").use { storedName ->
                    assertEquals(JsonValueType.String, storedName.valueType)
                    assertEquals("codex", storedName.getString())
                }
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }

    @Test
    fun get_named_members_can_use_cswinrt_style_defaults() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.parse("""{"id":"1146217767","verified":true,"education":[{"type":"College"}]}""").use { jsonObject ->
                assertEquals("1146217767", jsonObject.getNamedString("id", ""))
                assertEquals("fallback", jsonObject.getNamedString("missing", "fallback"))
                assertTrue(jsonObject.getNamedBoolean("verified", false))
                assertEquals(false, jsonObject.getNamedBoolean("missing-flag", false))

                JsonArray.create().use { fallbackArray ->
                    jsonObject.getNamedArray("education", fallbackArray).use { education ->
                        education.getObjectAt(0u).use { firstEntry ->
                            assertEquals("College", firstEntry.getNamedString("type"))
                        }
                    }
                    val missingArray = jsonObject.getNamedArray("missing-array", fallbackArray)
                    assertTrue(missingArray === fallbackArray)
                }
            }
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }
}
