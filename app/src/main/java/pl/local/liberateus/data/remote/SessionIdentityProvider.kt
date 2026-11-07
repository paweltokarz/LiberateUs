package pl.local.liberateus.data.remote

/**
 * Minimal session information needed by repositories.
 *
 * Repository does not need to know OAuth/cookie details.
 */
interface SessionIdentityProvider {

    fun savedUsername():
        String?
}
