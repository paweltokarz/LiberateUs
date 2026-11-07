package pl.local.liberateus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MessageEntity::class,
        MessageContentEntity::class,
        DraftEntity::class,
        SentMessageEntity::class,
        SentMessageContentEntity::class,
        SyncStateEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class LiberateDatabase :
    RoomDatabase() {

    abstract fun messageDao(): MessageDao

    abstract fun messageContentDao():
        MessageContentDao

    abstract fun syncStateDao():
        SyncStateDao

    
    abstract fun draftDao(): DraftDao

    
    abstract fun sentMessageDao():
        SentMessageDao

    abstract fun sentMessageContentDao():
        SentMessageContentDao

    companion object {
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS sent_message_contents (
                            accountId TEXT NOT NULL,
                            messageId TEXT NOT NULL,
                            content TEXT NOT NULL,
                            fetchedAt INTEGER NOT NULL,
                            PRIMARY KEY(accountId, messageId),
                            FOREIGN KEY(accountId, messageId)
                                REFERENCES sent_messages(accountId, messageId)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                            index_sent_message_contents_accountId_messageId
                        ON sent_message_contents(accountId, messageId)
                        """.trimIndent()
                    )
                }
            }


        private val MIGRATION_2_3 =
            object : Migration(
                2,
                3
            ) {
                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS sent_messages (
                            accountId TEXT NOT NULL,
                            messageId TEXT NOT NULL,
                            receiver TEXT NOT NULL,
                            receiverAccountId TEXT,
                            receiverUuid TEXT,
                            topic TEXT NOT NULL,
                            body TEXT NOT NULL,
                            sendDateRaw TEXT NOT NULL,
                            hasAttachment INTEGER NOT NULL,
                            category TEXT,
                            remoteOrder INTEGER NOT NULL,
                            lastSeenAt INTEGER NOT NULL,
                            PRIMARY KEY(accountId, messageId)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                            index_sent_messages_accountId
                        ON sent_messages(accountId)
                        """.trimIndent()
                    )
                }
            }


        private val MIGRATION_1_2 =
            object : Migration(
                1,
                2
            ) {
                override fun migrate(
                    db: SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS drafts (
                            accountId TEXT NOT NULL,
                            draftId TEXT NOT NULL,
                            ownerId TEXT,
                            parentMessageId TEXT,
                            dateAddedRaw TEXT,
                            deleted INTEGER NOT NULL,
                            hasAttachment INTEGER NOT NULL,
                            topic TEXT NOT NULL,
                            body TEXT NOT NULL,
                            receiversJson TEXT NOT NULL,
                            type TEXT,
                            remoteOrder INTEGER NOT NULL,
                            lastSeenAt INTEGER NOT NULL,
                            PRIMARY KEY(accountId, draftId)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                            index_drafts_accountId
                        ON drafts(accountId)
                        """.trimIndent()
                    )
                }
            }


        const val DATABASE_NAME =
            "liberate_us.db"

        @Volatile
        private var instance:
            LiberateDatabase? = null

        fun getInstance(
            context: Context
        ): LiberateDatabase {

            return instance
                ?: synchronized(this) {

                    instance
                        ?: Room.databaseBuilder(
                            context.applicationContext,
                            LiberateDatabase::class.java,
                            DATABASE_NAME
                        )
                            /*
                             * Nie używamy
                             * fallbackToDestructiveMigration().
                             *
                             * Od wersji 1 baza ma mieć
                             * normalne migracje.
                             */
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
.build()
                            .also {
                                instance = it
                            }
                }
        }
    }
}
