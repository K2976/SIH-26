package com.fitforge.app.core.di

import com.fitforge.app.data.remote.generated.models.PatchedUserProfile
import com.fitforge.app.data.remote.generated.models.UnitPreferenceEnum
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Regression test: onboarding's "Next" button on the Basics step crashed on
 * device with a SerializationException because the app's shared Json
 * instance had no contextual serializer for BigDecimal — required as soon as
 * the schema regen correctly typed height_cm/current_weight_kg as decimals
 * instead of the stale plain-string snapshot. NetworkModule.provideJson()
 * now merges in the generator's own adapters; this proves it holds for the
 * exact object saveBasics() constructs.
 */
class NetworkModuleJsonTest {
    @Test
    fun `encodes a PatchedUserProfile with decimal and enum fields without throwing`() {
        val json = NetworkModule.provideJson()

        val encoded = json.encodeToString(
            PatchedUserProfile(
                dateOfBirth = LocalDate(1995, 6, 15),
                sexAtBirth = PatchedUserProfile.SexAtBirth.male,
                heightCm = BigDecimal("178.5"),
                currentWeightKg = BigDecimal("82.3"),
                unitPreference = UnitPreferenceEnum.metric,
                timezone = "Asia/Kolkata",
            ),
        )

        assertTrue(encoded.contains("\"178.5\""))
        assertTrue(encoded.contains("\"82.3\""))
    }

    @Test
    fun `encodes declined_fields, an Any-typed field, without throwing`() {
        val json = NetworkModule.provideJson()

        val encoded = json.encodeToString(
            PatchedUserProfile(declinedFields = listOf("waist_cm", "resting_hr")),
        )

        assertTrue(encoded.contains("waist_cm"))
    }
}
