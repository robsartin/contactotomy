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
 * - merged: cards collapsed by the Review step (contacts.size - reviewedContacts.size), 0 if no review step
 * - removed: cards removed by deletion rules
 *   (reviewedContacts ?: contacts).size - finalContacts.size, 0 if no deletion step
 * - exporting: the final count to be written
 */
fun exportSummary(state: AppState): ExportSummary {
    val imported = state.contacts.size
    val merged = if (state.reviewedContacts != null) maxOf(0, imported - state.reviewedContacts.size) else 0
    val preDeletionSize = (state.reviewedContacts ?: state.contacts).size
    val removed = if (state.finalContacts != null) maxOf(0, preDeletionSize - state.finalContacts.size) else 0
    val exporting = state.finalContacts?.size ?: preDeletionSize
    return ExportSummary(imported = imported, merged = merged, removed = removed, exporting = exporting)
}
