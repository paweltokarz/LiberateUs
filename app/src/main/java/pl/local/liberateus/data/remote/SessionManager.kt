package pl.local.liberateus.data.remote

import android.content.Context
import pl.local.liberateus.data.LoginResult

/**
 * Public session façade.
 *
 * UI and repositories do not decide how a broken Librus
 * session should be recovered.
 */
class SessionManager internal constructor(
    private val backend: SessionBackend
) : SessionIdentityProvider {

    internal constructor(
        connection: LibrusConnection
    ) : this(
        LibrusSessionBackend(
            connection
        )
    )

    constructor(
        context: Context
    ) : this(
        LibrusConnection(
            context.applicationContext
        )
    )

    fun login(
        username: String,
        password: String
    ): LoginResult =
        backend.login(
            username,
            password
        )

    fun hasSavedCredentials():
        Boolean =
        backend.hasSavedCredentials()

    override fun savedUsername():
        String? =
        backend.savedUsername()

    fun hasRestoredSession():
        Boolean =
        backend.hasRestoredSession()

    /**
     * Legacy low-level operation retained for compatibility.
     *
     * Application orchestration should use
     * recoverSavedSession() or recoverAfterRequestFailure().
     */
    fun loginWithSavedCredentials():
        LoginResult =
        backend.loginWithSavedCredentials()

    /**
     * Legacy low-level startup operation retained for
     * compatibility.
     *
     * Application orchestration should use
     * recoverSavedSession().
     */
    fun resumeSavedLogin():
        LoginResult =
        backend.resumeSavedLogin()

    /**
     * Low-level lightweight token refresh.
     *
     * Application orchestration should not need to select
     * this operation directly.
     */
    fun refreshOAuthToken():
        Boolean =
        backend.refreshOAuthToken()

    /**
     * Central startup recovery.
     *
     * Order:
     *
     * 1. accept an already restored oauth_token;
     * 2. try lightweight /refreshToken;
     * 3. if refresh cleanly fails, use saved credentials;
     * 4. classify the result as ONLINE / LOCAL /
     *    LOGIN_REQUIRED.
     *
     * A refresh exception is treated as a temporary failure.
     * We deliberately do NOT immediately perform another
     * potentially expensive full login when the network itself
     * appears unavailable.
     */
    fun recoverSavedSession(
        hasLocalAccount: Boolean
    ): SessionRecoveryResult =
        recover(
            hasLocalAccount =
                hasLocalAccount,
            trustExistingSession =
                true
        )

    /**
     * Recovery after an authenticated request has failed.
     *
     * The existing oauth_token cannot be trusted because the
     * request using that session has already failed.
     *
     * Therefore this path starts with refresh and only then
     * falls back to saved credentials.
     */
    fun recoverAfterRequestFailure(
        hasLocalAccount: Boolean
    ): SessionRecoveryResult =
        recover(
            hasLocalAccount =
                hasLocalAccount,
            trustExistingSession =
                false
        )

    private fun recover(
        hasLocalAccount: Boolean,
        trustExistingSession: Boolean
    ): SessionRecoveryResult {

        if (
            trustExistingSession
            &&
            backend.hasRestoredSession()
        ) {
            return classifySessionRecovery(
                hasLocalAccount =
                    hasLocalAccount,
                result =
                    LoginResult.Success,
                source =
                    SessionRecoverySource.EXISTING_SESSION
            )
        }

        val refreshResult =
            try {
                backend.refreshOAuthToken()
            } catch (
                error: Exception
            ) {
                return classifySessionRecovery(
                    hasLocalAccount =
                        hasLocalAccount,
                    result =
                        LoginResult.Failure(
                            error.message
                                ?: "Nie udało się odświeżyć sesji"
                        ),
                    source =
                        SessionRecoverySource.REFRESH
                )
            }

        if (refreshResult) {
            return classifySessionRecovery(
                hasLocalAccount =
                    hasLocalAccount,
                result =
                    LoginResult.Success,
                source =
                    SessionRecoverySource.REFRESH
            )
        }

        if (
            !backend.hasSavedCredentials()
        ) {
            return classifySessionRecovery(
                hasLocalAccount =
                    hasLocalAccount,
                result =
                    LoginResult.Failure(
                        "Brak zapisanych danych logowania"
                    ),
                source =
                    SessionRecoverySource.NONE
            )
        }

        val loginResult =
            try {
                backend
                    .loginWithSavedCredentials()
            } catch (
                error: Exception
            ) {
                LoginResult.Failure(
                    error.message
                        ?: "Nie udało się odnowić sesji"
                )
            }

        return classifySessionRecovery(
            hasLocalAccount =
                hasLocalAccount,
            result =
                loginResult,
            source =
                SessionRecoverySource.SAVED_CREDENTIALS
        )
    }

    fun logout() {
        backend.clearSavedLogin()
    }
}
