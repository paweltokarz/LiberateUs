package pl.local.liberateus.data.remote

import pl.local.liberateus.data.LoginResult

/**
 * Access state after a centralized session-recovery attempt.
 *
 * ONLINE:
 * remote requests may proceed.
 *
 * LOCAL:
 * remote session is unavailable, but previously cached
 * account data may remain accessible.
 *
 * LOGIN_REQUIRED:
 * the app must present authentication UI.
 */
enum class SessionAccess {
    ONLINE,
    LOCAL,
    LOGIN_REQUIRED
}

/**
 * Which mechanism produced the recovery result.
 */
enum class SessionRecoverySource {
    EXISTING_SESSION,
    REFRESH,
    SAVED_CREDENTIALS,
    NONE
}

data class SessionRecoveryResult(
    val access: SessionAccess,
    val result: LoginResult,
    val source: SessionRecoverySource
) {

    val isOnline: Boolean
        get() =
            access ==
                SessionAccess.ONLINE
}

/**
 * One authoritative mapping from authentication result
 * to application access.
 *
 * Explicit invalid credentials are authoritative and
 * therefore require login.
 *
 * CAPTCHA and temporary failures do not invalidate
 * already cached Room data.
 */
internal fun classifySessionRecovery(
    hasLocalAccount: Boolean,
    result: LoginResult,
    source: SessionRecoverySource
): SessionRecoveryResult {

    val access =
        when (result) {

            LoginResult.Success ->
                SessionAccess.ONLINE

            LoginResult.InvalidCredentials ->
                SessionAccess.LOGIN_REQUIRED

            LoginResult.CaptchaRequired ->
                if (hasLocalAccount) {
                    SessionAccess.LOCAL
                } else {
                    SessionAccess.LOGIN_REQUIRED
                }

            is LoginResult.Failure ->
                if (hasLocalAccount) {
                    SessionAccess.LOCAL
                } else {
                    SessionAccess.LOGIN_REQUIRED
                }
        }

    return SessionRecoveryResult(
        access = access,
        result = result,
        source = source
    )
}
