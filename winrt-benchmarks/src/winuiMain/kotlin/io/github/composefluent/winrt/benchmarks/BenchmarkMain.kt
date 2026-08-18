package io.github.composefluent.winrt.benchmarks

import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic

fun main(args: Array<String>) {
    RuntimeScope.initializeMultithreaded().use {
        WinRTProjectionSupportIntrinsic.ensureInitialized()
        val scenarios = buildList {
            addAll(queryInterfaceScenarios())
            addAll(eventScenarios())
            addAll(guidScenarios())
            addAll(reflectionScenarios())
            addAll(asyncScenarios())
            addAll(nonAgileObjectScenarios())
        }
        check(scenarios.size == REFERENCE_SCENARIO_COUNT) {
            "Expected $REFERENCE_SCENARIO_COUNT .cswinrt reference scenarios, got ${scenarios.size}."
        }
        val duplicateNames = scenarios.groupingBy(BenchmarkScenario::name).eachCount().filterValues { it > 1 }.keys
        check(duplicateNames.isEmpty()) {
            "Duplicate benchmark scenarios: ${duplicateNames.sorted().joinToString()}."
        }
        runBenchmarkSuite(
            args = args,
            scenarios = scenarios,
        )
    }
}

private const val REFERENCE_SCENARIO_COUNT: Int = 97
