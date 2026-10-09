package io.github.composefluent.winrt.ide.nuget

/** NuGet's four-part System.Version core and SemVer prerelease ordering. */
internal object WinRTNuGetVersion {
    private data class Version(val numbers: List<java.math.BigInteger>, val labels: List<String>)
    private fun parse(value: String): Version {
        require(Regex("[0-9]+(?:\\.[0-9]+){0,3}(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?").matches(value)) { "Select an exact NuGet version." }
        val text = value.substringBefore('+')
        return Version(text.substringBefore('-').split('.').map { it.toBigInteger() }.let { it + List(4 - it.size) { java.math.BigInteger.ZERO } },
            text.substringAfter('-', "").split('.').filter(String::isNotEmpty))
    }
    fun compare(a: String, b: String): Int {
        val left = parse(a); val right = parse(b)
        left.numbers.zip(right.numbers).forEach { (x, y) -> if (x != y) return x.compareTo(y) }
        if (left.labels.isEmpty() || right.labels.isEmpty()) return when {
            left.labels.isEmpty() && right.labels.isEmpty() -> 0
            left.labels.isEmpty() -> 1
            else -> -1
        }
        left.labels.zip(right.labels).forEach { (x, y) ->
            val xn = x.toBigIntegerOrNull(); val yn = y.toBigIntegerOrNull()
            val result = when { xn != null && yn != null -> xn.compareTo(yn); xn != null -> -1; yn != null -> 1; else -> x.compareTo(y, true) }
            if (result != 0) return result
        }
        return left.labels.size.compareTo(right.labels.size)
    }
    fun normalized(value: String): String {
        val parsed = parse(value)
        val core = parsed.numbers.take(if (parsed.numbers.last().signum() == 0) 3 else 4).joinToString(".")
        return core + if (parsed.labels.isEmpty()) "" else "-" + parsed.labels.joinToString(".")
    }
}
