package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreMergedTest {
    @Test
    fun `setMergedContacts stores the working set`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        val merged = listOf(Contact(id = "m1", source = Source.APPLE, name = ContactName(given = "M"), rawVCard = ""))
        store.setMergedContacts(merged)
        assertEquals(merged, store.state.value.mergedContacts)
    }
}
