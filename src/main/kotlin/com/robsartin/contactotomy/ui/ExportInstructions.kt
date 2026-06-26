package com.robsartin.contactotomy.ui

/** Loads the bundled export/import guide from resources. */
fun loadExportInstructions(): String =
    object {}
        .javaClass
        .getResourceAsStream("/export-instructions.md")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("export-instructions.md not found on the classpath")
