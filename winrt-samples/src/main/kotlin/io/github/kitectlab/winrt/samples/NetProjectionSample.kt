package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.runtime.RuntimeScope

data class SimpleMathComponentResult(
    val expression: String,
    val value: Double,
)

object NetProjectionSample {
    const val componentRuntimeClass: String =
        "io.github.kitectlab.winrt.projections.simplemathcomponent.SimpleMath"

    fun add(firstNumber: Double = 5.5, secondNumber: Double = 6.5): SimpleMathComponentResult =
        RuntimeScope.initializeSingleThreaded().use {
            val simpleMath = Class.forName(componentRuntimeClass)
                .getDeclaredConstructor()
                .newInstance()
            val result = simpleMath.javaClass
                .getMethod("add", Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
                .invoke(simpleMath, firstNumber, secondNumber) as Double
            SimpleMathComponentResult("$firstNumber + $secondNumber", result)
        }
}
