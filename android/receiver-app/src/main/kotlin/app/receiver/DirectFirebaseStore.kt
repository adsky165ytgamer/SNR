package app.receiver

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/** Direct Firebase client. Receiver registration and delivery bypass Fastify entirely. */
object DirectFirebaseStore {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun registerReceiver(receiverId: String, ownerUid: String, name: String, appVersion: String) {
        firestore.collection("receivers").document(receiverId).set(
            mapOf(
                "receiverId" to receiverId,
                "ownerUid" to ownerUid,
                "name" to name,
                "platform" to "android",
                "appVersion" to appVersion,
                "enabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun heartbeat(receiverId: String, appVersion: String) {
        firestore.collection("receivers").document(receiverId).set(
            mapOf(
                "appVersion" to appVersion,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "enabled" to true,
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun updateFcmToken(receiverId: String, token: String) {
        firestore.collection("receivers").document(receiverId).update(
            mapOf(
                "fcmToken" to token,
                "fcmUpdatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun fetchRecentNotices(receiverId: String, ownerUid: String): List<NoticeRecord> {
        return firestore.collection("receivers").document(receiverId).collection("notices")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                if (document.getString("senderUid").isNullOrBlank()) return@mapNotNull null
                val title = document.getString("title")?.trim().orEmpty()
                val body = document.getString("body")?.trim().orEmpty()
                if (title.isBlank() || body.isBlank()) return@mapNotNull null
                NoticeRecord(
                    id = document.id,
                    title = title,
                    body = body,
                    receivedAt = document.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                )
            }
    }

    fun listenForNotices(receiverId: String, onNotice: (NoticeRecord) -> Unit, onError: (Exception) -> Unit): ListenerRegistration {
        return firestore.collection("receivers").document(receiverId).collection("notices")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.forEach { change ->
                        val document = change.document
                        val title = document.getString("title")?.trim().orEmpty()
                        val body = document.getString("body")?.trim().orEmpty()
                        if (title.isNotBlank() && body.isNotBlank()) {
                            val receivedAt = document.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                            onNotice(NoticeRecord(document.id, title, body, receivedAt))
                        }
                    }
            }
    }
}
