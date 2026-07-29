package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun acceptsPublicHttpsCloudEndpoint() {
        assertNull(EndpointPolicy.validate("https://api.openai.com/v1"))
    }

    @Test
    fun rejectsClearTextEndpoint() {
        assertEquals(
            "The cloud endpoint must use HTTPS.",
            EndpointPolicy.validate("http://api.example.com/v1"),
        )
    }

    @Test
    fun rejectsLocalModelEndpoint() {
        assertEquals(
            "Local and private-network model endpoints are not supported.",
            EndpointPolicy.validate("https://127.0.0.1:11434/v1"),
        )
    }

    @Test
    fun rejectsCredentialInUrl() {
        assertEquals(
            "Enter a valid cloud host.",
            EndpointPolicy.validate("https://user:secret@example.com/v1"),
        )
    }
}
