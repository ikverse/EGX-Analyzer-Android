package com.ikverse.egxanalyzer.model

/**
 * What carries this device's sync, chosen per device and never synced itself.
 *
 * Synced, a phone switched off could never be switched back on from another, because it would no
 * longer be listening - the reason the old Telegram switch was device-local, kept for the choice
 * that replaced it.
 */
enum class SyncMode(val label: String) {
    OFF("Off"),
    TELEGRAM("Telegram"),
    DRIVE("Google Drive"),
}
