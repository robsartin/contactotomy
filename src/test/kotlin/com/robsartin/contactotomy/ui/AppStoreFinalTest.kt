package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreFinalTest {
    @Test
    fun `setFinalContacts stores the post-deletion set`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        val final = listOf(contact("m1", given = "M"))
        store.setFinalContacts(final)
        assertEquals(final, store.state.value.finalContacts)
    }
}
