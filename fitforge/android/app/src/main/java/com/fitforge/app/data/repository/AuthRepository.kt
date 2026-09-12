package com.fitforge.app.data.repository

import com.fitforge.app.core.database.CalibrationProfileDao
import com.fitforge.app.core.database.UserStateDao
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import javax.inject.Inject

/**
 * Ends the local session. Diary rows (workouts, sets, meals, cycle logs) are
 * deliberately left alone — some may still be `PENDING` sync, and silently
 * discarding unsynced user data on logout would be a data-loss bug, not a
 * privacy fix. Only the derived `UserState` cache and per-exercise
 * calibration profiles are cleared: both are cheap to rebuild from the
 * server and carry no unsynced content, but leaving them would show the next
 * account on this device a stale dashboard snapshot and someone else's
 * curl/squat calibration.
 */
class AuthRepository @Inject constructor(
    private val preferences: UserPreferencesDataStore,
    private val userStateDao: UserStateDao,
    private val calibrationProfileDao: CalibrationProfileDao,
) {
    suspend fun logout() {
        preferences.clearSession()
        userStateDao.deleteAll()
        calibrationProfileDao.deleteAll()
    }
}
