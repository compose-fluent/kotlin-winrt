package io.github.composefluent.winrt.compiler.callsites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class TestProjectionCallSite(
    val hResult: WinRTProjectionCallSiteHResultPolicy = WinRTProjectionCallSiteHResultPolicy.CHECK,
    val result: WinRTProjectionCallSiteResultKind = WinRTProjectionCallSiteResultKind.INFER,
    val returnAbiType: String = "",
)

private class TestRuntimeCallSiteOwner {
    @TestProjectionCallSite
    fun getInt32(reference: Any, slot: Int): Int = 0

    @TestProjectionCallSite(returnAbiType = "kotlin.UInt")
    fun getUInt32(reference: Any, slot: Int): UInt = 0u

    fun ordinaryMethod(): Unit = Unit
}

private class DuplicateDescriptorCallSiteOwner {
    @TestProjectionCallSite
    fun first(reference: Any, slot: Int): Int = 0

    @TestProjectionCallSite
    fun second(reference: Any, slot: Int): Int = 0
}

class WinRTProjectionCallSiteCatalogTest {
    @Test
    fun binary_annotations_form_a_sorted_structured_catalog() {
        val catalog = WinRTProjectionCallSiteCatalog.fromJvmClass(
            owner = TestRuntimeCallSiteOwner::class.java,
            annotationFqName = TestProjectionCallSite::class.java.name,
        )

        assertEquals(listOf("getInt32", "getUInt32"), catalog.declarations.map { it.functionName }.sorted())
        assertEquals(
            "getInt32",
            catalog[
                WinRTProjectionCallSiteCatalogKey(
                    metadata = WinRTProjectionCallSiteMetadata(),
                    jvmMethodDescriptor = "(Ljava/lang/Object;I)I",
                ),
            ]?.functionName,
        )
        assertEquals(
            "getUInt32",
            catalog[
                WinRTProjectionCallSiteCatalogKey(
                    metadata = WinRTProjectionCallSiteMetadata(returnAbiType = "kotlin.UInt"),
                    jvmMethodDescriptor = "(Ljava/lang/Object;I)I",
                ),
            ]?.functionName,
        )
    }

    @Test
    fun duplicate_structured_call_site_ownership_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            WinRTProjectionCallSiteCatalog.fromJvmClass(
                owner = DuplicateDescriptorCallSiteOwner::class.java,
                annotationFqName = TestProjectionCallSite::class.java.name,
            )
        }
    }
}
