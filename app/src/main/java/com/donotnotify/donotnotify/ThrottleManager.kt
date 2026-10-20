package com.donotnotify.donotnotify

/**
 * Pure, Android-free planning for THROTTLE rules, JVM-testable like
 * [RuleMatcher.planNotificationDecision], which supplies the matched rules.
 *
 * Each THROTTLE rule has its own window. Every matching notification consumes and is gated by
 * it, whichever rule matched first. A notification matching several THROTTLE rules is
 * suppressed while any of their windows is running. A rule with empty filters matches
 * everything, so it throttles the whole app.
 *
 * A window restarts only when a notification actually passes, so a suppressed one never
 * extends it.
 */
object ThrottleManager {

    const val DEFAULT_SECONDS = 60

    /** Longest allowed window, also the editor's clamp: 24 hours. */
    const val MAX_SECONDS = 24 * 60 * 60

    /** One running throttle window. [key] addresses its last-pass record in storage. */
    data class ThrottleWindow(val key: String, val seconds: Int)

    /** What to do with a notification: suppress it, or let it pass and restart [windowsToRecord]. */
    data class ThrottleOutcome(val suppress: Boolean, val windowsToRecord: List<String>)

    /** A rule's window length: its setting, or the default when unset or out of range. */
    fun secondsFor(config: AdvancedRuleConfig?): Int =
        config?.throttleSeconds?.takeIf { it > 0 }?.coerceAtMost(MAX_SECONDS) ?: DEFAULT_SECONDS

    fun windowsFor(matchedThrottleRules: List<BlockerRule>): List<ThrottleWindow> =
        matchedThrottleRules.map { ThrottleWindow("rule:${it.id}", secondsFor(it.advancedConfig)) }

    /**
     * True while a window that last passed a notification at [lastPassAt] is still running.
     * `now - lastPassAt == window` passes (the window has run out). A [lastPassAt] in the
     * future (the clock was set back) doesn't suppress, so a clock change can't stall an app.
     */
    fun shouldSuppress(lastPassAt: Long?, seconds: Int, now: Long): Boolean =
        lastPassAt != null && now >= lastPassAt && now - lastPassAt < seconds * 1000L

    fun evaluate(windows: List<ThrottleWindow>, lastPassAt: (String) -> Long?, now: Long): ThrottleOutcome {
        val suppress = windows.any { shouldSuppress(lastPassAt(it.key), it.seconds, now) }
        return ThrottleOutcome(suppress, if (suppress) emptyList() else windows.map { it.key })
    }
}
