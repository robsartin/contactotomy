package com.robsartin.contactotomy.ui

import java.awt.FileDialog
import java.awt.Frame

/** Real file chooser backed by the native AWT file dialog, filtered to .vcf. */
class AwtFilePicker(
    private val title: String,
) : FilePicker {
    override fun pick(): String? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".vcf", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return dir + file
    }
}
