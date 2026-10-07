package app.receiver

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Handles background Firebase Cloud Messaging delivery when the app is not open. */
class NoticeMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.notification?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: "School notice"
        val body = message.data["body"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.notification?.body?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        val noticeId = message.data["noticeId"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.messageId
            ?: "fcm-${System.currentTimeMillis()}"
        val receiver = ReceiverIdentity(applicationContext)
        receiver.recordNotice(title, body, noticeId)
        DirectNoticeNotifier.show(applicationContext, NoticeRecord(noticeId, title, body, System.currentTimeMillis()))
    }

    override fun onNewToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val receiver = ReceiverIdentity(applicationContext)
        if (receiver.lastRegisteredAt() == 0L) return
        FirebaseFirestore.getInstance().collection("receivers").document(receiver.receiverId()).update(
            mapOf("fcmToken" to token, "fcmUpdatedAt" to FieldValue.serverTimestamp(), "ownerUid" to user.uid),
        )
    }
}
