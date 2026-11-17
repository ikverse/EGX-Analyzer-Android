package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.SyncMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The choice that replaced the Telegram switch reads what every device already has stored: a phone
 * that had switched sync off must not find it back on after the update.
 */
@RunWith(RobolectricTestRunner::class)
class SyncModeSettingTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun repository() = SettingsRepository(context, NoCredentials())

    private fun oldSwitch(on: Boolean) {
        context.getSharedPreferences("egx_android_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("telegram_sync_enabled", on).commit()
    }

    @Test
    fun `a fresh install syncs through Telegram, as it always has`() {
        assertEquals(SyncMode.TELEGRAM, repository().syncMode())
    }

    @Test
    fun `a device that had switched sync off stays off`() {
        oldSwitch(false)
        assertEquals(SyncMode.OFF, repository().syncMode())
    }

    @Test
    fun `a choice once made outranks the old switch and survives a restart`() {
        oldSwitch(true)
        repository().recordSyncMode(SyncMode.DRIVE)
        assertEquals(SyncMode.DRIVE, repository().syncMode())
    }

    private class NoCredentials : CredentialStore {
        override fun contains(provider: CloudProvider) = false
        override fun save(provider: CloudProvider, credential: CharArray) = Unit
        override fun read(provider: CloudProvider): CharArray? = null
        override fun remove(provider: CloudProvider) = Unit
    }
}
