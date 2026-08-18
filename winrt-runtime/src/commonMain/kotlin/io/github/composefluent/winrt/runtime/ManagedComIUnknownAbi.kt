package io.github.composefluent.winrt.runtime

internal expect fun allocateWin64ExecutableCode(
    code: ByteArray,
    description: String,
): RawAddress

internal fun buildManagedComQueryInterfaceStub(fallbackAddress: Long): ByteArray {
    val tableSlotOffset = managedComQueryInterfaceTableSlot * Long.SIZE_BYTES
    val counterSlotOffset = managedComReferenceCounterSlot * Long.SIZE_BYTES
    val tableHeaderSize = managedComQueryInterfaceTableHeaderWordCount * Long.SIZE_BYTES
    val tableForwardTargetOffset = managedComQueryInterfaceTableForwardTargetWord * Long.SIZE_BYTES
    val tableEntrySize = managedComQueryInterfaceTableEntryWordCount * Long.SIZE_BYTES
    require(tableSlotOffset in 0..Byte.MAX_VALUE)
    require(counterSlotOffset in 0..Byte.MAX_VALUE)
    require(tableHeaderSize in 0..Byte.MAX_VALUE)
    require(tableForwardTargetOffset in 0..Byte.MAX_VALUE)
    require(tableEntrySize in 0..Byte.MAX_VALUE)

    return Win64ManagedComCodeBuilder().apply {
        emit(0x48, 0x85, 0xC9) // test rcx, rcx
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x48, 0x85, 0xD2) // test rdx, rdx
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x4D, 0x85, 0xC0) // test r8, r8
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x4C, 0x8B, 0x49, tableSlotOffset) // mov r9, [rcx + tableSlotOffset]
        emit(0x4D, 0x85, 0xC9) // test r9, r9
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x4D, 0x8B, 0x59, tableForwardTargetOffset) // mov r11, [r9 + forward target offset]
        emit(0x45, 0x8B, 0x11) // mov r10d, [r9]
        emit(0x45, 0x85, 0xD2) // test r10d, r10d
        jumpIf(0x8E, ManagedComCodeLabel.Forward)
        emit(0x49, 0x83, 0xC1, tableHeaderSize) // add r9, tableHeaderSize

        mark(ManagedComCodeLabel.Scan)
        emit(0x48, 0x8B, 0x02) // mov rax, [rdx]
        emit(0x49, 0x39, 0x01) // cmp [r9], rax
        jumpIf(0x85, ManagedComCodeLabel.NextEntry)
        emit(0x48, 0x8B, 0x42, Long.SIZE_BYTES) // mov rax, [rdx + 8]
        emit(0x49, 0x39, 0x41, Long.SIZE_BYTES) // cmp [r9 + 8], rax
        jumpIf(0x84, ManagedComCodeLabel.Found)

        mark(ManagedComCodeLabel.NextEntry)
        emit(0x49, 0x83, 0xC1, tableEntrySize) // add r9, tableEntrySize
        emit(0x41, 0xFF, 0xCA) // dec r10d
        jumpIf(0x85, ManagedComCodeLabel.Scan)
        jump(ManagedComCodeLabel.Forward)

        mark(ManagedComCodeLabel.Found)
        emit(0x4C, 0x8B, 0x59, counterSlotOffset) // mov r11, [rcx + counter slot]
        emit(0x4D, 0x85, 0xDB) // test r11, r11
        jumpIf(0x84, ManagedComCodeLabel.Fallback)

        mark(ManagedComCodeLabel.ReferenceLoop)
        emit(0x49, 0x8B, 0x03) // mov rax, [r11]
        emitManagedComReferenceCountMinimumCheck(managedComAddRefFastPathMinimumCount)
        emit(0x3D)
        emitInt32(Int.MAX_VALUE) // cmp low reference count, Int.MAX_VALUE
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x4C, 0x8D, 0x50, 0x01) // lea r10, [rax + 1]
        emit(0xF0, 0x4D, 0x0F, 0xB1, 0x13) // lock cmpxchg [r11], r10
        jumpIf(0x85, ManagedComCodeLabel.ReferenceLoop)
        emit(0x49, 0x8B, 0x41, Long.SIZE_BYTES * 2) // mov rax, [r9 + target offset]
        emit(0x49, 0x89, 0x00) // mov [r8], rax
        emit(0x31, 0xC0) // xor eax, eax (S_OK)
        emit(0xC3) // ret

        mark(ManagedComCodeLabel.Forward)
        emit(0x4D, 0x85, 0xDB) // test r11, r11
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x49, 0x8B, 0x03) // mov rax, [r11]
        emit(0x48, 0x85, 0xC0) // test rax, rax
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x48, 0x8B, 0x00) // mov rax, [rax] (QueryInterface)
        emit(0x48, 0x85, 0xC0) // test rax, rax
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x4C, 0x89, 0xD9) // mov rcx, r11
        emit(0xFF, 0xE0) // jmp rax

        mark(ManagedComCodeLabel.Fallback)
        emit(0x48, 0xB8) // mov rax, fallbackAddress
        emitInt64(fallbackAddress)
        emit(0xFF, 0xE0) // jmp rax
    }.build()
}

