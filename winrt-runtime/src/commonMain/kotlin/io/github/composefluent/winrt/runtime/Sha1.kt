package io.github.composefluent.winrt.runtime

internal object Sha1 {
    private const val BLOCK_SIZE = 64
    private const val DIGEST_SIZE = 20

    fun digest(input: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()

        val padded = pad(input)
        val words = IntArray(80)

        var blockOffset = 0
        while (blockOffset < padded.size) {
            for (index in 0 until 16) {
                val base = blockOffset + index * 4
                words[index] =
                    ((padded[base].toInt() and 0xFF) shl 24) or
                        ((padded[base + 1].toInt() and 0xFF) shl 16) or
                        ((padded[base + 2].toInt() and 0xFF) shl 8) or
                        (padded[base + 3].toInt() and 0xFF)
            }

            for (index in 16 until 80) {
                words[index] = rotateLeft(words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16], 1)
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4

            for (index in 0 until 80) {
                val (f, k) =
                    when (index) {
                        in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                        in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                        in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                        else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                    }

                val temp = rotateLeft(a, 5) + f + e + k + words[index]
                e = d
                d = c
                c = rotateLeft(b, 30)
                b = a
                a = temp
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            blockOffset += BLOCK_SIZE
        }

        return ByteArray(DIGEST_SIZE).also { output ->
            writeInt(h0, output, 0)
            writeInt(h1, output, 4)
            writeInt(h2, output, 8)
            writeInt(h3, output, 12)
            writeInt(h4, output, 16)
        }
    }

    private fun pad(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8L
        var paddedSize = input.size + 1 + 8
        while (paddedSize % BLOCK_SIZE != 0) {
            paddedSize += 1
        }

        return ByteArray(paddedSize).also { padded ->
            input.copyInto(padded)
            padded[input.size] = 0x80.toByte()
            for (index in 0 until 8) {
                padded[padded.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
            }
        }
    }

    private fun rotateLeft(
        value: Int,
        bitCount: Int,
    ): Int = (value shl bitCount) or (value ushr (32 - bitCount))

    private fun writeInt(
        value: Int,
        destination: ByteArray,
        offset: Int,
    ) {
        destination[offset] = (value ushr 24).toByte()
        destination[offset + 1] = (value ushr 16).toByte()
        destination[offset + 2] = (value ushr 8).toByte()
        destination[offset + 3] = value.toByte()
    }
}
