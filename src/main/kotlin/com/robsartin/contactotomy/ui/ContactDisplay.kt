package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.ContactName

/** Human-readable name for a contact: the formatted name, else given + family. */
fun displayName(name: ContactName): String =
    name.formatted?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(name.given, name.family).joinToString(" ")
