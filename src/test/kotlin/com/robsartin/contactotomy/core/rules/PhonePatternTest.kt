package com.robsartin.contactotomy.core.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonePatternTest {
    @Test fun `matches by national number area code`() {
        assertTrue(PhonePattern.matches("512-???-????", "+15125551234"))
    }

    @Test fun `rejects a different area code`() {
        assertFalse(PhonePattern.matches("512-???-????", "+13125551234"))
    }

    @Test fun `unparseable phone falls back to suffix match`() {
        // "5551234" cannot be parsed as E.164 (no +country); suffix match on digits.
        assertTrue(PhonePattern.matches("???????", "5551234"))
        assertFalse(PhonePattern.matches("000????", "5551234"))
    }

    @Test fun `parseable phone does not fall back to suffix match`() {
        // "5551234" is the trailing digits but NOT the national number; must not match.
        assertFalse(PhonePattern.matches("555-1234", "+15125551234"))
    }

    @Test fun `preserves leading zero in national significant number`() {
        // Rome (+39 06...) keeps its trunk leading zero in the NSN: "0665897000".
        // nationalNumber (a Long) would drop it to "665897000", breaking the match.
        assertTrue(PhonePattern.matches("06-????-????", "+390665897000"))
    }

    @Test fun `empty pattern never matches`() {
        assertFalse(PhonePattern.matches("---", "+15125551234"))
    }
}
