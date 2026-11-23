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
  tableName = "image_tasks",
  indices = [
    Index("conversationId"),
    Index("status"),
    Index("createdAtMs"),
  ],
)
data class ImageTaskEntity(
  @PrimaryKey val taskId: String,
  val conversationId: String,
  val featureType: String,
  val executionSource: String,
  val prompt: String,
  val editPlanJson: String,
  val inputAssetId: String?,
  val maskPath: String?,
  val outputAssetId: String?,
  val providerId: String,
  val modelId: String,
  val status: String,
  val errorMessage: String?,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "image_assets",
  indices = [
    Index("createdAtMs"),
  ],
)
data class ImageAssetEntity(
  @PrimaryKey val assetId: String,
  val localPreviewPath: String,
  val mediaStoreUri: String?,
  val mimeType: String,
  val width: Int,
  val height: Int,
  val fileSizeBytes: Long,
  val hasTransparency: Boolean,
  val sourceBadge: String,
  val createdAtMs: Long,
)

@Entity(tableName = "image_provider_status")
data class ImageProviderStatusEntity(
  @PrimaryKey val providerId: String,
  val displayName: String,
  val connectionStatus: String,
  val quotaState: String,
  val isFreeTier: Boolean,
  val activeModelId: String,
  val lastVerifiedAtMs: Long?,
  val lastErrorDetail: String?,
)

@Dao
interface ImageStudioDao {
  @Query("SELECT * FROM image_tasks ORDER BY createdAtMs DESC LIMIT 50")
  fun observeRecentTasks(): Flow<List<ImageTaskEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTask(entity: ImageTaskEntity)

  @Query("SELECT * FROM image_assets ORDER BY createdAtMs DESC LIMIT 50")
  fun observeAssets(): Flow<List<ImageAssetEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAsset(entity: ImageAssetEntity)

  @Query("SELECT * FROM image_provider_status ORDER BY isFreeTier DESC, providerId ASC")
  fun observeProviderStatuses(): Flow<List<ImageProviderStatusEntity>>

  @Query("SELECT * FROM image_provider_status ORDER BY isFreeTier DESC, providerId ASC")
  suspend fun getProviderStatusesSnapshot(): List<ImageProviderStatusEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProviderStatus(entity: ImageProviderStatusEntity)

  @Query("DELETE FROM image_tasks WHERE createdAtMs < :olderThanMs AND status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
  suspend fun pruneOldTasks(olderThanMs: Long)
}
