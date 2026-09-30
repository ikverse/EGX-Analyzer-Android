package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialSanitizerTest {

    @Test
    fun `keeps an ordinary key unchanged`() {
        assertEquals("sk-abc123DEF_456-xyz", "sk-abc123DEF_456-xyz".sanitizedCredential())
    }

    @Test
    fun `strips the invisible characters a phone paste adds`() {
        // Zero-width space, non-breaking space, zero-width no-break space, newline, tab.
        val pasted = "​sk-abc123 ﻿\n\t"

        assertEquals("sk-abc123", pasted.sanitizedCredential())
    }

    @Test
    fun `strips surrounding whitespace like trim did`() {
        assertEquals("sk-abc123", "  sk-abc123  ".sanitizedCredential())
    }

    @Test
    fun `leaves nothing when the input is only invisible characters`() {
        assertEquals("", "​  \n".sanitizedCredential())
    }
}
