package com.robsartin.contactotomy.core.rules

import com.google.i18n.phonenumbers.PhoneNumberUtil

/** Matches a digit pattern (`?` = one digit, separators ignored) against a phone's national number. */
internal object PhonePattern {
    private val util = PhoneNumberUtil.getInstance()

    fun matches(
        pattern: String,
        phone: String,
    ): Boolean {
        val pat = pattern.filter { it.isDigit() || it == '?' }
        if (pat.isEmpty()) return false

        val national = nationalDigits(phone)
        if (national != null && digitMatch(pat, national)) return true

        // Fallback: suffix match against the phone's bare digits.
        val digits = phone.filter { it.isDigit() }
        return digits.length >= pat.length && digitMatch(pat, digits.takeLast(pat.length))
    }

    private fun nationalDigits(phone: String): String? =
        try {
            util.parse(phone, null).nationalNumber.toString()
        } catch (e: com.google.i18n.phonenumbers.NumberParseException) {
            null
        }

    private fun digitMatch(
        pattern: String,
        digits: String,
    ): Boolean {
        if (pattern.length != digits.length) return false
        return pattern.indices.all { i -> pattern[i] == '?' || pattern[i] == digits[i] }
    }
}
