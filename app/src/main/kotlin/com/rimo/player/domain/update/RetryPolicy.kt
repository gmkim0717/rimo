package com.rimo.player.domain.update

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Exponential back-off for the APK download: up to [maxRetries] retries after the first attempt,
 * waiting `baseDelay * factor^(n-1)` before retry n, capped at [maxDelay].
 *
 * Defaults give 2s, 4s, 8s, 16s, 32s and then give up.
 */
class RetryPolicy(
    val maxRetries: Int = 5,
    val baseDelay: Duration = 2.seconds,
    val factor: Double = 2.0,
    val maxDelay: Duration = 60.seconds,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        require(factor >= 1.0) { "factor must be >= 1.0" }
    }

    /**
     * Delay to wait after attempt number [failedAttempt] (1-based) has failed, or `null` when
     * the retry budget is exhausted and the caller should give up.
     */
    fun delayAfter(failedAttempt: Int): Duration? {
        require(failedAttempt >= 1) { "failedAttempt is 1-based" }
        if (failedAttempt > maxRetries) return null
        val raw = baseDelay * factor.pow(failedAttempt - 1)
        return if (raw > maxDelay) maxDelay else raw
    }
}
