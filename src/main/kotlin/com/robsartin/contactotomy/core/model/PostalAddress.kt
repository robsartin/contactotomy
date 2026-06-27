package com.robsartin.contactotomy.core.model

data class PostalAddress(
    val poBox: String? = null,
    val extended: String? = null,
    val street: String? = null,
    val city: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)

/** Comma-joined rendering used as the identity key for dedup, value-removal, and glob matching. */
fun PostalAddress.toDisplayString(): String = listOfNotNull(street, city, region, postalCode, country).joinToString(", ")
