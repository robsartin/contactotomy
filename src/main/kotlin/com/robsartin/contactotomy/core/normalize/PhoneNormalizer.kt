package com.robsartin.contactotomy.core.normalize

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat

class PhoneNormalizer(private val defaultRegion: String = "US") {
    private val util = PhoneNumberUtil.getInstance()

    /** Returns the E.164 form, or null if the input cannot be parsed as a phone number. */
    fun normalize(raw: String): String? = try {
        val parsed = util.parse(raw, defaultRegion)
        if (util.isValidNumber(parsed)) util.format(parsed, PhoneNumberFormat.E164) else null
    } catch (e: NumberParseException) {
        null
    }
}
