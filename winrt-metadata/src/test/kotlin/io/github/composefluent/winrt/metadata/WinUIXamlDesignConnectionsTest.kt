package io.github.composefluent.winrt.metadata

import org.junit.Assert.*
import org.junit.Test

class WinUIXamlDesignConnectionsTest {
    @Test fun source_locations_match_unnamed_multiline_elements_and_property_collections() {
        val text = "<Grid>\r\n<!-- hidden <Button/> -->\r\n<Grid.Resources><SolidColorBrush Color='Red'/></Grid.Resources>\r\n<Button\r\n Content='&gt;'/><TextBlock Text='next'/></Grid>"
        val button = WinUIXamlConnectionDeclaration(1, "Button", null, WinUIXamlSourceLocation(4, 2), emptyList())
        val label = WinUIXamlConnectionDeclaration(2, "TextBlock", "Label", WinUIXamlSourceLocation(5, 18), emptyList())
        val paths = WinUIXamlDesignConnections.paths(text, listOf(button, label))
        assertEquals(button, paths[listOf(1)])
        assertEquals(label, paths[listOf(2)])
    }
    @Test fun diagnostic_location_changes_do_not_invalidate_the_generated_contract() {
        val connection = WinUIXamlConnectionDeclaration(1, "Button", "First", WinUIXamlSourceLocation(2, 3), emptyList())
        assertEquals(WinUIXamlDeclarations.designConnectionSignature(connection),
            WinUIXamlDeclarations.designConnectionSignature(connection.copy(location = WinUIXamlSourceLocation(20, 30))))
        assertNotEquals(WinUIXamlDeclarations.designConnectionSignature(connection),
            WinUIXamlDeclarations.designConnectionSignature(connection.copy(fieldName = "Other")))
    }
}
