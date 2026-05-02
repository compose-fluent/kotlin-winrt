package io.github.composefluent.winrt.metadata

import java.nio.file.Path

data class WinRtMetadataAuxiliaryTableInventory(
    val files: List<WinRtMetadataFileAuxiliaryTableInventory>,
) {
    val tables: List<WinRtMetadataAuxiliaryTableDescriptor>
        get() = files.flatMap(WinRtMetadataFileAuxiliaryTableInventory::tables)

    fun table(tableName: String): List<WinRtMetadataAuxiliaryTableDescriptor> =
        tables.filter { it.tableName == tableName }
}

data class WinRtMetadataFileAuxiliaryTableInventory(
    val file: Path,
    val tables: List<WinRtMetadataAuxiliaryTableDescriptor>,
)

data class WinRtMetadataAuxiliaryTableDescriptor(
    val tableId: Int,
    val tableName: String,
    val rowCount: Int,
    val rowSize: Int,
    val modeled: Boolean,
)
