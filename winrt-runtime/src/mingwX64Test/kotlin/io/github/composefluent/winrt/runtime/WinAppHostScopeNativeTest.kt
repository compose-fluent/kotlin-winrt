@file:OptIn(kotlin.native.concurrent.ObsoleteWorkersApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test

class WinAppHostScopeNativeTest {
    @Test
    fun application_model_selects_the_entry_apartment_and_preserves_conflicts() {
        val worker = Worker.start()
        try {
            worker.execute(TransferMode.SAFE, { Unit }) {
                verifyApplicationHostApartments()
            }.result
        } finally {
            worker.requestTermination().result
        }
    }
}
