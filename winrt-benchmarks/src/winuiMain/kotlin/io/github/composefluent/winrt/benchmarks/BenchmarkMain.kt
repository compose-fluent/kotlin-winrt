package io.github.composefluent.winrt.benchmarks

import io.github.composefluent.winrt.runtime.RuntimeScope
import windows.`data`.json.JsonObject
import windows.`data`.json.JsonValueType

private const val PAYLOAD = "{\"name\":\"kotlin-winrt\",\"verified\":true,\"count\":42.5}"

fun main(args: Array<String>) {
    RuntimeScope.initializeMultithreaded().use {
        runBenchmarkSuite(args, createWinRTScenarios())
    }
}

private fun createWinRTScenarios(): List<BenchmarkScenario> {
    val json = JsonObject.parse(PAYLOAD)
    val stringifiedLength = json.stringify().length.toLong()

    return listOf(
        BenchmarkScenario("activate_json_object", expectedSingleChecksum = 1L) { iterations ->
            var checksum = 0L
            repeat(iterations) {
                if (JsonObject().valueType == JsonValueType.Object) {
                    checksum += 1L
                }
            }
            checksum
        },
        BenchmarkScenario("get_value_type", expectedSingleChecksum = 1L) { iterations ->
            var checksum = 0L
            repeat(iterations) {
                if (json.valueType == JsonValueType.Object) {
                    checksum += 1L
                }
            }
            checksum
        },
        BenchmarkScenario("get_named_boolean", expectedSingleChecksum = 1L) { iterations ->
            var checksum = 0L
            repeat(iterations) {
                if (json.getNamedBoolean("verified")) {
                    checksum += 1L
                }
            }
            checksum
        },
        BenchmarkScenario("get_named_string", expectedSingleChecksum = "kotlin-winrt".length.toLong()) { iterations ->
            var checksum = 0L
            repeat(iterations) {
                checksum += json.getNamedString("name").length
            }
            checksum
        },
        BenchmarkScenario("stringify", expectedSingleChecksum = stringifiedLength) { iterations ->
            var checksum = 0L
            repeat(iterations) {
                checksum += json.stringify().length
            }
            checksum
        },
        BenchmarkScenario("parse_get_named_number", expectedSingleChecksum = 42L) { iterations ->
            var checksum = 0L
            repeat(iterations) {
                checksum += JsonObject.parse(PAYLOAD).getNamedNumber("count").toLong()
            }
            checksum
        },
    )
}
