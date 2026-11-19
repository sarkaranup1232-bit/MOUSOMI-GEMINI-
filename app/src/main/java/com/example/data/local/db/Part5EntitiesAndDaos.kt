package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
  tableName = "image_versions",
  indices = [Index("rootMediaId"), Index("conversationId"), Index("createdAtMs")],
)
data class ImageVersionEntity(
  @PrimaryKey val id: String,
  val rootMediaId: String,
  val parentMediaId: String?,
  val conversationId: String,
  val messageId: String,
  val versionNumber: Int,
  val prompt: String,
  val editInstruction: String?,
  val filePath: String,
  val mimeType: String,
  val width: Int,
  val height: Int,
  val providerModel: String,
  val createdAtMs: Long,
)

@Entity(
  tableName = "task_plans",
  indices = [Index("conversationId"), Index("createdAtMs")],
)
data class TaskPlanEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val goal: String,
  val status: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "task_steps",
  indices = [Index("planId"), Index("stepOrder")],
)
data class TaskStepEntity(
  @PrimaryKey val id: String,
  val planId: String,
  val stepOrder: Int,
  val agentRole: String,
  val description: String,
  val status: String,
  val requiresApproval: Boolean,
  val outputSummary: String,
)

@Entity(
  tableName = "voice_sessions",
  indices = [Index("startedAtMs")],
)
data class VoiceSessionEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val voiceGender: String,
  val languageTag: String,
  val turnsCount: Int,
  val startedAtMs: Long,
  val endedAtMs: Long?,
)

@Entity(
  tableName = "drawing_annotations",
  indices = [Index("targetMediaId"), Index("createdAtMs")],
)
data class DrawingAnnotationEntity(
  @PrimaryKey val id: String,
  val targetMediaId: String?,
  val strokesJson: String,
  val boundingBoxJson: String,
  val promptNote: String,
  val createdAtMs: Long,
)

@Entity(
  tableName = "rich_cards",
  indices = [Index("messageId"), Index("createdAtMs")],
)
data class RichCardEntity(
  @PrimaryKey val id: String,
  val messageId: String,
  val kind: String,
  val title: String,
  val subtitle: String,
  val url: String,
  val packageName: String?,
  val createdAtMs: Long,
)

@Dao
interface Part5Dao {
  @Query("SELECT * FROM image_versions WHERE rootMediaId = :rootMediaId ORDER BY versionNumber ASC")
  fun observeImageVersions(rootMediaId: String): Flow<List<ImageVersionEntity>>

  @Query("SELECT * FROM image_versions WHERE rootMediaId = :rootMediaId ORDER BY versionNumber ASC")
  suspend fun getImageVersionsSnapshot(rootMediaId: String): List<ImageVersionEntity>

  @Query("SELECT * FROM image_versions WHERE conversationId = :conversationId ORDER BY createdAtMs DESC")
  fun observeConversationImageVersions(conversationId: String): Flow<List<ImageVersionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertImageVersion(entity: ImageVersionEntity)

  @Query("DELETE FROM image_versions WHERE id = :id")
  suspend fun deleteImageVersion(id: String)

  @Query("SELECT * FROM task_plans ORDER BY updatedAtMs DESC")
  fun observeTaskPlans(): Flow<List<TaskPlanEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTaskPlan(entity: TaskPlanEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTaskSteps(entities: List<TaskStepEntity>)

  @Query("SELECT * FROM task_steps WHERE planId = :planId ORDER BY stepOrder ASC")
  suspend fun getStepsForPlan(planId: String): List<TaskStepEntity>

  @Query("SELECT * FROM voice_sessions ORDER BY startedAtMs DESC LIMIT 50")
  fun observeVoiceSessions(): Flow<List<VoiceSessionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertVoiceSession(entity: VoiceSessionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDrawingAnnotation(entity: DrawingAnnotationEntity)

  @Query("SELECT * FROM drawing_annotations WHERE targetMediaId = :mediaId ORDER BY createdAtMs DESC")
  suspend fun getAnnotationsForMedia(mediaId: String): List<DrawingAnnotationEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertRichCard(entity: RichCardEntity)

  @Query("SELECT * FROM rich_cards WHERE messageId = :messageId ORDER BY createdAtMs ASC")
  suspend fun getRichCardsForMessage(messageId: String): List<RichCardEntity>
}
