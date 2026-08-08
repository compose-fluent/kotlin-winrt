package io.github.composefluent.winrt.projections

import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRTOut
import io.github.composefluent.winrt.runtime.WinRTRuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import windows.`data`.json.JsonObject
import windows.`data`.json.JsonValue
import windows.foundation.Point
import windows.foundation.PropertyValue

class GeneratedModuleRouteParityTest {
    @Test
    fun generated_json_routes_preserve_values_and_projected_ownership() {
        RuntimeScope.initializeSingleThreaded().use {
            val root = JsonObject.parse(
                """{"text":"module-route","number":17.25,"flag":true,"child":{"value":"alive"}}""",
            )
            var rootClosed = false
            try {
                assertEquals("module-route", root.getNamedString("text"))
                assertEquals(17.25, root.getNamedNumber("number"))
                assertTrue(root.getNamedBoolean("flag"))
                assertTrue(root.stringify().contains("\"text\":\"module-route\""))

                JsonValue.createStringValue("inserted").also { inserted ->
                    try {
                        root.setNamedValue("inserted", inserted)
                        assertEquals("inserted", root.getNamedString("inserted"))
                    } finally {
                        inserted.nativeObject.close()
                    }
                }

                JsonValue.createBooleanValue(true).also { booleanValue ->
                    try {
                        assertTrue(booleanValue.getBoolean())
                    } finally {
                        booleanValue.nativeObject.close()
                    }
                }

                JsonValue.createNumberValue(-123.5).also { numberValue ->
                    try {
                        assertEquals(-123.5, numberValue.getNumber())
                    } finally {
                        numberValue.nativeObject.close()
                    }
                }

                val parsedOut = WinRTOut<JsonObject>()
                assertTrue(JsonObject.tryParse("""{"out":"initialized"}""", parsedOut))
                assertTrue(parsedOut.isInitialized)
                parsedOut.value.also { parsed ->
                    try {
                        assertEquals("initialized", parsed.getNamedString("out"))
                    } finally {
                        parsed.nativeObject.close()
                    }
                }

                root.getNamedObject("child").also { child ->
                    try {
                        root.nativeObject.close()
                        rootClosed = true
                        assertEquals("alive", child.getNamedString("value"))
                    } finally {
                        child.nativeObject.close()
                    }
                }
            } finally {
                if (!rootClosed) {
                    root.nativeObject.close()
                }
            }
        }
    }

    @Test
    fun generated_struct_route_preserves_layout_sensitive_fields() {
        RuntimeScope.initializeSingleThreaded().use {
            val boxed = PropertyValue.createPoint(Point(1.25f, -9.5f))
            val point = assertIs<Point>(boxed)

            assertEquals(1.25f, point.x)
            assertEquals(-9.5f, point.y)
        }
    }

    @Test
    fun generated_hstring_route_propagates_the_exact_hresult() {
        RuntimeScope.initializeSingleThreaded().use {
            val json = JsonObject.parse("{}")
            try {
                val failure = assertFailsWith<WinRTRuntimeException> {
                    json.getNamedString("missing")
                }

                assertEquals(KnownHResults.WEB_E_JSON_VALUE_NOT_FOUND, failure.hResult)
            } finally {
                json.nativeObject.close()
            }
        }
    }
}
