package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.ContactName

/** Which signal flagged a name as company-like (precision order: LEGAL_SUFFIX highest). */
enum class CompanySignal { LEGAL_SUFFIX, AMPERSAND, KEYWORD, WEAK }

// Mirrors the UI's displayName but kept in core (no UI dependency) so core stays UI-free.

/** The company string a name field holds: formatted if present, else given + family. */
fun companyNameText(name: ContactName): String =
    name.formatted?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(name.given, name.family).joinToString(" ")

/** Pure heuristic: does a name field actually hold a company name? Returns the strongest signal, or null. */
object CompanyNameDetector {
    private val SUFFIXES =
        setOf(
            "INC",
            "LLC",
            "LTD",
            "CORP",
            "CO",
            "GMBH",
            "PLC",
            "SA",
            "LLP",
            "LP",
            "GROUP",
            "HOLDINGS",
            "CORPORATION",
            "INCORPORATED",
            "COMPANY",
        )
    private val KEYWORDS =
        setOf(
            "SERVICES",
            "SOLUTIONS",
            "RESTAURANT",
            "PLUMBING",
            "SALON",
            "CLINIC",
            "STUDIO",
            "BANK",
            "AGENCY",
            "CONSULTING",
            "SYSTEMS",
            "TECHNOLOGIES",
            "ENTERPRISES",
            "INDUSTRIES",
            "ISD",
        )
    private val AND_CO = Regex("(?i)\\b(and|&)\\s+co\\b")

    fun detect(name: ContactName): CompanySignal? {
        val display = companyNameText(name)
        if (display.isBlank()) return null
        val tokens = display.split(Regex("\\s+")).filter { it.isNotBlank() }
        val cleaned = tokens.map { it.replace(".", "").replace(",", "").uppercase() }

        if (cleaned.isNotEmpty() && cleaned.last() in SUFFIXES) return CompanySignal.LEGAL_SUFFIX
        if (display.contains("&") || AND_CO.containsMatchIn(display)) return CompanySignal.AMPERSAND
        if (cleaned.any { it in KEYWORDS } || display.contains("independent school district", ignoreCase = true)) {
            return CompanySignal.KEYWORD
        }
        val allCaps = display.any { it.isLetter() } && display == display.uppercase()
        val singleToken = tokens.size == 1 && name.family.isNullOrBlank()
        if (allCaps || singleToken) return CompanySignal.WEAK
        return null
    }

    /** True only for the strong signals safe to auto-pre-check (never WEAK). */
    fun isHighPrecision(name: ContactName): Boolean =
        when (detect(name)) {
            CompanySignal.LEGAL_SUFFIX, CompanySignal.AMPERSAND, CompanySignal.KEYWORD -> true
            CompanySignal.WEAK, null -> false
        }
}
