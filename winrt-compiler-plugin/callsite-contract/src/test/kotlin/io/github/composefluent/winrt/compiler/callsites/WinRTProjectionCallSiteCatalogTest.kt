package io.github.composefluent.winrt.compiler.callsites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val TEST_INT32_DESCRIPTOR =
    "v1|COM_OBJECT_REFERENCE|PARAMETER|-1|CHECK|SCALAR_OUT|INT32~NONE~0~0~0|-"
private const val TEST_UINT32_DESCRIPTOR =
    "v1|COM_OBJECT_REFERENCE|PARAMETER|-1|CHECK|SCALAR_OUT|UINT32~NONE~0~0~0|-"

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class TestProjectionCallSite(
    val descriptor: String,
)

private class TestRuntimeCallSiteOwner {
    @TestProjectionCallSite(TEST_INT32_DESCRIPTOR)
    fun getInt32(): Int = 0

    @TestProjectionCallSite(TEST_UINT32_DESCRIPTOR)
    fun getUInt32(): UInt = 0u

    fun ordinaryMethod(): Unit = Unit
}

private class DuplicateDescriptorCallSiteOwner {
    @TestProjectionCallSite(TEST_INT32_DESCRIPTOR)
    fun first(): Int = 0

    @TestProjectionCallSite(TEST_INT32_DESCRIPTOR)
    fun second(): Int = 0
}

class WinRTProjectionCallSiteCatalogTest {
    @Test
    fun binary_annotations_form_a_sorted_descriptor_catalog() {
        val catalog = WinRTProjectionCallSiteCatalog.fromJvmClass(
            owner = TestRuntimeCallSiteOwner::class.java,
            annotationFqName = TestProjectionCallSite::class.java.name,
        )

        assertEquals(listOf("getInt32", "getUInt32"), catalog.declarations.map { it.functionName }.sorted())
        assertEquals(
            "getInt32",
            catalog[WinRTProjectionCallSiteDescriptor.parse(TEST_INT32_DESCRIPTOR)]?.functionName,
        )
        assertEquals(
            "getUInt32",
            catalog[WinRTProjectionCallSiteDescriptor.parse(TEST_UINT32_DESCRIPTOR)]?.functionName,
        )
    }

    @Test
    fun duplicate_descriptor_ownership_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            WinRTProjectionCallSiteCatalog.fromJvmClass(
                owner = DuplicateDescriptorCallSiteOwner::class.java,
                annotationFqName = TestProjectionCallSite::class.java.name,
            )
        }
    }
}
