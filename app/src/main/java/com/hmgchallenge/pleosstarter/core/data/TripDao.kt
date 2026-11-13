package com.hmgchallenge.pleosstarter.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.hmgchallenge.pleosstarter.core.data.entity.DrivingNote
import com.hmgchallenge.pleosstarter.core.data.entity.Quest
import com.hmgchallenge.pleosstarter.core.data.entity.Trip
import com.hmgchallenge.pleosstarter.core.data.entity.TripEvent
import com.hmgchallenge.pleosstarter.core.data.entity.TripReport
import com.hmgchallenge.pleosstarter.core.data.entity.TripSample

@Dao
interface TripDao {
    // ── A: 주행 기록 ──
    @Upsert suspend fun upsertTrip(trip: Trip)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSamples(samples: List<TripSample>)
    @Insert suspend fun insertNote(note: DrivingNote)

    // ── B: 분석 입력 ──
    @Query("SELECT * FROM Trip WHERE tripId = :tripId") suspend fun trip(tripId: String): Trip?
    @Query("SELECT * FROM TripSample WHERE tripId = :tripId ORDER BY t") suspend fun samples(tripId: String): List<TripSample>
    @Query("SELECT * FROM DrivingNote WHERE tripId = :tripId ORDER BY t") suspend fun notes(tripId: String): List<DrivingNote>

    // ── B: 분석 결과 (같은 tripId로 다시 분석하면 이벤트를 갈아끼움) ──
    @Query("DELETE FROM TripEvent WHERE tripId = :tripId") suspend fun deleteEvents(tripId: String)
    @Insert suspend fun insertEvents(events: List<TripEvent>)
    @Transaction suspend fun replaceEvents(tripId: String, events: List<TripEvent>) {
        deleteEvents(tripId); insertEvents(events)
    }
    @Query("SELECT * FROM TripEvent WHERE tripId = :tripId ORDER BY t") suspend fun events(tripId: String): List<TripEvent>
    @Upsert suspend fun upsertReport(report: TripReport)
    @Query("SELECT * FROM TripReport WHERE tripId = :tripId") suspend fun report(tripId: String): TripReport?

    // ── C: 누적 판단 ──
    @Query("SELECT * FROM TripReport ORDER BY createdAt DESC LIMIT :limit") suspend fun recentReports(limit: Int): List<TripReport>
    @Query("""SELECT * FROM DrivingNote WHERE tripId IN
              (SELECT tripId FROM Trip ORDER BY startedAt DESC LIMIT :tripLimit) ORDER BY t""")
    suspend fun notesOfRecentTrips(tripLimit: Int): List<DrivingNote>
    @Upsert suspend fun upsertQuest(quest: Quest)
    @Query("SELECT * FROM Quest WHERE status = :status ORDER BY createdAt") suspend fun quests(status: String): List<Quest>
}
