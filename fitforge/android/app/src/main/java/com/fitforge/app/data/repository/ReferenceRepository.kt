package com.fitforge.app.data.repository

import android.content.Context
import com.fitforge.app.core.database.ExerciseDao
import com.fitforge.app.core.database.ExerciseEntity
import com.fitforge.app.core.database.NO_CYCLE_PHASE
import com.fitforge.app.core.database.TrainingMusicMappingDao
import com.fitforge.app.core.database.TrainingMusicMappingEntity
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.ReferenceApi
import com.fitforge.app.data.remote.generated.models.Exercise
import com.fitforge.app.data.remote.generated.models.TrainingMusicMapping
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    @SerialName("training_music_mappings")
    val trainingMusicMappings: List<TrainingMusicMappingBundleDto> = emptyList(),
)

@Serializable
private data class TrainingMusicMappingBundleDto(
    @SerialName("training_focus") val trainingFocus: String,
    @SerialName("cycle_phase") val cyclePhase: String? = null,
    val genre: String,
    @SerialName("youtube_playlist_id") val youtubePlaylistId: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at_version") val updatedAtVersion: Int = 0,
)

@Serializable
private data class AngleJointDto(val from: String, val vertex: String, val to: String)

@Serializable
private data class PoseConfigDto(
    @SerialName("angle_joint") val angleJoint: AngleJointDto,
    @SerialName("min_rep_seconds") val minRepSeconds: Double,
    @SerialName("max_rep_seconds") val maxRepSeconds: Double,
    @SerialName("symmetry_tolerance_deg") val symmetryToleranceDeg: Double,
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
    @SerialName("pose_config") val poseConfig: PoseConfigDto? = null,
    @SerialName("camera_position") val cameraPosition: String? = null,
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
    cameraPosition = cameraPosition,
    poseAngleFrom = poseConfig?.angleJoint?.from,
    poseAngleVertex = poseConfig?.angleJoint?.vertex,
    poseAngleTo = poseConfig?.angleJoint?.to,
    poseMinRepSeconds = poseConfig?.minRepSeconds,
    poseMaxRepSeconds = poseConfig?.maxRepSeconds,
    poseSymmetryToleranceDeg = poseConfig?.symmetryToleranceDeg,
)

@Suppress("UNCHECKED_CAST")
private fun Exercise.toEntity(): ExerciseEntity {
    val nameMap = name as? Map<String, String>
    val instructionsMap = instructions as? Map<String, String>
    val primary = (primaryMuscles as? List<String>).orEmpty()
    val secondary = (secondaryMuscles as? List<String>).orEmpty()
    val poseConfigMap = poseConfig as? Map<String, Any?>
    val angleJointMap = poseConfigMap?.get("angle_joint") as? Map<String, Any?>
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
        cameraPosition = cameraPosition,
        poseAngleFrom = angleJointMap?.get("from") as? String,
        poseAngleVertex = angleJointMap?.get("vertex") as? String,
        poseAngleTo = angleJointMap?.get("to") as? String,
        poseMinRepSeconds = (poseConfigMap?.get("min_rep_seconds") as? Number)?.toDouble(),
        poseMaxRepSeconds = (poseConfigMap?.get("max_rep_seconds") as? Number)?.toDouble(),
        poseSymmetryToleranceDeg = (poseConfigMap?.get("symmetry_tolerance_deg") as? Number)?.toDouble(),
    )
}

private fun TrainingMusicMappingBundleDto.toEntity() = TrainingMusicMappingEntity(
    trainingFocus = trainingFocus,
    cyclePhase = cyclePhase ?: NO_CYCLE_PHASE,
    genre = genre,
    youtubePlaylistId = youtubePlaylistId,
    isActive = isActive,
    updatedAtVersion = updatedAtVersion,
)

private fun TrainingMusicMapping.toEntity() = TrainingMusicMappingEntity(
    trainingFocus = trainingFocus.value,
    cyclePhase = cyclePhase ?: NO_CYCLE_PHASE,
    genre = genre,
    youtubePlaylistId = youtubePlaylistId,
    isActive = isActive ?: true,
    updatedAtVersion = updatedAtVersion ?: 0,
)

/**
 * Exercise library cache: seeded once from the bundled asset so a fresh
 * install can search/log offline before its first sync, then kept fresh by
 * [com.fitforge.app.sync.ReferenceSyncWorker] pulling deltas.
 */
@Singleton
class ReferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val trainingMusicMappingDao: TrainingMusicMappingDao,
    private val referenceApi: ReferenceApi,
    private val preferences: UserPreferencesDataStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val musicMappings: Flow<List<TrainingMusicMappingEntity>> = trainingMusicMappingDao.getAllActive()

    suspend fun seedFromAssetsIfEmpty() {
        if (exerciseDao.count() > 0) return
        val raw = context.assets.open("reference_bundle.json").bufferedReader().use { it.readText() }
        val bundle = json.decodeFromString(ReferenceBundleDto.serializer(), raw)
        exerciseDao.upsertWithSearchIndex(bundle.exercises.map { it.toEntity() })
        trainingMusicMappingDao.upsertAll(bundle.trainingMusicMappings.map { it.toEntity() })
        preferences.setReferenceVersion(bundle.referenceVersion)
    }

    suspend fun syncDelta() {
        val manifest = referenceApi.referenceManifestRetrieve().body() ?: return
        val localVersion = preferences.referenceVersion.first()
        if (manifest.referenceVersion <= localVersion) return

        val musicDelta = referenceApi.referenceMusicList(since = localVersion).body().orEmpty()
        if (musicDelta.isNotEmpty()) {
            trainingMusicMappingDao.upsertAll(musicDelta.map { it.toEntity() })
        }

        val delta = referenceApi.referenceExercisesList(since = localVersion).body().orEmpty()
        if (delta.isNotEmpty()) {
            exerciseDao.upsertWithSearchIndex(delta.map { it.toEntity() })
        }
        preferences.setReferenceVersion(manifest.referenceVersion)
    }
}