internal fun buildManagedComReferenceCountStub(
    minimumCurrentCount: Int,
    delta: Int,
    fallbackAddress: Long,
): ByteArray {
    val counterSlotOffset = managedComReferenceCounterSlot * Long.SIZE_BYTES
    require(counterSlotOffset in 0..Byte.MAX_VALUE)
    require(minimumCurrentCount in 0..Byte.MAX_VALUE)
    require(delta in Byte.MIN_VALUE..Byte.MAX_VALUE)

    return Win64ManagedComCodeBuilder().apply {
        emit(0x48, 0x85, 0xC9) // test rcx, rcx
        jumpIf(0x84, ManagedComCodeLabel.Fallback)
        emit(0x48, 0x8B, 0x51, counterSlotOffset) // mov rdx, [rcx + counter slot]
        emit(0x48, 0x85, 0xD2) // test rdx, rdx
        jumpIf(0x84, ManagedComCodeLabel.Fallback)

        mark(ManagedComCodeLabel.ReferenceLoop)
        emit(0x48, 0x8B, 0x02) // mov rax, [rdx]
        emitManagedComReferenceCountMinimumCheck(minimumCurrentCount)
        if (delta > 0) {
            emit(0x3D)
            emitInt32(Int.MAX_VALUE) // cmp low reference count, Int.MAX_VALUE
            jumpIf(0x84, ManagedComCodeLabel.Fallback)
        }
        emit(0x4C, 0x8D, 0x40, delta) // lea r8, [rax + delta]
        emit(0xF0, 0x4C, 0x0F, 0xB1, 0x02) // lock cmpxchg [rdx], r8
        jumpIf(0x85, ManagedComCodeLabel.ReferenceLoop)
        emit(0x44, 0x89, 0xC0) // mov eax, r8d
        emit(0xC3) // ret

        mark(ManagedComCodeLabel.Fallback)
        emit(0x48, 0xB8) // mov rax, fallbackAddress
        emitInt64(fallbackAddress)
        emit(0xFF, 0xE0) // jmp rax
    }.build()
}

private enum class ManagedComCodeLabel {
    Scan,
    NextEntry,
    Found,
    ReferenceLoop,
    Forward,
    Fallback,
}

private class Win64ManagedComCodeBuilder {
    private val code = mutableListOf<Byte>()
    private val labels = mutableMapOf<ManagedComCodeLabel, Int>()
    private val relativePatches = mutableListOf<Pair<Int, ManagedComCodeLabel>>()

    fun emit(vararg values: Int) {
        values.forEach { value -> code.add(value.toByte()) }
    }

    fun emitInt32(value: Int) {
        repeat(Int.SIZE_BYTES) { shift ->
            code.add((value ushr (shift * Byte.SIZE_BITS)).toByte())
        }
    }

    fun emitInt64(value: Long) {
        repeat(Long.SIZE_BYTES) { shift ->
            code.add((value ushr (shift * Byte.SIZE_BITS)).toByte())
        }
    }

    fun emitManagedComReferenceCountMinimumCheck(minimumCurrentCount: Int) {
        require(minimumCurrentCount in 1..Byte.MAX_VALUE)
        // A borrow-ready cache state relies on the projected argument as its managed root, so
        // its low COM count may be one lower than an ordinary pinned state. The high marker makes
        // the following full-width comparison accept only that explicitly borrow-ready state.
        emit(0x83, 0xF8, minimumCurrentCount - 1) // cmp eax, borrow-ready minimum count
        jumpIf(0x8C, ManagedComCodeLabel.Fallback)
        emit(0x48, 0x83, 0xF8, minimumCurrentCount) // cmp rax, ordinary minimum count
        jumpIf(0x8C, ManagedComCodeLabel.Fallback)
    }

    fun mark(label: ManagedComCodeLabel) {
        check(labels.put(label, code.size) == null) { "Duplicate managed COM code label $label." }
    }

    fun jumpIf(conditionOpcode: Int, label: ManagedComCodeLabel) {
        emit(0x0F, conditionOpcode)
        emitRelativePatch(label)
    }

    fun jump(label: ManagedComCodeLabel) {
        emit(0xE9)
        emitRelativePatch(label)
    }

    fun build(): ByteArray {
        val result = code.toByteArray()
        relativePatches.forEach { (offset, label) ->
            val target = labels[label] ?: error("Missing managed COM code label $label.")
            val displacement = target - (offset + Int.SIZE_BYTES)
            repeat(Int.SIZE_BYTES) { shift ->
                result[offset + shift] = (displacement ushr (shift * Byte.SIZE_BITS)).toByte()
            }
        }
        return result
    }

    private fun emitRelativePatch(label: ManagedComCodeLabel) {
        relativePatches += code.size to label
        emitInt32(0)
    }
}
