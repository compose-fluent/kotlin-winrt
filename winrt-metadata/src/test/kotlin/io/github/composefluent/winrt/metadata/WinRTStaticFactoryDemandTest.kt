package io.github.composefluent.winrt.metadata

import org.junit.Assert.*
import org.junit.Test

class WinRTStaticFactoryDemandTest {
    @Test fun static_abi_role_does_not_hide_projected_or_factory_uses() {
        // CsWinRT code_writers.h: raw static objrefs are distinct from RCW interfaces.
        val statics = WinRTTypeDefinition("App", "IStatics", WinRTTypeKind.Interface, isExclusiveTo = true)
        val tools = WinRTTypeDefinition("App", "Tools", WinRTTypeKind.RuntimeClass,
            customAttributes = listOf(WinRTCustomAttributeDefinition("Windows.Foundation.Metadata.StaticAttribute",
                fixedArguments = listOf(WinRTCustomAttributeValue.TypeValue(statics.qualifiedName)))),
            activation = WinRTActivationShape(staticInterfaceNames = listOf(statics.qualifiedName),
                factories = listOf(WinRTAttributedFactoryShape(statics.qualifiedName, WinRTAttributedFactoryKind.Static))))
        fun model(owner: WinRTTypeDefinition = tools) = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(statics, owner))))
        assertEquals(setOf(statics.qualifiedName), model().staticFactoryInterfacesWithoutProjectedUses(setOf(tools.qualifiedName)))
        assertTrue(model().staticFactoryInterfacesWithoutProjectedUses(setOf(statics.qualifiedName)).isEmpty())
        assertTrue(model(tools.copy(methods = listOf(WinRTMethodDefinition("GetInterface", statics.qualifiedName))))
            .staticFactoryInterfacesWithoutProjectedUses(emptySet()).isEmpty())
        assertTrue(model(tools.copy(activation = tools.activation.copy(activatableFactoryInterfaceName = statics.qualifiedName)))
            .staticFactoryInterfacesWithoutProjectedUses(emptySet()).isEmpty())
        val selfReturningStatics = statics.copy(methods = listOf(WinRTMethodDefinition("GetSelf", statics.qualifiedName)))
        assertTrue(WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(selfReturningStatics, tools))))
            .staticFactoryInterfacesWithoutProjectedUses(emptySet()).isEmpty())
        assertEquals(listOf(statics.qualifiedName), tools.activation.staticInterfaceNames)
    }
}
