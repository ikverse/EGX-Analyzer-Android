package com.ikverse.egxanalyzer.model

import java.io.File

/** Where the update check has got to, so the screen can say the same thing the app is doing. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** Nothing newer exists. Carries the version asked about, so the answer names what it answered. */
    data class UpToDate(val versionName: String) : UpdateState

    data class Available(val update: AvailableUpdate) : UpdateState

    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState

    /** Downloaded, checked, and waiting for the one tap that hands it to Android's installer. */
    data class Ready(val update: AvailableUpdate, val file: File) : UpdateState

    data class Failed(val reason: String) : UpdateState
}
