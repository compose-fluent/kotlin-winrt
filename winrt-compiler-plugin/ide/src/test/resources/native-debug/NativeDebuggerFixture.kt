@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(kotlin.native.internal.InternalForKotlinNative::class)
import kotlin.native.internal.GCUnsafeCall

@GCUnsafeCall("kotlin_winrt_debug_anchor") external fun retainDebugApi()
data class DebugRecord(val text: String, val count: Int)
fun main() {
    retainDebugApi()
    val record = DebugRecord("native fields", 42)
    val numbers = IntArray(125) { it }
    println(record) // BREAKPOINT
    val next = increment(41) // STEP
    println(next)
    println(numbers[124])
}
fun increment(value: Int): Int {
    return value + 1
}
