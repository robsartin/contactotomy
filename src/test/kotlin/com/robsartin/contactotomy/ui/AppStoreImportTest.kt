package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStoreImportTest {
    private fun fakeContacts(
        vararg ids: String,
        source: Source = Source.APPLE,
    ) = ids.map { Contact(id = it, source = source, name = ContactName(given = it), rawVCard = "") }

    @Test
    fun `import appends contacts and a file summary, namespacing ids`() =
        runTest {
            val store = AppStore(parse = { _, src -> fakeContacts("a", "b", source = src) }, ioDispatcher = Dispatchers.Unconfined)
            store.importFile("apple.vcf", Source.APPLE)
            val s = store.state.value
            assertEquals(listOf("imp0:a", "imp0:b"), s.contacts.map { it.id })
            assertEquals(2, s.imported.single().count)
            assertEquals(Source.APPLE, s.imported.single().source)
            assertEquals(false, s.importing)
        }

    @Test
    fun `two imports keep ids unique across files`() =
        runTest {
            val store = AppStore(parse = { _, src -> fakeContacts("x", source = src) }, ioDispatcher = Dispatchers.Unconfined)
            store.importFile("apple.vcf", Source.APPLE)
            store.importFile("google.vcf", Source.GOOGLE)
            assertEquals(
                listOf("imp0:x", "imp1:x"),
                store.state.value.contacts
                    .map { it.id },
            )
        }

    @Test
    fun `import failure sets error without throwing`() =
        runTest {
            val store = AppStore(parse = { _, _ -> error("bad file") }, ioDispatcher = Dispatchers.Unconfined)
            store.importFile("broken.vcf", Source.APPLE)
            val s = store.state.value
            assertEquals("bad file", s.error)
            assertTrue(s.contacts.isEmpty())
            assertEquals(false, s.importing)
        }

    @Test
    fun `a new import clears a previous error`() =
        runTest {
            val store =
                AppStore(
                    parse = { p, src -> if (p == "broken.vcf") error("bad") else fakeContacts("a", source = src) },
                    ioDispatcher = Dispatchers.Unconfined,
                )
            store.importFile("broken.vcf", Source.APPLE)
            store.importFile("ok.vcf", Source.APPLE)
            assertNull(store.state.value.error)
        }
}
