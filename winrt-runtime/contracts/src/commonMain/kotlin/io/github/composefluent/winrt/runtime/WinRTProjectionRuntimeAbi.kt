package io.github.composefluent.winrt.runtime

/**
 * Link contract used by generated projections on both JVM and mingwX64.
 *
 * Like CsWinRT's runtime assembly ABI, this is independent of the SDK and package version.
 * Increment for incompatible changes to symbols or ownership semantics consumed by generated
 * code. Implementation-only fixes do not change it. This does not version Kotlin's own ABI.
 */
const val WINRT_PROJECTION_RUNTIME_ABI_VERSION: Int = 1
