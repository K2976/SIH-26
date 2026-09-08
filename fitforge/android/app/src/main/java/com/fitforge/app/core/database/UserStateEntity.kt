package com.fitforge.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Local Room cache for the UserState snapshot returned by GET /api/v1/state/.
 * Contains derived state across energy, training, plan adaptation, body trend,
 * and data sufficiency.
 * Single row with id = 1 for the authenticated user.
 */
@Entity(tableName = "user_state")
data class UserStateEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "computed_at")
    val computedAt: Instant,

    @ColumnInfo(name = "as_of_date")
    val asOfDate: String,

    // Profile
    @ColumnInfo(name = "profile_age")
    val profileAge: Int?,
    @ColumnInfo(name = "profile_sex_at_birth")
    val profileSexAtBirth: String?,
    @ColumnInfo(name = "profile_height_cm")
    val profileHeightCm: Double?,
    @ColumnInfo(name = "profile_unit_preference")
    val profileUnitPreference: String?,
    @ColumnInfo(name = "profile_tracks_cycle")
    val profileTracksCycle: Boolean?,
    @ColumnInfo(name = "profile_timezone")
    val profileTimezone: String?,

    // Goal
    @ColumnInfo(name = "goal_energy_direction")
    val goalEnergyDirection: String?,
    @ColumnInfo(name = "goal_training_focus")
    val goalTrainingFocus: String?,
    @ColumnInfo(name = "goal_target_rate_kg_per_week")
    val goalTargetRateKgPerWeek: Double?,
    @ColumnInfo(name = "goal_auto_adjust")
    val goalAutoAdjust: Boolean?,

    // Energy
    @ColumnInfo(name = "energy_maintenance_kcal")
    val energyMaintenanceKcal: Double?,
    @ColumnInfo(name = "energy_maintenance_source")
    val energyMaintenanceSource: String?,
    @ColumnInfo(name = "energy_maintenance_confidence")
    val energyMaintenanceConfidence: Double?,
    @ColumnInfo(name = "energy_target_kcal_today")
    val energyTargetKcalToday: Double?,
    @ColumnInfo(name = "energy_consumed_kcal_today")
    val energyConsumedKcalToday: Double?,
    @ColumnInfo(name = "energy_consumed_kcal_today_partial")
    val energyConsumedKcalTodayPartial: Boolean,
    @ColumnInfo(name = "energy_burned_kcal_today")
    val energyBurnedKcalToday: Double?,
    @ColumnInfo(name = "energy_burned_kcal_today_partial")
    val energyBurnedKcalTodayPartial: Boolean,
    @ColumnInfo(name = "energy_week_balance_kcal")
    val energyWeekBalanceKcal: Double?,
    @ColumnInfo(name = "energy_week_balance_kcal_partial")
    val energyWeekBalanceKcalPartial: Boolean,
    @ColumnInfo(name = "energy_data_sufficiency")
    val energyDataSufficiency: String,

    // Training
    @ColumnInfo(name = "training_week_volume_planned")
    val trainingWeekVolumePlanned: Double?,
    @ColumnInfo(name = "training_week_volume_planned_partial")
    val trainingWeekVolumePlannedPartial: Boolean,
    @ColumnInfo(name = "training_week_volume_completed")
    val trainingWeekVolumeCompleted: Double?,
    @ColumnInfo(name = "training_week_volume_completed_partial")
    val trainingWeekVolumeCompletedPartial: Boolean,
    @ColumnInfo(name = "training_volume_by_muscle_group_json")
    val trainingVolumeByMuscleGroupJson: String?,
    @ColumnInfo(name = "training_volume_by_muscle_group_partial")
    val trainingVolumeByMuscleGroupPartial: Boolean,
    @ColumnInfo(name = "training_adherence_7d")
    val trainingAdherence7d: Double?,
    @ColumnInfo(name = "training_adherence_7d_partial")
    val trainingAdherence7dPartial: Boolean,
    @ColumnInfo(name = "training_readiness_ceiling_today")
    val trainingReadinessCeilingToday: Double?,
    @ColumnInfo(name = "training_days_since_rest")
    val trainingDaysSinceRest: Int?,
    @ColumnInfo(name = "training_days_since_rest_partial")
    val trainingDaysSinceRestPartial: Boolean,

    // PlanDelta
    @ColumnInfo(name = "has_plan_delta")
    val hasPlanDelta: Boolean,
    @ColumnInfo(name = "plan_delta_target_kcal")
    val planDeltaTargetKcal: Double?,
    @ColumnInfo(name = "plan_delta_volume_today_kg")
    val planDeltaVolumeTodayKg: Double?,
    @ColumnInfo(name = "plan_delta_readiness_ceiling")
    val planDeltaReadinessCeiling: Double?,
    @ColumnInfo(name = "plan_delta_dropped_sessions")
    val planDeltaDroppedSessions: Int?,
    @ColumnInfo(name = "plan_delta_reason")
    val planDeltaReason: String?,
    @ColumnInfo(name = "plan_delta_clamped_by_json")
    val planDeltaClampedByJson: String?,

    // Body
    @ColumnInfo(name = "body_latest_weight_kg")
    val bodyLatestWeightKg: Double?,
    @ColumnInfo(name = "body_weight_trend_kg_per_week")
    val bodyWeightTrendKgPerWeek: Double?,
    @ColumnInfo(name = "body_trend_confidence")
    val bodyTrendConfidence: Double?,
    @ColumnInfo(name = "body_fitness_age")
    val bodyFitnessAge: Double?,

    // Cycle
    @ColumnInfo(name = "cycle_enabled")
    val cycleEnabled: Boolean,
    @ColumnInfo(name = "cycle_tracking_mode")
    val cycleTrackingMode: String?,
    @ColumnInfo(name = "cycle_phase")
    val cyclePhase: String?,
    @ColumnInfo(name = "cycle_day")
    val cycleDay: Int?,
    @ColumnInfo(name = "cycle_confidence")
    val cycleConfidence: Double?,
    @ColumnInfo(name = "cycle_days_until_predicted_period")
    val cycleDaysUntilPredictedPeriod: Int?,
    @ColumnInfo(name = "cycle_energy_availability_flag")
    val cycleEnergyAvailabilityFlag: String?,
    @ColumnInfo(name = "cycle_learned_pattern_sample_cycles")
    val cycleLearnedPatternSampleCycles: Int?,
    @ColumnInfo(name = "cycle_learned_pattern_confidence")
    val cycleLearnedPatternConfidence: Double?,

    // Flags
    @ColumnInfo(name = "flags_missing_food_days_7d")
    val flagsMissingFoodDays7d: Int,
    @ColumnInfo(name = "flags_missing_weight_days_14d")
    val flagsMissingWeightDays14d: Int,
    @ColumnInfo(name = "flags_goal_recently_changed")
    val flagsGoalRecentlyChanged: Boolean,
    @ColumnInfo(name = "flags_insufficient_data_for_adaptation")
    val flagsInsufficientDataForAdaptation: Boolean,
)
