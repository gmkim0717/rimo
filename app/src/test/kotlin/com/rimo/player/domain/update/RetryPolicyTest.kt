package com.rimo.player.domain.update

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryPolicyTest {

    private val policy = RetryPolicy()

    @Test
    fun `default delays double from two seconds`() {
        assertEquals(2.seconds, policy.delayAfter(1))
        assertEquals(4.seconds, policy.delayAfter(2))
        assertEquals(8.seconds, policy.delayAfter(3))
        assertEquals(16.seconds, policy.delayAfter(4))
        assertEquals(32.seconds, policy.delayAfter(5))
    }

    @Test
    fun `gives up after five retries`() {
        assertNull(policy.delayAfter(6))
        assertNull(policy.delayAfter(7))
    }

    @Test
    fun `delay is capped at maxDelay`() {
        val aggressive = RetryPolicy(maxRetries = 10, baseDelay = 30.seconds, factor = 2.0, maxDelay = 60.seconds)
        assertEquals(30.seconds, aggressive.delayAfter(1))
        assertEquals(60.seconds, aggressive.delayAfter(2))
        assertEquals(60.seconds, aggressive.delayAfter(3))
    }

    @Test
    fun `zero retries always gives up`() {
        assertNull(RetryPolicy(maxRetries = 0).delayAfter(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `attempt numbers are one based`() {
        policy.delayAfter(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factor below one is rejected`() {
        RetryPolicy(factor = 0.5)
    }
}
