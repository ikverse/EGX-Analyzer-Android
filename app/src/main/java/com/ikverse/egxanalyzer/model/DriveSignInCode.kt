package com.ikverse.egxanalyzer.model

/**
 * A Google sign-in waiting on the owner, on a device with no Play services: the code to type and
 * the page to type it at. Shown in the Drive row until Google says yes, no, or the code expires.
 */
data class DriveSignInCode(
    val userCode: String,
    val verificationUrl: String,
)
