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
 */
data class BaseDiaryColumns(
    val clientUuid: String,
    val occurredAt: Instant,
    val source: String,
    val createdAt: Instant,
    val syncStatus: SyncStatus,
)
