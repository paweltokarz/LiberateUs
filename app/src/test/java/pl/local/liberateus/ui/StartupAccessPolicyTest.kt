package pl.local.liberateus.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.local.liberateus.data.LoginResult

class StartupAccessPolicyTest {

    @Test
    fun success_entersOnlineMode() {

        assertEquals(
            StartupAccessMode.ONLINE,
            resolveStartupAccess(
                hasLocalAccount = true,
                result =
                    LoginResult.Success
            )
        )
    }

    @Test
    fun invalidCredentials_alwaysRequireLogin() {

        assertEquals(
            StartupAccessMode.LOGIN_REQUIRED,
            resolveStartupAccess(
                hasLocalAccount = true,
                result =
                    LoginResult.InvalidCredentials
            )
        )
    }

    @Test
    fun failureWithLocalAccount_entersLocalMode() {

        assertEquals(
            StartupAccessMode.LOCAL,
            resolveStartupAccess(
                hasLocalAccount = true,
                result =
                    LoginResult.Failure(
                        "network unavailable"
                    )
            )
        )
    }

    @Test
    fun failureWithoutLocalAccount_requiresLogin() {

        assertEquals(
            StartupAccessMode.LOGIN_REQUIRED,
            resolveStartupAccess(
                hasLocalAccount = false,
                result =
                    LoginResult.Failure(
                        "network unavailable"
                    )
            )
        )
    }

    @Test
    fun captchaWithLocalAccount_entersLocalMode() {

        assertEquals(
            StartupAccessMode.LOCAL,
            resolveStartupAccess(
                hasLocalAccount = true,
                result =
                    LoginResult.CaptchaRequired
            )
        )
    }

    @Test
    fun captchaWithoutLocalAccount_requiresLogin() {

        assertEquals(
            StartupAccessMode.LOGIN_REQUIRED,
            resolveStartupAccess(
                hasLocalAccount = false,
                result =
                    LoginResult.CaptchaRequired
            )
        )
    }
}
