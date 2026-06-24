package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreNavigationTest {
    private fun storeWithContacts(n: Int): AppStore {
        val store =
            AppStore(parse = { _, _ ->
                (1..n).map {
                    Contact(id = "c$it", source = Source.APPLE, name = ContactName(given = "C$it"), rawVCard = "")
                }
            })
        if (n > 0) kotlinx.coroutines.runBlocking { store.importFile("f.vcf", Source.APPLE) }
        return store
    }

    @Test
    fun `next is blocked on import with no contacts`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        store.next()
        assertEquals(Screen.IMPORT, store.state.value.screen)
    }

    @Test
    fun `next advances and back returns once there are contacts`() {
        val store = storeWithContacts(2)
        store.next()
        assertEquals(Screen.MERGE, store.state.value.screen)
        store.back()
        assertEquals(Screen.IMPORT, store.state.value.screen)
    }

    @Test
    fun `goTo jumps directly and back from import is a no-op`() {
        val store = storeWithContacts(1)
        store.goTo(Screen.EXPORT)
        assertEquals(Screen.EXPORT, store.state.value.screen)
        store.goTo(Screen.IMPORT)
        store.back()
        assertEquals(Screen.IMPORT, store.state.value.screen)
    }
}
