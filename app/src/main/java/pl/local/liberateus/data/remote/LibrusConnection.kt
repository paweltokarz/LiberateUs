package pl.local.liberateus.data.remote

import android.content.Context
import pl.local.liberateus.data.LibrusAuthClient

/**
 * Jedno współdzielone połączenie z Librusem.
 *
 * Na etapie migracji zarówno SessionManager,
 * jak i przyszły MessagesRemoteDataSource
 * muszą korzystać z TEGO SAMEGO LibrusAuthClient,
 * ponieważ klient przechowuje cookies sesji.
 *
 * Ta klasa jest celowo internal:
 * UI nie powinno jej znać docelowo.
 */
internal class LibrusConnection(
    context: Context
) {

    internal val legacyClient =
        LibrusAuthClient(
            context.applicationContext
        )
}
