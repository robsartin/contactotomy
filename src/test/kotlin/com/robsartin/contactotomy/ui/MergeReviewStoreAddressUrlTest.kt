package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.toDisplayString
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertTrue

class MergeReviewStoreAddressUrlTest {
    private val addr1 = PostalAddress(street = "100 Main St", city = "Austin", region = "TX", postalCode = "78701")
    private val addr2 = PostalAddress(street = "200 Oak Ave", city = "Seattle", region = "WA", postalCode = "98101")

    private fun addressStore(): Pair<MergeReviewStore, String> {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), addresses = listOf(addr1))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), addresses = listOf(addr2))
        val store = MergeReviewStore(listOf(a, b))
        val id =
            store.state.value.items
                .single()
                .id
        return store to id
    }

    private fun urlStore(): Pair<MergeReviewStore, String> {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), urls = listOf("https://rob.example.com"))
        val b =
            contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), urls = listOf("https://robert.example.com"))
        val store = MergeReviewStore(listOf(a, b))
        val id =
            store.state.value.items
                .single()
                .id
        return store to id
    }

    @Test
    fun `toggling an address value adds it to excludedValues`() {
        val (store, id) = addressStore()
        val displayStr = addr1.toDisplayString()
        val ev = ExcludedValue("addresses", displayStr)
        store.toggleField(id, ev)
        assertTrue(
            ev in
                store.state.value.items
                    .single()
                    .excludedValues,
        )
    }

    @Test
    fun `toggling an address value twice removes it from excludedValues`() {
        val (store, id) = addressStore()
        val displayStr = addr1.toDisplayString()
        val ev = ExcludedValue("addresses", displayStr)
        store.toggleField(id, ev)
        store.toggleField(id, ev)
        assertTrue(
            ev !in
                store.state.value.items
                    .single()
                    .excludedValues,
        )
    }

    @Test
    fun `commit drops the excluded address from the merged contact`() {
        val (store, id) = addressStore()
        val displayStr = addr1.toDisplayString()
        store.toggleField(id, ExcludedValue("addresses", displayStr))
        store.accept(id)
        val merged = store.commit().single()
        assertTrue(merged.addresses.none { it.toDisplayString() == displayStr })
        assertTrue(merged.addresses.any { it.toDisplayString() == addr2.toDisplayString() })
    }

    @Test
    fun `toggling a url value adds it to excludedValues`() {
        val (store, id) = urlStore()
        val ev = ExcludedValue("urls", "https://rob.example.com")
        store.toggleField(id, ev)
        assertTrue(
            ev in
                store.state.value.items
                    .single()
                    .excludedValues,
        )
    }

    @Test
    fun `toggling a url value twice removes it from excludedValues`() {
        val (store, id) = urlStore()
        val ev = ExcludedValue("urls", "https://rob.example.com")
        store.toggleField(id, ev)
        store.toggleField(id, ev)
        assertTrue(
            ev !in
                store.state.value.items
                    .single()
                    .excludedValues,
        )
    }

    @Test
    fun `commit drops the excluded url from the merged contact`() {
        val (store, id) = urlStore()
        store.toggleField(id, ExcludedValue("urls", "https://rob.example.com"))
        store.accept(id)
        val merged = store.commit().single()
        assertTrue("https://rob.example.com" !in merged.urls)
        assertTrue("https://robert.example.com" in merged.urls)
    }
}
