package com.robsartin.contactotomy.ui

data class ExportSummary(
    val imported: Int,
    val merged: Int,
    val removed: Int,
    val exporting: Int,
)

/**
 * Derives a pre-export summary from the pipeline state using only list sizes.
 *
 * - imported: raw imported count
 * - merged: cards collapsed (contacts.size - mergedContacts.size), 0 if no merge step
 * - removed: cards removed by deletion rules
 *   (tidyContacts ?: mergedContacts ?: contacts).size - finalContacts.size, 0 if no deletion step
 * - exporting: the final count to be written
 */
fun exportSummary(state: AppState): ExportSummary {
    val imported = state.contacts.size
    val merged = if (state.mergedContacts != null) imported - state.mergedContacts.size else 0
    val preDeletionSize = (state.tidyContacts ?: state.mergedContacts ?: state.contacts).size
    val removed = if (state.finalContacts != null) preDeletionSize - state.finalContacts.size else 0
    val exporting = state.finalContacts?.size ?: preDeletionSize
    return ExportSummary(imported = imported, merged = merged, removed = removed, exporting = exporting)
}
