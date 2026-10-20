package io.github.composefluent.winrt.projections.generator

/**
 * Public declaration/mapping policy version, shared by reference and implementation consumers.
 * Corresponds to CsWinRT's projection API boundary, not the generator executable version.
 * SDK API additions are described by metadata and do not by themselves change this version.
 */
const val KOTLIN_PROJECTION_SHAPE_VERSION: Int = 1
