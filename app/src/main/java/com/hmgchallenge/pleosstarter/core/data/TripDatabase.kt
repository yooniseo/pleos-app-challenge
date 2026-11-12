package com.hmgchallenge.pleosstarter.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hmgchallenge.pleosstarter.core.data.entity.DrivingNote
import com.hmgchallenge.pleosstarter.core.data.entity.Quest
import com.hmgchallenge.pleosstarter.core.data.entity.Trip
import com.hmgchallenge.pleosstarter.core.data.entity.TripEvent
import com.hmgchallenge.pleosstarter.core.data.entity.TripReport
import com.hmgchallenge.pleosstarter.core.data.entity.TripSample

@Database(
    entities = [Trip::class, TripSample::class, DrivingNote::class, TripEvent::class, TripReport::class, Quest::class],
    version = 1,
    exportSchema = false
)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile private var instance: TripDatabase? = null

        /** 앱 전체에서 하나만 사용합니다. 개발 중 테이블이 바뀌면 기존 데이터는 지워집니다. */
        fun get(context: Context): TripDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TripDatabase::class.java, "trips.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
