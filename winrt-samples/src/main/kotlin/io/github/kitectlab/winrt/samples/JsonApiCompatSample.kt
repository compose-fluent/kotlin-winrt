package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.projections.windows.`data`.json.JsonArray
import io.github.kitectlab.winrt.projections.windows.`data`.json.JsonObject
import io.github.kitectlab.winrt.projections.windows.`data`.json.JsonValueType
import io.github.kitectlab.winrt.runtime.RuntimeScope

data class JsonApiCompatResult(
    val id: String,
    val nullValueType: JsonValueType,
    val verified: Boolean,
    val firstEducationType: String,
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
            val phoneJsonValue = jsonObject.GetNamedValue("phone")
            val education = jsonObject.GetNamedArray("education", JsonArray())
            JsonApiCompatResult(
                id = jsonObject.GetNamedString("id"),
                nullValueType = phoneJsonValue.valueType,
                verified = jsonObject.GetNamedBoolean("verified"),
                firstEducationType = education.GetObjectAt(0u).GetNamedString("type"),
            )
        }
}
