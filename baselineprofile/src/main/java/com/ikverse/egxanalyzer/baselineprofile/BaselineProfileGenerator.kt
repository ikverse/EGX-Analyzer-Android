package com.ikverse.egxanalyzer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Runs the app once, cold, and records what it touches while starting and while the reader first
 * scrolls a list - the two moments a baseline profile actually speeds up, since both would
 * otherwise be interpreted fresh by the runtime on a phone that has never run this code before.
 *
 * Recorded, not written by hand: guessing which classes matter would drift from what the app
 * actually does the moment either one changes, and would keep being "right" long after it had
 * gone stale. This runs once, on a connected device, to produce `baseline-prof.txt`; nothing
 * about a later release re-runs it; see `app/build.gradle.kts`'s `baselineProfile` block for how
 * that file reaches the APK from here.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.ikverse.egxanalyzer",
        includeInStartupProfile = true,
    ) {
        startActivityAndWait()

        // The first frame and the background data load (saved reports, trades, prompts - see
        // `LiveAppState.initialDataLoaded`) both need room to finish before a scroll means
        // anything; scrolling through a "Loading..." state would profile that screen instead of
        // the one a reader actually spends time on.
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
        Thread.sleep(1_000)

        repeat(3) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.8).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.2).toInt(),
                10,
            )
            Thread.sleep(300)
        }
    }
}
