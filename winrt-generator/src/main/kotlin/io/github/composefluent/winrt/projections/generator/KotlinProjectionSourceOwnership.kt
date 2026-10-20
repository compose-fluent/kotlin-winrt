package io.github.composefluent.winrt.projections.generator

/** The same source ownership rule is used by full, reference and demand compilations.
 * COM interop source additions without the generated-projection marker are business
 * overlays too; authoring overlays opt out explicitly even when they have that marker. */
fun isProjectionImplementationSourceHeader(header: List<String>): Boolean =
    header.any { it.contains("\"KOTLIN_WINRT_GENERATED\"") } &&
        header.none { it.contains("KOTLIN_WINRT_BUSINESS_OVERLAY") }
