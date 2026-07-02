package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanyNormalizerTest {
    @Test
    fun `blank org gets the name promoted and the name cleared`() {
        val c = contact("a").copy(name = ContactName(formatted = "Round Rock ISD"))
        val out = CompanyNormalizer.markAsCompany(c)
        assertEquals("Round Rock ISD", out.org)
        assertEquals(ContactName(), out.name)
    }

    @Test
    fun `existing org is kept and only the name is cleared`() {
        val c = contact("a", given = "Acme", family = "Inc", org = "Acme Incorporated")
        val out = CompanyNormalizer.markAsCompany(c)
        assertEquals("Acme Incorporated", out.org)
        assertEquals(ContactName(), out.name)
    }

    @Test
    fun `nameFromEmail sets the formatted name to the first email and keeps emails`() {
        val c = contact("a", emails = listOf("lonely@example.com", "other@example.com"))
        val out = CompanyNormalizer.nameFromEmail(c)
        assertEquals(ContactName(formatted = "lonely@example.com"), out.name)
        assertEquals(listOf("lonely@example.com", "other@example.com"), out.emails)
    }

    @Test
    fun `nameFromPhone sets the formatted name to the first phone and keeps phones and emails`() {
        val c = contact("a", phones = listOf("+15125551234", "+15125555678"), emails = listOf("x@example.com"))
        val out = CompanyNormalizer.nameFromPhone(c)
        assertEquals(ContactName(formatted = "+15125551234"), out.name)
        assertEquals(listOf("+15125551234", "+15125555678"), out.phones)
        assertEquals(listOf("x@example.com"), out.emails)
    }
}
