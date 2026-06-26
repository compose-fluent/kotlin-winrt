package io.github.composefluent.winrt.metadata

import java.nio.file.Path

data class WinRTMetadataAuxiliaryTableInventory(
    val files: List<WinRTMetadataFileAuxiliaryTableInventory>,
) {
    val tables: List<WinRTMetadataAuxiliaryTableDescriptor>
        get() = files.flatMap(WinRTMetadataFileAuxiliaryTableInventory::tables)

    fun table(tableName: String): List<WinRTMetadataAuxiliaryTableDescriptor> =
        tables.filter { it.tableName == tableName }
}

data class WinRTMetadataFileAuxiliaryTableInventory(
    val file: Path,
    val tables: List<WinRTMetadataAuxiliaryTableDescriptor>,
)

data class WinRTMetadataAuxiliaryTableDescriptor(
    val tableId: Int,
    val tableName: String,
    val rowCount: Int,
    val rowSize: Int,
    val modeled: Boolean,
)
