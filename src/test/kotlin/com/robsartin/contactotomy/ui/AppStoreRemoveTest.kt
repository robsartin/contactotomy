package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreRemoveTest {
    private fun store() =
        AppStore(
            parse = { path, src ->
                val tag = if (src == Source.APPLE) "a" else "g"
                listOf(Contact(id = tag, source = src, name = ContactName(given = tag), rawVCard = ""))
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `removing a file drops its summary and its contributed contacts`() =
        runTest {
            val s = store()
            s.importFile("apple.vcf", Source.APPLE) // imp0:a
            s.importFile("google.vcf", Source.GOOGLE) // imp1:g
            s.removeImportedFile("apple.vcf")
            assertEquals(
                listOf("google.vcf"),
                s.state.value.imported
                    .map { it.path },
            )
            assertEquals(
                listOf("imp1:g"),
                s.state.value.contacts
                    .map { it.id },
            )
        }
}
