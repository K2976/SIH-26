package com.fitforge.app.core.database

import kotlinx.datetime.Instant

/** Whether a locally-written row has reached the server yet. */
enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED,
}

/**
 * Columns every diary entity carries. Room has no entity inheritance, so
 * embed this with `@Embedded` in each `@Entity` rather than extending it.
 * A `@PrimaryKey` here would be silently ignored by Room once embedded —
 * each entity declares `primaryKeys = ["clientUuid"]` on its own `@Entity`.
 */
data class BaseDiaryColumns(
    val clientUuid: String,
    val occurredAt: Instant,
    /** e.g. "+05:30" — needed for the 04:00-local-day rollover on read. */
    val utcOffset: String,
    val source: String,
    val createdAt: Instant,
    val syncStatus: SyncStatus,
    /** client_uuid of the row this one corrects. Rows are never edited in place. */
    val supersedes: String? = null,
)
