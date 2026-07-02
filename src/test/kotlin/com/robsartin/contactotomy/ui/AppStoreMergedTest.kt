package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreMergedTest {
    @Test
    fun `setReviewedContacts stores the working set`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        val reviewed = listOf(Contact(id = "r1", source = Source.APPLE, name = ContactName(given = "R"), rawVCard = ""))
        store.setReviewedContacts(reviewed)
        assertEquals(reviewed, store.state.value.reviewedContacts)
    }
}
