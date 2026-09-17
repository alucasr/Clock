package org.fossify.clock.databases

import android.content.Context
import android.media.RingtoneManager
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.clock.extensions.config
import org.fossify.clock.helpers.Converters
import org.fossify.clock.interfaces.RoutineDao
import org.fossify.clock.interfaces.RoutineGroupDao
import org.fossify.clock.interfaces.TimerDao
import org.fossify.clock.models.ROUTINE_STYLE_CONTINUOUS
import org.fossify.clock.models.Routine
import org.fossify.clock.models.RoutineGroup
import org.fossify.clock.models.Timer
import org.fossify.clock.models.TimerState
import org.fossify.commons.extensions.getDefaultAlarmSound
import java.util.concurrent.Executors

@Database(entities = [Timer::class, Routine::class, RoutineGroup::class], version = 7)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun TimerDao(): TimerDao

    abstract fun RoutineDao(): RoutineDao

    abstract fun RoutineGroupDao(): RoutineGroupDao

    companion object {
        private var db: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            if (db == null) {
                synchronized(AppDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
                            .fallbackToDestructiveMigration()
                            .addMigrations(
                                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                                MIGRATION_5_6, migration6to7(context)
                            )
                            .addCallback(object : Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    insertDefaultTimer(context)
                                    insertDefaultRoutines(context)
                                }
                            })
                            .build()
                    }
                }
            }
            return db!!
        }

        private fun insertDefaultTimer(context: Context) {
            Executors.newSingleThreadScheduledExecutor().execute {
                val config = context.config
                db!!.TimerDao().insertOrUpdateTimer(
                    Timer(
                        id = null,
                        seconds = config.timerSeconds,
                        state = TimerState.Idle,
                        vibrate = config.timerVibrate,
                        soundUri = config.timerSoundUri,
                        soundTitle = config.timerSoundTitle,
                        label = config.timerLabel ?: "",
                        createdAt = System.currentTimeMillis(),
                        channelId = config.timerChannelId,
                    )
                )
            }
        }

        private fun insertDefaultRoutines(context: Context) {
            Executors.newSingleThreadScheduledExecutor().execute {
                val defaultAlarmSound = context.getDefaultAlarmSound(RingtoneManager.TYPE_ALARM)
                val workGroupId = db!!.RoutineGroupDao().insertOrUpdateRoutineGroup(
                    RoutineGroup(id = 0, title = "Trabajo", isEnabled = true)
                ).toInt()

                db!!.RoutineDao().insertOrUpdateRoutine(
                    Routine(
                        id = null,
                        label = "Estirar las piernas",
                        groupId = workGroupId,
                        intervalSeconds = 30 * 60,
                        isEnabled = true,
                        startTimeMinutes = 9 * 60,
                        endTimeMinutes = 18 * 60,
                        days = 31, // Mon-Fri
                        vibrate = true,
                        soundUri = defaultAlarmSound.uri,
                        soundTitle = defaultAlarmSound.title,
                        notificationStyle = ROUTINE_STYLE_CONTINUOUS,
                    )
                )

                db!!.RoutineDao().insertOrUpdateRoutine(
                    Routine(
                        id = null,
                        label = "5 min deporte",
                        groupId = null,
                        intervalSeconds = 90 * 60,
                        isEnabled = true,
                        startTimeMinutes = 9 * 60,
                        endTimeMinutes = 18 * 60,
                        days = 31, // Mon-Fri
                        vibrate = true,
                        soundUri = defaultAlarmSound.uri,
                        soundTitle = defaultAlarmSound.title,
                        notificationStyle = ROUTINE_STYLE_CONTINUOUS,
                    )
                )

                db!!.RoutineDao().insertOrUpdateRoutine(
                    Routine(
                        id = null,
                        label = "Descansar los ojos-Mirar larga distancia",
                        groupId = workGroupId,
                        intervalSeconds = 30 * 60,
                        isEnabled = true,
                        startTimeMinutes = 9 * 60,
                        endTimeMinutes = 18 * 60,
                        days = 31, // Mon-Fri
                        vibrate = true,
                        soundUri = defaultAlarmSound.uri,
                        soundTitle = defaultAlarmSound.title,
                        notificationStyle = ROUTINE_STYLE_CONTINUOUS,
                    )
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `timers` ADD COLUMN `oneShot` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // default to createdAt so existing timers keep their current relative order
                // until they are used for the first time
                db.execSQL("ALTER TABLE `timers` ADD COLUMN `lastUsedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `timers` SET `lastUsedAt` = `createdAt`")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routines` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "`label` TEXT NOT NULL, " +
                        "`groupId` INTEGER, " +
                        "`intervalMinutes` INTEGER NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`startTimeMinutes` INTEGER NOT NULL, " +
                        "`endTimeMinutes` INTEGER NOT NULL, " +
                        "`days` INTEGER NOT NULL, " +
                        "`vibrate` INTEGER NOT NULL, " +
                        "`soundUri` TEXT NOT NULL, " +
                        "`soundTitle` TEXT NOT NULL, " +
                        "`notificationStyle` INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Routine groups are independent from alarm groups (which live in a separate
                // legacy SQLite database via DBHelper) -- own table, own CRUD.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine_groups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // intervalMinutes -> intervalSeconds: rename the column (SQLite 3.25+, Room's
                // bundled driver supports it) then convert the stored value so existing
                // routines keep the same real-world interval.
                db.execSQL("ALTER TABLE `routines` RENAME COLUMN `intervalMinutes` TO `intervalSeconds`")
                db.execSQL("UPDATE `routines` SET `intervalSeconds` = `intervalSeconds` * 60")
            }
        }

        private fun migration6to7(context: Context): Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Adds the 3rd default routine ("Descansar los ojos") to databases that already
                // migrated past v6 before this routine was introduced. Uses the existing "Trabajo"
                // group if present (created by insertDefaultRoutines/onCreate); falls back to no
                // group if it was renamed/deleted by the user.
                val cursor = db.query("SELECT id FROM routine_groups WHERE title = 'Trabajo' LIMIT 1")
                val workGroupId: Int? = if (cursor.moveToFirst()) cursor.getInt(0) else null
                cursor.close()

                val exists = db.query(
                    "SELECT COUNT(*) FROM routines WHERE label = 'Descansar los ojos-Mirar larga distancia'"
                )
                val alreadyExists = exists.moveToFirst() && exists.getInt(0) > 0
                exists.close()
                if (alreadyExists) return

                val defaultAlarmSound = context.getDefaultAlarmSound(RingtoneManager.TYPE_ALARM)
                db.execSQL(
                    "INSERT INTO routines (label, groupId, intervalSeconds, isEnabled, " +
                        "startTimeMinutes, endTimeMinutes, days, vibrate, soundUri, soundTitle, notificationStyle) " +
                        "VALUES ('Descansar los ojos-Mirar larga distancia', " +
                        (workGroupId?.toString() ?: "NULL") + ", 1800, 1, 540, 1080, 31, 1, " +
                        "'${defaultAlarmSound.uri.replace("'", "''")}', " +
                        "'${defaultAlarmSound.title.replace("'", "''")}', 0)"
                )
            }
        }
    }
}
