package com.robsartin.contactotomy.ui

import java.awt.FileDialog
import java.awt.Frame

/** Real file chooser backed by the native AWT file dialog. LOAD by default; SAVE when [save] is true. */
class AwtFilePicker(
    private val title: String,
    private val save: Boolean = false,
) : FilePicker {
    override fun pick(): String? {
        val mode = if (save) FileDialog.SAVE else FileDialog.LOAD
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return dir + file
    }
}
