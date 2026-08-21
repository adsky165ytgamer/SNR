package app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class NoticeMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch { runCatching { register(token) } }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: "School Notice"
        val body = message.data["body"] ?: message.notification?.body ?: "You have a new notice."
        ReceiverIdentity(applicationContext).recordNotice(title, body)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("school_notice_test", "School notices", NotificationManager.IMPORTANCE_HIGH))
        val notification = NotificationCompat.Builder(this, "school_notice_test").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        manager.notify(message.data["noticeId"]?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }
    private suspend fun register(token: String) {
        val authToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return
        val identity = ReceiverIdentity(applicationContext)
        BackendClient.post("/api/v1/receivers/register", JSONObject().put("receiverId", identity.receiverId()).put("name", identity.name()).put("fcmToken", token).put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName), authToken)
    }
}
