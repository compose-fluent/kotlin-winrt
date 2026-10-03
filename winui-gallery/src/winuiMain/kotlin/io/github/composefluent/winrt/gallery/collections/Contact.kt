// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.runtime.await
import windows.foundation.Uri
import windows.storage.*

internal class Contact(val FirstName: String, val LastName: String, val Company: String) {
    val Name: String get() = "$FirstName $LastName"
    override fun toString(): String = "$Name, $Company"
    companion object {
        suspend fun GetContactsAsync(): List<Contact> {
            val file = StorageFile.getFileFromApplicationUriAsync(Uri("ms-appx:///Assets/SampleMedia/Contacts.txt")).await()
            return FileIO.readLinesAsync(file).await().chunked(3).filter { it.size == 3 }.map { Contact(it[0], it[1], it[2]) }
        }
    }
}

internal class GroupInfoList(val Key: String, private val values: List<Contact>) : AbstractList<Contact>() {
    override val size: Int get() = values.size
    override fun get(index: Int): Contact = values[index]
    override fun toString(): String = "Group $Key"
}

internal class Message(val MsgText: String, val MsgDateTime: kotlin.time.Instant, var MsgAlignment: microsoft.ui.xaml.HorizontalAlignment) {
    override fun toString(): String = "$MsgDateTime $MsgText"
}
