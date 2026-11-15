package com.hmgchallenge.pleosstarter.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hmgchallenge.pleosstarter.core.data.entity.AppSnapshot
import com.hmgchallenge.pleosstarter.core.data.entity.PracticeCompletion
import com.hmgchallenge.pleosstarter.core.data.entity.CoachingEvent
import com.hmgchallenge.pleosstarter.core.data.entity.DrivingNote
import com.hmgchallenge.pleosstarter.core.data.entity.Quest
import com.hmgchallenge.pleosstarter.core.data.entity.Trip
import com.hmgchallenge.pleosstarter.core.data.entity.TripEvent
import com.hmgchallenge.pleosstarter.core.data.entity.TripReport
import com.hmgchallenge.pleosstarter.core.data.entity.TripSample

@Database(
    entities = [Trip::class, TripSample::class, DrivingNote::class, TripEvent::class, TripReport::class, Quest::class,
        AppSnapshot::class, PracticeCompletion::class, CoachingEvent::class],
    version = 2,
    exportSchema = false
)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun coachDao(): CoachDao

    companion object {
        @Volatile private var instance: TripDatabase? = null

        val MIGRATION_1_2=object: Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `AppSnapshot` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `PracticeCompletion` (`id` TEXT NOT NULL, `profile` TEXT NOT NULL, `tripId` TEXT, `skill` TEXT NOT NULL, `source` TEXT NOT NULL, `at` INTEGER NOT NULL, `difficult` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PracticeCompletion_tripId` ON `PracticeCompletion` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PracticeCompletion_profile_skill` ON `PracticeCompletion` (`profile`,`skill`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `CoachingEvent` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `type` TEXT NOT NULL, `at` INTEGER NOT NULL, `source` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_CoachingEvent_tripId` ON `CoachingEvent` (`tripId`)")
            }
        }
        /** One shared DB; upgrades preserve existing trip records. */
        fun get(context: Context): TripDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TripDatabase::class.java, "trips.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
