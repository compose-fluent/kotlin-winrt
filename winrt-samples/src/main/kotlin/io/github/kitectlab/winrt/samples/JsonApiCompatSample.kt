package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.projections.windows.`data`.json.JsonObject
import io.github.kitectlab.winrt.projections.windows.`data`.json.JsonValue
import io.github.kitectlab.winrt.projections.windows.`data`.json.JsonValueType
import io.github.kitectlab.winrt.runtime.RuntimeScope

data class JsonApiCompatResult(
    val id: String,
    val nullValueType: JsonValueType,
    val verified: Boolean,
)

object JsonApiCompatSample {
    val sampleText: String = """
        {
          "id": "1146217767",
          "name": "A. Datum",
          "phone": null,
          "education": [
            {
              "school": {
                "id": "20504016387",
                "name": "Contoso High School"
              },
              "type": "High School"
            },
            {
              "school": {
                "id": "116138758396662",
                "name": "Contoso University"
              },
              "type": "College"
            }
          ],
          "timezone": -8,
          "verified": true
        }
    """.trimIndent()

    fun run(): JsonApiCompatResult =
        RuntimeScope.initializeSingleThreaded().use {
            val jsonObject = JsonObject.Parse(sampleText)
            // cswinrt ApiCompat reads the "phone" null through GetNamedValue. The current
            // generator/runtime path still needs nullable object-return support for that exact call.
            // The same cswinrt sample iterates "education"; generated collection projection support
            // must land before this sample can execute that branch without sample-local glue.
            JsonApiCompatResult(
                id = jsonObject.GetNamedString("id"),
                nullValueType = JsonValue.CreateNullValue().valueType,
                verified = jsonObject.GetNamedBoolean("verified"),
            )
        }
}
