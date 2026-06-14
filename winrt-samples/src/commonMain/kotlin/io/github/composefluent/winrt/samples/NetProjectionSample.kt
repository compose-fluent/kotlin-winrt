package io.github.composefluent.winrt.samples

import simplemathcomponent.SimpleMath
import io.github.composefluent.winrt.runtime.RuntimeScope

data class SimpleMathComponentResult(
    val expression: String,
    val value: Double,
)

object NetProjectionSample {
    fun add(firstNumber: Double = 5.5, secondNumber: Double = 6.5): SimpleMathComponentResult =
        RuntimeScope.initializeSingleThreaded().use {
            val result = SimpleMath().add(firstNumber, secondNumber)
            SimpleMathComponentResult("$firstNumber + $secondNumber", result)
        }
}
