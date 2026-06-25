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

    @Test
    fun `re-importing the same path then removing once leaves the other import intact`() =
        runTest {
            var call = 0
            val s =
                AppStore(
                    parse = { _, src ->
                        val tag = "c${call++}"
                        listOf(Contact(id = tag, source = src, name = ContactName(given = tag), rawVCard = ""))
                    },
                    ioDispatcher = Dispatchers.Unconfined,
                )
            s.importFile("apple.vcf", Source.APPLE) // imp0:c0
            s.importFile("apple.vcf", Source.APPLE) // imp1:c1, SAME path
            s.removeImportedFile("apple.vcf")
            // Exactly one summary row and one import's contacts remain.
            assertEquals(1, s.state.value.imported.size)
            assertEquals(
                listOf("imp0:c0"),
                s.state.value.contacts
                    .map { it.id },
            )
        }
}
