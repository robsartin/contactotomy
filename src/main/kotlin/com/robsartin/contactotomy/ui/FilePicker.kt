package com.robsartin.contactotomy.ui

/** Opens a file chooser and returns the chosen path, or null if cancelled. */
fun interface FilePicker {
    fun pick(): String?
}
