package com.hmgchallenge.pleosstarter.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 계획서 1-3 "데이터 모델"과 1:1로 맞춘 테이블입니다. 바꿀 때는 A·B·C 담당 모두 합의하세요.

// ── A가 저장 ──────────────────────────────────────────

@Entity
data class Trip(
    @PrimaryKey val tripId: String,       // UUID
    val startedAt: Long,
    val endedAt: Long?,
    val source: String,                   // TripSource 이름: "VEHICLE" | "REPLAY"
    val distanceKm: Float?                // 속도 적분으로 계산
)

enum class TripSource { VEHICLE, REPLAY }

@Entity(primaryKeys = ["tripId", "t"])
data class TripSample(
    val tripId: String,
    val t: Long,                          // epoch ms, 1초 간격
    val speedKmh: Float?                  // 값이 없으면 null (가짜 값 금지)
)

@Entity(indices = [Index("tripId")])
data class DrivingNote(
    @PrimaryKey val noteId: String,
    val tripId: String,
    val t: Long,                          // 메모를 남긴 시각
    val rawText: String,                  // 사용자가 남긴 원문 (버튼이면 버튼 이름)
    val category: String                  // NoteCategory 이름
)

enum class NoteCategory { LEFT_TURN, RIGHT_TURN, U_TURN, LANE_CHANGE, PARKING, SPEED_CONTROL, OTHER }

// ── B가 저장 ──────────────────────────────────────────

@Entity(indices = [Index("tripId")])
data class TripEvent(
    @PrimaryKey val eventId: String,
    val tripId: String,
    val type: String,                     // EventType 이름
    val t: Long,
    val severity: Int,                    // 1~3
    val deltaKmh: Float                   // 1초간 속도 변화
)

enum class EventType { HARSH_START, HARSH_ACCEL, HARSH_BRAKE }

@Entity
data class TripReport(
    @PrimaryKey val tripId: String,
    val createdAt: Long,
    val headline: String,
    val goodPoints: String,               // JSON 배열
    val practicePoints: String,           // JSON 배열
    val skillScores: String               // JSON {"SMOOTH_ACCEL":82,"BRAKING":60} ← C의 입력
)

enum class Skill { SMOOTH_ACCEL, BRAKING }   // 메모 분류는 NoteCategory로 따로 집계

// ── C가 저장 ──────────────────────────────────────────

@Entity
data class Quest(
    @PrimaryKey val questId: String,
    val templateId: String,
    val title: String,
    val reason: String,
    val goalType: String,                 // "AUTO" | "SELF_CHECK"
    val target: Int,
    val progress: Int,
    val status: String,                   // "ACTIVE" | "DONE"
    val createdAt: Long
)
