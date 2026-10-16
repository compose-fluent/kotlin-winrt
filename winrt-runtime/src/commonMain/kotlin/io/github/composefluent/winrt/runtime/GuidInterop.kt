package io.github.composefluent.winrt.runtime

fun Guid.writeTo(destination: RawAddress) {
    PlatformAbi.writeGuid(destination, this)
}

fun Guid.Companion.readFrom(source: RawAddress): Guid =
    PlatformAbi.readGuid(source)
