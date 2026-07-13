package io.github.composefluent.winrt.build

fun projectionArtifactVersion(
    metadataVersion: String,
    kotlinWinRTVersion: String,
): String =
    if (kotlinWinRTVersion.endsWith("-SNAPSHOT")) {
        "$metadataVersion-kotlin-winrt-$kotlinWinRTVersion"
    } else {
        metadataVersion
    }
