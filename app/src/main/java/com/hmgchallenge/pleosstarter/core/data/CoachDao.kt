package com.hmgchallenge.pleosstarter.core.data

import androidx.room.*
import com.hmgchallenge.pleosstarter.core.data.entity.*

/** Used only on the shared repository's IO worker. The team's suspend TripDao stays available. */
@Dao
interface CoachDao {
    @Query("SELECT * FROM AppSnapshot WHERE `key` = :key") fun snapshot(key: String): AppSnapshot?
    @Query("INSERT OR IGNORE INTO AppSnapshot (`key`,value,updatedAt) SELECT 'recommendation-archive:' || tripId,'1',:at FROM Trip WHERE endedAt IS NOT NULL")
    fun archiveRecommendationInputs(at: Long)
    @Upsert fun saveSnapshot(value: AppSnapshot)
    @Query("DELETE FROM PracticeCompletion WHERE profile = :profile") fun clearCompletions(profile: String)
    @Upsert fun saveCompletions(value: List<PracticeCompletion>)
    @Query("SELECT * FROM PracticeCompletion WHERE profile=:profile ORDER BY at DESC") fun completions(profile: String): List<PracticeCompletion>
    @Query("UPDATE PracticeCompletion SET profile=:archive WHERE profile='demo'") fun archiveDemoCompletions(archive: String)
    @Query("SELECT * FROM PracticeCompletion WHERE tripId=:id ORDER BY at") fun tripCompletions(id: String): List<PracticeCompletion>
    @Insert(onConflict=OnConflictStrategy.IGNORE) fun saveEvent(event: CoachingEvent): Long
    @Query("SELECT * FROM CoachingEvent WHERE tripId=:tripId ORDER BY at,id") fun events(tripId: String): List<CoachingEvent>
    @Query("SELECT * FROM CoachingEvent WHERE type='LEGACY_COACH' ORDER BY at,id") fun journal(): List<CoachingEvent>
    @Query("SELECT * FROM CoachingEvent WHERE type='DIFFICULTY' ORDER BY at DESC LIMIT 100") fun difficulties(): List<CoachingEvent>
    @Query("SELECT r.* FROM CoachingEvent r INNER JOIN PracticeCompletion p ON r.id=p.id || ':reward' WHERE p.profile=:profile")
    fun rewards(profile: String): List<CoachingEvent>
    @Query("""SELECT e.payload FROM CoachingEvent e JOIN Trip t ON t.tripId = e.tripId
        WHERE t.source = 'REPLAY' AND e.type = 'SCENARIO_PRACTICED'
        AND NOT EXISTS (SELECT 1 FROM AppSnapshot a WHERE a.key = 'recommendation-archive:' || t.tripId)""")
    fun replayPracticeEvents(): List<String>

    @Upsert fun saveTrip(value: Trip)
    @Query("SELECT * FROM Trip WHERE tripId=:id") fun trip(id: String): Trip?
    @Query("SELECT * FROM Trip ORDER BY startedAt DESC LIMIT :limit") fun recentTrips(limit: Int): List<Trip>
    @Query("SELECT * FROM Trip WHERE source=:source AND endedAt IS NOT NULL AND NOT EXISTS (SELECT 1 FROM AppSnapshot a WHERE a.`key` = 'recommendation-archive:' || Trip.tripId) ORDER BY startedAt DESC LIMIT :limit")
    fun recentTripsBySource(source: String,limit: Int): List<Trip>
    @Upsert fun saveSample(value: TripSample)
    @Query("SELECT * FROM TripSample WHERE tripId=:id ORDER BY t") fun samples(id: String): List<TripSample>
    @Insert(onConflict=OnConflictStrategy.IGNORE) fun saveNote(value: DrivingNote): Long
    @Query("SELECT * FROM DrivingNote WHERE tripId=:id ORDER BY t") fun notes(id: String): List<DrivingNote>
    @Query("SELECT * FROM DrivingNote ORDER BY t DESC LIMIT 100") fun recentNotes(): List<DrivingNote>
    @Upsert fun saveReport(value: TripReport)
    @Query("SELECT * FROM TripReport WHERE tripId=:id") fun report(id: String): TripReport?
    @Query("SELECT * FROM TripReport ORDER BY createdAt DESC LIMIT :limit") fun recentReports(limit: Int): List<TripReport>
    @Query("DELETE FROM TripEvent WHERE tripId=:id AND eventId LIKE :ownedPrefix") fun clearSpeedEvents(id: String,ownedPrefix: String)
    @Insert fun saveSpeedEvents(events: List<TripEvent>)
    @Query("SELECT * FROM TripEvent WHERE tripId=:id ORDER BY t") fun speedEvents(id: String): List<TripEvent>
    @Upsert fun saveQuests(value: List<Quest>)
    @Query("DELETE FROM Quest WHERE questId LIKE :prefix") fun clearQuests(prefix: String)
    @Query("SELECT COUNT(*) FROM Quest") fun questRows(): Int
}
