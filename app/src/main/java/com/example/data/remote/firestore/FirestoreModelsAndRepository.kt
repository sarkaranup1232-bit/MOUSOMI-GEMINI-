package com.example.data.remote.firestore

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.core.security.OperationType
import com.example.core.security.handleFirestoreError
import com.example.domain.model.AppSettings
import com.example.domain.model.DomainConversation
import com.example.domain.model.DomainMessage
import com.example.domain.model.ResponseBlock
import com.example.domain.model.TokenUsage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String? = null,
  val photoUrl: String? = null,
  val themeModeName: String = "DARK",
  val selectedModelId: String = "gemini_4_hydrogen",
  val hydrogenBackingModelId: String? = null,
  val groqReasoningModelId: String? = null,
  val imageModelId: String? = null,
  val voiceLanguageTag: String? = null,
  val voiceGenderName: String? = null,
  val composerGlowIntensity: Double = 0.85,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FirestoreConversation(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val clientUpdatedAtMs: Long = 0L,
  val modelId: String? = null,
  val messageCount: Long? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FirestoreMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val conversationId: String = "",
  val fromUser: Boolean = false,
  val text: String = "",
  val clientTimestampMs: Long = 0L,
  val modelUsed: String? = null,
  val categoryLabel: String? = null,
  val totalTokens: Long? = null,
  val errorFlag: Boolean? = null,
  val blocksJson: String? = null,
  val attachmentsCount: Long? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Singleton
class FirestoreCloudSyncRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val json: Json,
) {
  val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val db: FirebaseFirestore by lazy {
    val dbId = context.applicationContext.getString(R.string.firestore_database_id)
    FirebaseFirestore.getInstance(dbId)
  }

  private fun currentUidOrNull(): String? =
    runCatching { Firebase.auth.currentUser?.uid }.getOrNull()?.takeIf { it.isNotBlank() }

  private fun requireUid(): String =
    currentUidOrNull()
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeUserProfile(): Flow<FirestoreUserProfile?> = flow {
    val uid = currentUidOrNull() ?: run {
      emit(null)
      return@flow
    }
    val path = "users/$uid"
    emitAll(
      db.collection("users").document(uid)
        .snapshots()
        .map { snapshot ->
          if (!snapshot.exists()) null
          else snapshot.toObject(FirestoreUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && currentUidOrNull() == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  fun observeConversations(): Flow<List<DomainConversation>> = flow {
    val uid = currentUidOrNull() ?: run {
      emit(emptyList())
      return@flow
    }
    val path = "users/$uid/conversations"
    emitAll(
      db.collection("users").document(uid).collection("conversations")
        .whereEqualTo("userId", uid)
        .orderBy("clientUpdatedAtMs", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.documents.mapNotNull { doc ->
            val model = doc.toObject(
              FirestoreConversation::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: return@mapNotNull null
            val createdMs = model.createdAt?.toDate()?.time ?: model.clientUpdatedAtMs
            DomainConversation(
              id = doc.id,
              title = model.title.ifBlank { "New Conversation" },
              createdAtMs = createdMs,
              updatedAtMs = model.clientUpdatedAtMs,
            )
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && currentUidOrNull() == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeMessages(conversationId: String): Flow<List<DomainMessage>> = flow {
    val uid = currentUidOrNull() ?: run {
      emit(emptyList())
      return@flow
    }
    require(conversationId.isNotBlank()) { "conversationId must not be blank" }
    val path = "users/$uid/conversations/$conversationId/messages"
    emitAll(
      db.collection("users").document(uid)
        .collection("conversations").document(conversationId)
        .collection("messages")
        .whereEqualTo("userId", uid)
        .orderBy("clientTimestampMs", Query.Direction.ASCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.documents.mapNotNull { doc ->
            val msg = doc.toObject(
              FirestoreMessage::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: return@mapNotNull null
            val decodedBlocks = msg.blocksJson?.let { raw ->
              runCatching { json.decodeFromString<List<ResponseBlock>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
            val totalTok = msg.totalTokens?.toInt()
            DomainMessage(
              id = doc.id,
              conversationId = msg.conversationId.ifBlank { conversationId },
              isUser = msg.fromUser,
              text = msg.text,
              timestampMs = msg.clientTimestampMs,
              modelUsed = msg.modelUsed,
              categoryLabel = msg.categoryLabel,
              tokenUsage = totalTok?.let { TokenUsage(0, it, it) },
              isError = msg.errorFlag ?: false,
              blocks = decodedBlocks,
            )
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && currentUidOrNull() == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun syncUserProfileAndPreferences(
    displayName: String,
    email: String?,
    photoUrl: String?,
    settings: AppSettings,
  ): Result<Unit> {
    var docPath = "users/unauthenticated"
    return try {
      val uid = requireUid()
      val docRef = db.collection("users").document(uid)
      docPath = docRef.path
      val commonFields = mapOf<String, Any?>(
        "displayName" to displayName.ifBlank { "Mousomi Creator" }.take(120),
        "email" to email?.take(200),
        "photoUrl" to photoUrl?.take(512),
        "themeModeName" to settings.themeModeName.take(32),
        "selectedModelId" to settings.selectedModelOptionName.take(100),
        "hydrogenBackingModelId" to settings.hydrogenBackingModelId.takeIf { it.isNotBlank() }?.take(100),
        "groqReasoningModelId" to settings.groqReasoningModelId.takeIf { it.isNotBlank() }?.take(100),
        "imageModelId" to settings.imageModelId.takeIf { it.isNotBlank() }?.take(100),
        "voiceLanguageTag" to settings.voicePreferences.preferredLanguage.localeTag.take(20),
        "voiceGenderName" to settings.voicePreferences.voiceGender.name.take(20),
        "composerGlowIntensity" to settings.composerGlowIntensity.toDouble().coerceIn(0.0, 1.0),
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }

      val existing = docRef.get().await()
      if (!existing.exists()) {
        docRef.set(
          commonFields + mapOf(
            "userId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.update(commonFields).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun upsertConversation(
    conversationId: String,
    title: String,
    updatedAtMs: Long,
    modelId: String? = null,
  ): Result<Unit> {
    var docPath = "users/unauthenticated/conversations/$conversationId"
    return try {
      val uid = requireUid()
      require(conversationId.isNotBlank()) { "conversationId must not be blank" }
      val docRef = db.collection("users").document(uid)
        .collection("conversations").document(conversationId)
      docPath = docRef.path
      val sanitizedTitle = title.ifBlank { "New Conversation" }.take(200)
      val updatePayload = mapOf<String, Any?>(
        "title" to sanitizedTitle,
        "clientUpdatedAtMs" to updatedAtMs.coerceAtLeast(0L),
        "modelId" to modelId?.takeIf { it.isNotBlank() }?.take(100),
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }

      val existing = docRef.get().await()
      if (!existing.exists()) {
        docRef.set(
          updatePayload + mapOf(
            "userId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun upsertMessage(message: DomainMessage): Result<Unit> {
    var docPath = "users/unauthenticated/conversations/${message.conversationId}/messages/${message.id}"
    return try {
      val uid = requireUid()
      require(message.conversationId.isNotBlank()) { "conversationId must not be blank" }
      require(message.id.isNotBlank()) { "message.id must not be blank" }
      val convRef = db.collection("users").document(uid)
        .collection("conversations").document(message.conversationId)
      if (!convRef.get().await().exists()) {
        upsertConversation(
          conversationId = message.conversationId,
          title = if (message.isUser && message.text.isNotBlank()) message.text.take(60) else "New Conversation",
          updatedAtMs = message.timestampMs,
        )
      }
      val msgRef = convRef.collection("messages").document(message.id)
      docPath = msgRef.path
      val encodedBlocks =
        if (message.blocks.isNotEmpty()) {
          runCatching { json.encodeToString(message.blocks) }.getOrNull()?.take(180_000)
        } else null
      val updatePayload = mapOf<String, Any?>(
        "fromUser" to message.isUser,
        "text" to message.text.take(90_000),
        "clientTimestampMs" to message.timestampMs.coerceAtLeast(0L),
        "modelUsed" to message.modelUsed?.takeIf { it.isNotBlank() }?.take(120),
        "categoryLabel" to message.categoryLabel?.takeIf { it.isNotBlank() }?.take(80),
        "totalTokens" to message.tokenUsage?.totalTokens?.toLong()?.coerceAtLeast(0L),
        "errorFlag" to message.isError,
        "blocksJson" to encodedBlocks,
        "attachmentsCount" to message.attachedMediaSummaries.size.toLong().coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }

      val existing = msgRef.get().await()
      if (!existing.exists()) {
        msgRef.set(
          updatePayload + mapOf(
            "userId" to uid,
            "conversationId" to message.conversationId,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        msgRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteConversation(conversationId: String): Result<Unit> {
    var docPath = "users/unauthenticated/conversations/$conversationId"
    return try {
      val uid = requireUid()
      require(conversationId.isNotBlank()) { "conversationId must not be blank" }
      val convRef = db.collection("users").document(uid)
        .collection("conversations").document(conversationId)
      docPath = convRef.path
      convRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }
}
