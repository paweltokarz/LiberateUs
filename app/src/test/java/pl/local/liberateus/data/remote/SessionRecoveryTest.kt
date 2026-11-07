package pl.local.liberateus.data.remote

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.data.LoginResult

class SessionRecoveryTest {

    private class FakeBackend :
        SessionBackend {

        var restoredSession =
            false

        var savedCredentials =
            true

        var username:
            String? = "student"

        var refreshResult =
            false

        var refreshError:
            Exception? = null

        var savedLoginResult:
            LoginResult =
            LoginResult.Success

        var refreshCalls =
            0

        var savedLoginCalls =
            0

        var restoredChecks =
            0

        override fun login(
            username: String,
            password: String
        ): LoginResult =
            LoginResult.Success

        override fun hasSavedCredentials():
            Boolean =
            savedCredentials

        override fun savedUsername():
            String? =
            username

        override fun hasRestoredSession():
            Boolean {
            restoredChecks += 1

            return restoredSession
        }

        override fun loginWithSavedCredentials():
            LoginResult {

            savedLoginCalls += 1

            return savedLoginResult
        }

        override fun resumeSavedLogin():
            LoginResult =
            savedLoginResult

        override fun refreshOAuthToken():
            Boolean {

            refreshCalls += 1

            refreshError?.let {
                throw it
            }

            return refreshResult
        }

        override fun clearSavedLogin() {
        }
    }

    @Test
    fun startup_existingSession_isAcceptedWithoutNetworkRecovery() {

        val backend =
            FakeBackend().apply {
                restoredSession =
                    true
            }

        val manager =
            SessionManager(
                backend
            )

        val result =
            manager.recoverSavedSession(
                hasLocalAccount =
                    true
            )

        assertEquals(
            SessionAccess.ONLINE,
            result.access
        )

        assertEquals(
            SessionRecoverySource.EXISTING_SESSION,
            result.source
        )

        assertEquals(
            0,
            backend.refreshCalls
        )

        assertEquals(
            0,
            backend.savedLoginCalls
        )
    }

    @Test
    fun startup_withoutToken_triesRefreshBeforeFullLogin() {

        val backend =
            FakeBackend().apply {
                restoredSession =
                    false

                refreshResult =
                    true
            }

        val result =
            SessionManager(
                backend
            ).recoverSavedSession(
                hasLocalAccount =
                    true
            )

        assertEquals(
            SessionAccess.ONLINE,
            result.access
        )

        assertEquals(
            SessionRecoverySource.REFRESH,
            result.source
        )

        assertEquals(
            1,
            backend.refreshCalls
        )

        assertEquals(
            0,
            backend.savedLoginCalls
        )
    }

    @Test
    fun failedRefresh_fallsBackToSavedCredentials() {

        val backend =
            FakeBackend().apply {
                refreshResult =
                    false

                savedLoginResult =
                    LoginResult.Success
            }

        val result =
            SessionManager(
                backend
            ).recoverSavedSession(
                hasLocalAccount =
                    true
            )

        assertEquals(
            SessionAccess.ONLINE,
            result.access
        )

        assertEquals(
            SessionRecoverySource.SAVED_CREDENTIALS,
            result.source
        )

        assertEquals(
            1,
            backend.refreshCalls
        )

        assertEquals(
            1,
            backend.savedLoginCalls
        )
    }

    @Test
    fun refreshException_withLocalData_entersLocalModeWithoutSecondNetworkAttempt() {

        val backend =
            FakeBackend().apply {
                refreshError =
                    IOException(
                        "offline"
                    )
            }

        val result =
            SessionManager(
                backend
            ).recoverSavedSession(
                hasLocalAccount =
                    true
            )

        assertEquals(
            SessionAccess.LOCAL,
            result.access
        )

        assertEquals(
            SessionRecoverySource.REFRESH,
            result.source
        )

        assertEquals(
            1,
            backend.refreshCalls
        )

        assertEquals(
            0,
            backend.savedLoginCalls
        )
    }

    @Test
    fun invalidSavedCredentials_requireLogin() {

        val backend =
            FakeBackend().apply {
                savedLoginResult =
                    LoginResult.InvalidCredentials
            }

        val result =
            SessionManager(
                backend
            ).recoverSavedSession(
                hasLocalAccount =
                    true
            )

        assertEquals(
            SessionAccess.LOGIN_REQUIRED,
            result.access
        )

        assertEquals(
            LoginResult.InvalidCredentials,
            result.result
        )
    }

    @Test
    fun captcha_withCachedAccount_keepsLocalAccess() {

        val backend =
            FakeBackend().apply {
                savedLoginResult =
                    LoginResult.CaptchaRequired
            }

        val result =
            SessionManager(
                backend
            ).recoverSavedSession(
                hasLocalAccount =
                    true
            )

        assertEquals(
            SessionAccess.LOCAL,
            result.access
        )
    }

    @Test
    fun failure_withoutCachedAccount_requiresLogin() {

        val backend =
            FakeBackend().apply {
                savedLoginResult =
                    LoginResult.Failure(
                        "server unavailable"
                    )
            }

        val result =
            SessionManager(
                backend
            ).recoverSavedSession(
                hasLocalAccount =
                    false
            )

        assertEquals(
            SessionAccess.LOGIN_REQUIRED,
            result.access
        )
    }

    @Test
    fun noSavedCredentials_requiresLogin() {

        val backend =
            FakeBackend().apply {
                savedCredentials =
                    false

                refreshResult =
                    false
            }

        val result =
            SessionManager(
                backend
            ).recoverSavedSession(
                hasLocalAccount =
                    false
            )

        assertEquals(
            SessionAccess.LOGIN_REQUIRED,
            result.access
        )

        assertEquals(
            SessionRecoverySource.NONE,
            result.source
        )

        assertEquals(
            0,
            backend.savedLoginCalls
        )
    }

    @Test
    fun requestFailure_doesNotTrustExistingToken() {

        val backend =
            FakeBackend().apply {
                restoredSession =
                    true

                refreshResult =
                    true
            }

        val result =
            SessionManager(
                backend
            ).recoverAfterRequestFailure(
                hasLocalAccount =
                    true
            )

        assertEquals(
            SessionAccess.ONLINE,
            result.access
        )

        assertEquals(
            SessionRecoverySource.REFRESH,
            result.source
        )

        /*
         * recoverAfterRequestFailure() deliberately does not
         * ask whether the old token exists.
         */
        assertEquals(
            0,
            backend.restoredChecks
        )

        assertEquals(
            1,
            backend.refreshCalls
        )
    }

    @Test
    fun recoveryResult_reportsOnlineState() {

        val online =
            SessionRecoveryResult(
                access =
                    SessionAccess.ONLINE,
                result =
                    LoginResult.Success,
                source =
                    SessionRecoverySource.REFRESH
            )

        val local =
            SessionRecoveryResult(
                access =
                    SessionAccess.LOCAL,
                result =
                    LoginResult.Failure(
                        "offline"
                    ),
                source =
                    SessionRecoverySource.REFRESH
            )

        assertTrue(
            online.isOnline
        )

        assertFalse(
            local.isOnline
        )
    }
}
