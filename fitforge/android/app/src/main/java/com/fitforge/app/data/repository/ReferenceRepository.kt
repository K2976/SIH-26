package com.fitforge.app.data.repository

import android.content.Context
import com.fitforge.app.core.database.ExerciseDao
import com.fitforge.app.core.database.ExerciseEntity
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.ReferenceApi
import com.fitforge.app.data.remote.generated.models.Exercise
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ReferenceBundleDto(
    @SerialName("reference_version") val referenceVersion: Int,
    val exercises: List<ExerciseBundleDto>,
)

@Serializable
private data class ExerciseBundleDto(
    val slug: String,
    val name: Map<String, String>,
    val instructions: Map<String, String>,
    @SerialName("primary_muscles") val primaryMuscles: List<String> = emptyList(),
    @SerialName("secondary_muscles") val secondaryMuscles: List<String> = emptyList(),
    val equipment: String = "",
    @SerialName("movement_pattern") val movementPattern: String,
    @SerialName("met_value") val metValue: String,
    @SerialName("is_bodyweight") val isBodyweight: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at_version") val updatedAtVersion: Int = 0,
)

private fun ExerciseBundleDto.toEntity() = ExerciseEntity(
    slug = slug,
    name = name["en"] ?: name.values.firstOrNull() ?: slug,
    instructions = instructions["en"] ?: instructions.values.firstOrNull() ?: "",
    primaryMuscles = primaryMuscles.joinToString(", "),
    secondaryMuscles = secondaryMuscles.joinToString(", "),
    equipment = equipment,
    movementPattern = movementPattern,
    metValue = metValue.toDouble(),
    isBodyweight = isBodyweight,
    isActive = isActive,
    updatedAtVersion = updatedAtVersion,
)

@Suppress("UNCHECKED_CAST")
private fun Exercise.toEntity(): ExerciseEntity {
    val nameMap = name as? Map<String, String>
    val instructionsMap = instructions as? Map<String, String>
    val primary = (primaryMuscles as? List<String>).orEmpty()
    val secondary = (secondaryMuscles as? List<String>).orEmpty()
    return ExerciseEntity(
        slug = slug,
        name = nameMap?.get("en") ?: nameMap?.values?.firstOrNull() ?: slug,
        instructions = instructionsMap?.get("en") ?: instructionsMap?.values?.firstOrNull() ?: "",
        primaryMuscles = primary.joinToString(", "),
        secondaryMuscles = secondary.joinToString(", "),
        equipment = equipment ?: "",
        movementPattern = movementPattern.value,
        metValue = metValue.toDouble(),
        isBodyweight = isBodyweight ?: false,
        isActive = isActive ?: true,
        updatedAtVersion = updatedAtVersion ?: 0,
    )
}

/**
 * Exercise library cache: seeded once from the bundled asset so a fresh
 * install can search/log offline before its first sync, then kept fresh by
 * [com.fitforge.app.sync.ReferenceSyncWorker] pulling deltas.
 */
@Singleton
class ReferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val referenceApi: ReferenceApi,
    private val preferences: UserPreferencesDataStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedFromAssetsIfEmpty() {
        if (exerciseDao.count() > 0) return
        val raw = context.assets.open("reference_bundle.json").bufferedReader().use { it.readText() }
        val bundle = json.decodeFromString(ReferenceBundleDto.serializer(), raw)
        exerciseDao.upsertWithSearchIndex(bundle.exercises.map { it.toEntity() })
        preferences.setReferenceVersion(bundle.referenceVersion)
    }

    suspend fun syncDelta() {
        val manifest = referenceApi.referenceManifestRetrieve().body() ?: return
        val localVersion = preferences.referenceVersion.first()
        if (manifest.referenceVersion <= localVersion) return

        val delta = referenceApi.referenceExercisesList(since = localVersion).body().orEmpty()
        if (delta.isNotEmpty()) {
            exerciseDao.upsertWithSearchIndex(delta.map { it.toEntity() })
        }
        preferences.setReferenceVersion(manifest.referenceVersion)
    }
}
