package com.robsartin.contactotomy.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() =
    application {
        val store = remember { AppStore() }
        Window(onCloseRequest = ::exitApplication, title = "Contactotomy") {
            App(store)
        }
    }
