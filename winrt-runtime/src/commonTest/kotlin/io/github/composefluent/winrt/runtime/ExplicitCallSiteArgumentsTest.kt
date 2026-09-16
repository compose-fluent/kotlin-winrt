package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class ExplicitCallSiteArgumentsTest {
    @Test
    fun explicit_arguments_allow_renaming_and_intervening_statements() {
        val iid = Guid("feb4b8b3-edcd-4572-ae11-5cd4d9d21368")
        val method = WinRTInspectableMethodDefinition(
            ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
            handler = { args ->
                PlatformAbi.writeInt32(args[1] as RawAddress, (args[0] as Int) + 1)
                0
            },
        )
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
            host.createPrimaryReference().use { reference ->
                val renamedReceiver = reference
                val arbitrarySlot = 6
                val input = 41
                var unrelated = 0
                unrelated += 1
                @WinRTProjectionCallSite
                val renamedResult: Int = winRTProjectionCallSiteArguments(renamedReceiver, arbitrarySlot, input)
                assertEquals(42, renamedResult)
                assertEquals(1, unrelated)

                // Existing generated artifacts remain consumable while producers migrate.
                val __winrtCallSiteArgument0 = reference
                val __winrtCallSiteArgument1 = 6
                val __winrtCallSiteArgument2 = 41
                @WinRTProjectionCallSite
                val __winrtCallSiteResult: Int = TODO("legacy call-site marker")
                assertEquals(42, __winrtCallSiteResult)
            }
        }
    }
}
