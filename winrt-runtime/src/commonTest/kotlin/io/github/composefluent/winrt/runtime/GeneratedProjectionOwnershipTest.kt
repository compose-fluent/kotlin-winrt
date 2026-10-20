package io.github.composefluent.winrt.runtime

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeneratedProjectionOwnershipTest {
    private class First
    private class Second

    @BeforeTest fun prepare() = WinRTTypeRegistry.clearForTests()
    @AfterTest fun cleanup() = WinRTTypeRegistry.clearForTests()

    // CsWinRT 3 EmitTypeForwards + WindowsRuntimeMetadataTypeMapGroup: one implementation
    // identity per generated metadata name; no string-name equivalence across class loaders.
    @Test fun repeat_registration_is_allowed_but_a_different_type_cannot_replace_it() {
        registerGeneratedProjectionTypeIndex(First::class, "Ownership.Widget", "RuntimeClass", "")
        registerGeneratedProjectionTypeIndex(First::class, "Ownership.Widget", "RuntimeClass", "")
        assertFailsWith<IllegalArgumentException> {
            registerGeneratedProjectionTypeIndex(Second::class, "Ownership.Widget", "RuntimeClass", "")
        }
        assertEquals(First::class, WinRTTypeRegistry.findByName("Ownership.Widget")?.kClass)
    }

    @Test fun legacy_iid_can_be_enriched_but_conflicting_metadata_is_rejected() {
        val iid = "00000000-0000-0000-c000-000000000046"
        registerGeneratedProjectionTypeIndex(First::class, "Ownership.IWidget", "Interface", "")
        registerGeneratedProjectionTypeIndex(First::class, "Ownership.IWidget", "Interface", "", iid)
        registerGeneratedProjectionTypeIndex(First::class, "Ownership.IWidget", "Interface", "")
        assertEquals(Guid(iid), WinRTTypeRegistry.findByName("Ownership.IWidget")?.iid)
        assertFailsWith<IllegalArgumentException> {
            registerGeneratedProjectionTypeIndex(First::class, "Ownership.IWidget", "Interface", "", "00000000-0000-0000-0000-000000000001")
        }
        assertFailsWith<IllegalArgumentException> {
            registerGeneratedProjectionTypeIndex(First::class, "Ownership.IWidget", "RuntimeClass", "")
        }
    }
}
