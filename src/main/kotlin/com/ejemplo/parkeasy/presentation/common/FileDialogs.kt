package com.ejemplo.parkeasy.presentation.common

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.nio.file.Paths

/** Diálogos nativos para elegir dónde guardar o qué abrir. Devuelven null si se cancela. */
object FileDialogs {
    fun save(title: String, suggestedName: String): Path? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return Paths.get(dialog.directory, name)
    }

    fun open(title: String): Path? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return Paths.get(dialog.directory, name)
    }
}
