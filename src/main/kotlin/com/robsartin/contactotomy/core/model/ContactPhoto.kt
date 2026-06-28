package com.robsartin.contactotomy.core.model

/**
 * An optional photo for a contact, stored as a base64-encoded string (if embedded)
 * or a URL (if external). Using a String (not ByteArray) gives value equality
 * and stays serialization-friendly.
 *
 * Exactly one of [base64] or [url] should be non-null in practice; both null is
 * valid (representing "no photo") and both non-null is treated as embedded-preferred.
 */
data class ContactPhoto(
    val base64: String? = null, // base64-encoded embedded image bytes, if embedded
    val url: String? = null, // external URL, if the PHOTO is a URI
    val contentType: String? = null, // e.g. "image/jpeg"
)
