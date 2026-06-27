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
}
