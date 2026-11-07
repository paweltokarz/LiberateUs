package pl.local.liberateus.ui

import pl.local.liberateus.data.LoginResult

/**
 * Distinguishes access to locally cached account data
 * from availability of a live Librus session.
 */
internal enum class StartupAccessMode {

    /**
     * Remote session was restored successfully.
     */
    ONLINE,

    /**
     * We know which local account owns the Room data,
     * but Librus cannot currently provide a usable session.
     */
    LOCAL,

    /**
     * No safe local account context exists, or Librus
     * explicitly rejected the stored credentials.
     */
    LOGIN_REQUIRED
}

internal fun resolveStartupAccess(
    hasLocalAccount: Boolean,
    result: LoginResult
): StartupAccessMode =
    when (result) {

        LoginResult.Success ->
            StartupAccessMode.ONLINE

        /*
         * Explicit rejection of stored credentials is
         * different from temporary network/server failure.
         */
        LoginResult.InvalidCredentials ->
            StartupAccessMode.LOGIN_REQUIRED

        /*
         * CAPTCHA prevents remote authentication but does
         * not invalidate already cached local data.
         */
        LoginResult.CaptchaRequired ->
            if (hasLocalAccount) {
                StartupAccessMode.LOCAL
            } else {
                StartupAccessMode.LOGIN_REQUIRED
            }

        /*
         * Failure includes unavailable network/server and
         * other non-authoritative session restoration errors.
         *
         * It must not destroy access to existing Room data.
         */
        is LoginResult.Failure ->
            if (hasLocalAccount) {
                StartupAccessMode.LOCAL
            } else {
                StartupAccessMode.LOGIN_REQUIRED
            }
    }
