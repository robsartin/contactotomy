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

    @Test fun `empty pattern never matches`() {
        assertFalse(PhonePattern.matches("---", "+15125551234"))
    }
}
