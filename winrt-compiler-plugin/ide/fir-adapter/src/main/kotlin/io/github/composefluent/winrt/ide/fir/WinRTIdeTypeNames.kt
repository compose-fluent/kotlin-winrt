package io.github.composefluent.winrt.ide.fir

import io.github.composefluent.winrt.compiler.xaml.xamlProjectionClassId
import io.github.composefluent.winrt.compiler.xaml.xamlTypeClassId

/** Keeps editor navigation on the compiler owner's projection naming contract. */
object WinRTIdeTypeNames {
    fun projection(name: String): String = xamlProjectionClassId(name).asSingleFqName().asString()
    fun propertyType(name: String): String = xamlTypeClassId(name).asSingleFqName().asString()
}
