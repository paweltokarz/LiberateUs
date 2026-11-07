package pl.local.liberateus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.data.LoginResult

class SessionManagerTest {

    private class FakeBackend :
        SessionBackend {

        var username:
            String? = "saved-user"

        var savedCredentials =
            true

        var restoredSession =
            true

        var loginResult:
            LoginResult =
            LoginResult.Success

        var resumeResult:
            LoginResult =
            LoginResult.Success

        var reloginResult:
            LoginResult =
            LoginResult.Success

        var refreshResult =
            true

        var loginUsername:
            String? = null

        var loginPassword:
            String? = null

        var loginCalls =
            0

        var resumeCalls =
            0

        var reloginCalls =
            0

        var refreshCalls =
            0

        var clearCalls =
            0

        override fun login(
            username: String,
            password: String
        ): LoginResult {

            loginCalls += 1
            loginUsername = username
            loginPassword = password

            return loginResult
        }

        override fun hasSavedCredentials():
            Boolean =
            savedCredentials

        override fun savedUsername():
            String? =
            username

        override fun hasRestoredSession():
            Boolean =
            restoredSession

        override fun loginWithSavedCredentials():
            LoginResult {

            reloginCalls += 1

            return reloginResult
        }

        override fun resumeSavedLogin():
            LoginResult {

            resumeCalls += 1

            return resumeResult
        }

        override fun refreshOAuthToken():
            Boolean {

            refreshCalls += 1

            return refreshResult
        }

        override fun clearSavedLogin() {
            clearCalls += 1
            username = null
            savedCredentials = false
            restoredSession = false
        }
    }

    @Test
    fun savedSessionState_isDelegated() {

        val backend =
            FakeBackend()

        val manager =
            SessionManager(
                backend
            )

        assertTrue(
            manager.hasSavedCredentials()
        )

        assertEquals(
            "saved-user",
            manager.savedUsername()
        )

        assertTrue(
            manager.hasRestoredSession()
        )
    }

    @Test
    fun login_delegatesCredentialsExactly() {

        val backend =
            FakeBackend()

        val expected =
            LoginResult.InvalidCredentials

        backend.loginResult =
            expected

        val manager =
            SessionManager(
                backend
            )

        val result =
            manager.login(
                "user123",
                "secret456"
            )

        assertSame(
            expected,
            result
        )

        assertEquals(
            1,
            backend.loginCalls
        )

        assertEquals(
            "user123",
            backend.loginUsername
        )

        assertEquals(
            "secret456",
            backend.loginPassword
        )
    }

    @Test
    fun resumeSession_isDelegated() {

        val backend =
            FakeBackend()

        val expected =
            LoginResult.CaptchaRequired

        backend.resumeResult =
            expected

        val manager =
            SessionManager(
                backend
            )

        assertSame(
            expected,
            manager.resumeSavedLogin()
        )

        assertEquals(
            1,
            backend.resumeCalls
        )
    }

    @Test
    fun fullRelogin_isDelegated() {

        val backend =
            FakeBackend()

        val expected =
            LoginResult.InvalidCredentials

        backend.reloginResult =
            expected

        val manager =
            SessionManager(
                backend
            )

        assertSame(
            expected,
            manager.loginWithSavedCredentials()
        )

        assertEquals(
            1,
            backend.reloginCalls
        )
    }

    @Test
    fun tokenRefresh_isDelegated() {

        val backend =
            FakeBackend()

        backend.refreshResult =
            false

        val manager =
            SessionManager(
                backend
            )

        assertFalse(
            manager.refreshOAuthToken()
        )

        assertEquals(
            1,
            backend.refreshCalls
        )
    }

    @Test
    fun logout_clearsSavedSession() {

        val backend =
            FakeBackend()

        val manager =
            SessionManager(
                backend
            )

        manager.logout()

        assertEquals(
            1,
            backend.clearCalls
        )

        assertFalse(
            manager.hasSavedCredentials()
        )

        assertFalse(
            manager.hasRestoredSession()
        )

        assertNull(
            manager.savedUsername()
        )
    }
}
