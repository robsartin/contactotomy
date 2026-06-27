package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.ContactName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompanyNameDetectorTest {
    private fun named(
        formatted: String? = null,
        given: String? = null,
        family: String? = null,
    ) = ContactName(given = given, family = family, formatted = formatted)

    @Test
    fun `legal suffix is detected`() {
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(formatted = "Acme Inc")))
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(formatted = "Acme Inc.")))
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(given = "Acme", family = "LLC")))
    }

    @Test
    fun `ampersand is detected`() {
        assertEquals(CompanySignal.AMPERSAND, CompanyNameDetector.detect(named(formatted = "Smith & Sons")))
    }

    @Test
    fun `keyword is detected`() {
        assertEquals(CompanySignal.KEYWORD, CompanyNameDetector.detect(named(formatted = "Joe's Plumbing")))
        assertEquals(CompanySignal.KEYWORD, CompanyNameDetector.detect(named(formatted = "Bright Solutions")))
    }

    @Test
    fun `weak signals - all caps or single token with no family`() {
        assertEquals(CompanySignal.WEAK, CompanyNameDetector.detect(named(formatted = "ACME")))
        assertEquals(CompanySignal.WEAK, CompanyNameDetector.detect(named(given = "Dave")))
    }

    @Test
    fun `ordinary person names are not company-like`() {
        assertNull(CompanyNameDetector.detect(named(given = "Jane", family = "Smith")))
        assertNull(CompanyNameDetector.detect(named(given = "Jane", family = "Baker")))
        assertNull(CompanyNameDetector.detect(named(formatted = "")))
    }

    @Test
    fun `highest-precision signal wins`() {
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(formatted = "ACME PLUMBING INC")))
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(formatted = "Smith & Sons LLC")))
    }
}
