package app.saathi.core

/** How "I'm okay" is confirmed. */
enum class OkayPath { DIRECT, UNLOCK_FIRST }

/**
 * Taking back an alert that already went out needs the phone unlocked, so a snatched phone cannot tell her
 * contacts she is fine. Everything else is one tap: answering a check-in, cancelling a pending window, ending a
 * practice. A phone with no screen lock has nothing to unlock, so it goes straight through.
 */
fun okayPath(alertActive: Boolean, keyguardLocked: Boolean, deviceSecure: Boolean): OkayPath =
    if (alertActive && keyguardLocked && deviceSecure) OkayPath.UNLOCK_FIRST else OkayPath.DIRECT
