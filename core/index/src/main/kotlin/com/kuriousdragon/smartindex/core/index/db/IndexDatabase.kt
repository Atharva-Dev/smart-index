package com.kuriousdragon.smartindex.core.index.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.kuriousdragon.smartindex.core.index.db.dao.MaintenanceDao
import com.kuriousdragon.smartindex.core.index.db.dao.ReadDao
import com.kuriousdragon.smartindex.core.index.db.dao.SearchDao
import com.kuriousdragon.smartindex.core.index.db.dao.WriteDao
import com.kuriousdragon.smartindex.core.index.db.entity.ContactNameEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemDateEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemKeyEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemParticipantEntity
import com.kuriousdragon.smartindex.core.index.db.entity.RecordingLinkEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ShortTextEntity
import com.kuriousdragon.smartindex.core.index.db.entity.SyncStateEntity
import kotlinx.coroutines.Dispatchers

/** The index database (LLD §4.1). DAO accessors are added by later tickets. */
@Database(
    entities = [
        ItemEntity::class,
        ItemKeyEntity::class,
        ItemDateEntity::class,
        ShortTextEntity::class,
        RecordingLinkEntity::class,
        ContactNameEntity::class,
        SyncStateEntity::class,
        ItemParticipantEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class IndexDatabase : RoomDatabase() {
    internal abstract fun writeDao(): WriteDao

    internal abstract fun maintenanceDao(): MaintenanceDao

    internal abstract fun searchDao(): SearchDao

    internal abstract fun readDao(): ReadDao

    companion object {
        const val NAME = "index.db"

        /** v2 (SIDX-108): further participants of a group message. Same DDL Room exports to 2.json. */
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `item_participant` (`item_id` INTEGER NOT NULL, " +
                            "`number_norm` TEXT NOT NULL, PRIMARY KEY(`item_id`, `number_norm`), " +
                            "FOREIGN KEY(`item_id`) REFERENCES `item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS `idx_item_participant_norm` ON `item_participant` (`number_norm`)",
                    )
                }
            }

        /** The file database; [name] may be an absolute path (the JVM benchmark uses a temporary folder). */
        fun build(context: Context, name: String = NAME): IndexDatabase =
            Room.databaseBuilder(context.applicationContext, IndexDatabase::class.java, name)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(FtsSchema.Callback)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()

        fun inMemory(context: Context): IndexDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, IndexDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(FtsSchema.Callback)
                .build()
    }
}
