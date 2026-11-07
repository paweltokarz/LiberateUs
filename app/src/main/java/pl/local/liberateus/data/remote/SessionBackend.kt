package pl.local.liberateus.data.remote

import pl.local.liberateus.data.LoginResult

/**
 * Wewnętrzny kontrakt warstwy sesji.
 *
 * Pozwala testować SessionManager bez Android Context
 * i bez wykonywania prawdziwych requestów HTTP.
 */
internal interface SessionBackend {

    fun login(
        username: String,
        password: String
    ): LoginResult

    fun hasSavedCredentials():
        Boolean

    fun savedUsername():
        String?

    fun hasRestoredSession():
        Boolean

    fun loginWithSavedCredentials():
        LoginResult

    fun resumeSavedLogin():
        LoginResult

    fun refreshOAuthToken():
        Boolean

    fun clearSavedLogin()
}

/**
 * Tymczasowy adapter do istniejącego,
 * sprawdzonego LibrusAuthClient.
 *
 * Nie zmieniamy jeszcze kodu OAuth/cookies.
 */
internal class LibrusSessionBackend(
    connection: LibrusConnection
) : SessionBackend {

    private val client =
        connection.legacyClient

    override fun login(
        username: String,
        password: String
    ): LoginResult =
        client.login(
            username,
            password
        )

    override fun hasSavedCredentials():
        Boolean =
        client.hasSavedCredentials()

    override fun savedUsername():
        String? =
        client.savedUsername()

    override fun hasRestoredSession():
        Boolean =
        client.hasRestoredSession()

    override fun loginWithSavedCredentials():
        LoginResult =
        client.loginWithSavedCredentials()

    override fun resumeSavedLogin():
        LoginResult =
        client.resumeSavedLogin()

    override fun refreshOAuthToken():
        Boolean =
        client.refreshOAuthToken()

    override fun clearSavedLogin() {
        client.clearSavedLogin()
    }
}
