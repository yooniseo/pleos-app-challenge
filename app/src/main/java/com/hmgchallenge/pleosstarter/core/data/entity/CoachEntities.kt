package com.hmgchallenge.pleosstarter.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Existing JSON codecs remain compatible while Room becomes the authoritative store. */
@Entity
data class AppSnapshot(@PrimaryKey val key: String,val value: String,val updatedAt: Long)

@Entity(indices=[Index("tripId"),Index(value=["profile","skill"])])
data class PracticeCompletion(@PrimaryKey val id: String,val profile: String,val tripId: String?,val skill: String,
                              val source: String,val at: Long,val difficult: Boolean)

@Entity(indices=[Index("tripId")])
data class CoachingEvent(@PrimaryKey val id: String,val tripId: String,val type: String,val at: Long,
                         val source: String,val payload: String)
