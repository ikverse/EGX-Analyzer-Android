package com.ikverse.egxanalyzer.data

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The end of a run, in the shade: where it posts, what a tap carries, and silence without leave. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportReadyNotifierTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `a ready report posts on its own channel and opens the report`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReportReadyNotifier(app).ready(calls = 5, resultId = 42)
        val posted = shadowOf(manager).getNotification(ReportReadyNotifier.NOTIFICATION_ID)
        assertEquals(ReportReadyNotifier.CHANNEL_ID, posted.channelId)
        val intent = shadowOf(posted.contentIntent).savedIntent
        assertEquals(42L, intent.getLongExtra(AnalysisNotifier.EXTRA_RESULT_ID, -1))
    }

    @Test
    fun `nothing is posted without the permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReportReadyNotifier(app).ready(calls = 5, resultId = 42)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
