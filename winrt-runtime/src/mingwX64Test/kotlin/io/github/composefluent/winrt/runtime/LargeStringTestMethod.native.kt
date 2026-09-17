@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.rawValue
import kotlinx.cinterop.staticCFunction

internal actual fun largeStringTestMethod() = WinRTInspectableMethodDefinition(
    ComMethodSignature.of(*Array(11) { ComAbiValueKind.Pointer }),
    abiEntryPoint = RawAddress(staticCFunction(::receiveManyStrings).rawValue.toLong()),
)

// A static test entry avoids the runtime compatibility callback's seven-word limit.
@Suppress("UNCHECKED_CAST")
private fun receiveManyStrings(self: Long, a: Long, b: Long, c: Long, d: Long, e: Long,
    f: Long, g: Long, h: Long, i: Long, j: Long, k: Long): Int = try {
    val handler = winRTProjectionInboundManagedValue(self) as (List<Any?>) -> Int
    handler(listOf(a, b, c, d, e, f, g, h, i, j, k).map(::RawAddress))
} catch (error: Throwable) {
    winRTProjectionInboundFailure(error)
}
