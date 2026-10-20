package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.XamlFramework
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

class KotlinProjectionInputContractTest {
    private val model = WinRTMetadataModel(emptyList())
    private val context = WinRTMetadataProjectionContext(sources = listOf(WinRTMetadataSource.path(Path.of("first.winmd"))))
    private fun contract(
        context: WinRTMetadataProjectionContext = this.context,
        model: WinRTMetadataModel = this.model,
        sourceModel: WinRTMetadataModel = this.model,
        owner: String = "sample.jar",
        suppressed: Set<String> = emptySet(),
    ) = KotlinProjectionInputContract.create(model, sourceModel, context, suppressed, emptySet(), owner, true)

    // CsWinRT reference and implementation generation share metadata and declaration policy;
    // locations/diagnostics are not public shape, while ownership and ABI metadata are inputs.
    @Test fun relocation_and_diagnostics_do_not_change_the_contract() {
        val relocated = context.copy(sources = listOf(WinRTMetadataSource.path(Path.of("other", "second.winmd"))),
            outputFolder = Path.of("output"), verbose = true)
        assertEquals(contract().descriptor, contract(relocated).descriptor)
        assertEquals(contract().fingerprint, contract(relocated).fingerprint)
        assertFalse(contract().descriptor.contains("first.winmd"))
    }

    @Test fun unordered_selections_are_canonical_and_separate_entries_cannot_alias() {
        assertEquals(contract(context.copy(include = linkedSetOf("A", "B"))).fingerprint,
            contract(context.copy(include = linkedSetOf("B", "A"))).fingerprint)
        assertNotEquals(contract(context.copy(include = setOf("A", "B"))).fingerprint,
            contract(context.copy(include = setOf("A, B"))).fingerprint)
    }

    @Test fun declaration_policies_ownership_and_supplemental_models_invalidate() {
        val original = contract().fingerprint
        for (changed in listOf(context.copy(sources = emptyList()), context.copy(component = true),
            context.copy(internal = true), context.copy(xamlFramework = XamlFramework.UWP),
            context.copy(inaccessibleDependencyTypes = setOf("Sample.IHidden")))) {
            assertNotEquals(original, contract(changed).fingerprint)
        }
        assertNotEquals(original, contract(owner = "other.jar").fingerprint)
        assertNotEquals(original, contract(suppressed = setOf("Sample.Widget")).fingerprint)
        val changedModel = WinRTMetadataModel(listOf(WinRTNamespace("Sample", listOf(
            WinRTTypeDefinition(namespace = "Sample", name = "Widget", kind = WinRTTypeKind.RuntimeClass),
        ))))
        assertNotEquals(original, contract(model = changedModel).fingerprint)
        assertNotEquals(original, contract(sourceModel = changedModel).fingerprint)
    }
}
