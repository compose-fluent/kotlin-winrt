package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha1Test {
    @Test
    fun platform_hashes_match_reference_across_blocks_and_prefix_splits() {
        // CsWinRT GuidGenerator.CreateIIDForGenericType hashes the namespace prefix
        // followed by UTF-8 signature bytes. Expected digests come from .NET SHA1.HashData.
        val cases = listOf(
            0 to "da39a3ee5e6b4b0d3255bfef95601890afd80709",
            3 to "d2df16b976a43628c63ea246436fccf6d7635d09",
            55 to "749bbefb28edc4638b28b2b9a9e03ab9a4032b90",
            56 to "a5b6e9c29d201c774753ff8e7fb64931656f5e63",
            63 to "d1a454409359fc372b4d22b3cea6488d6ba1be00",
            64 to "39a0d8b645ad85f1f976731ed112ac9455e28b78",
            65 to "d0c96e18890114a14716e9686528d2e3fdba8d9e",
            127 to "bebc42d2d3d1e5fb8ad8895c2dcef2d68a6c279a",
            128 to "0060f2a7e34b6e4d459f560197ef93243732a400",
            129 to "3a16082d1bf09b604907ec6908b9893ca3e937c0",
            1024 to "b66786cde756750241d1f0eab86ce6f81855b017",
        )
        for ((size, expected) in cases) {
            val bytes = ByteArray(size) { (it * 31 + 7).toByte() }
            for (split in setOf(0, minOf(16, size), size / 2, size)) {
                assertEquals(expected,
                    Sha1.digest(bytes.copyOfRange(0, split), bytes.copyOfRange(split, size)).toHexString(),
                    "size=$size, split=$split")
            }
        }
    }
}
