package app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.receiver.auth.FirebaseBootstrap
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
        val noticeId = message.data["noticeId"]?.takeIf { it.isNotBlank() } ?: "notice-${System.currentTimeMillis()}"
        val category = NoticeCategory.fromWire(message.data["type"])
        val notice = NoticeRecord(noticeId, title.trim(), body.trim(), System.currentTimeMillis(), category)
        ReceiverIdentity(applicationContext).recordNotice(notice.title, notice.body, notice.id, notice.category)
        Log.d("NoticeMessaging", "Notice received noticeId=$noticeId category=${category.wireValue}")
        if (!ReceiverPresentationState.isForeground()) {
            NoticeOverlayController(applicationContext).presentIfAllowed(notice)
        } else {
            Log.d("NoticeMessaging", "Receiver is foreground; keeping notice in app and notification presentation")
        }

        FirebaseBootstrap.ensureInitialized(applicationContext)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("school_notice_test", "School notices", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "High-priority school notices delivered by NoticeFlow"
                setShowBadge(true)
            },
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("noticeId", noticeId)
        }
        val pendingIntent = launchIntent?.let {
            androidx.core.app.PendingIntentCompat.getActivity(this, noticeId.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT, false)
        }
        val notification = NotificationCompat.Builder(this, "school_notice_test")
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()
        NotificationManagerCompat.from(this).notify(noticeId.hashCode(), notification)
    }

    private suspend fun register(token: String) {
        FirebaseBootstrap.ensureInitialized(applicationContext)
        val authToken = FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token ?: return
        val identity = ReceiverIdentity(applicationContext)
        BackendClient.post("/api/v1/receivers/register", JSONObject().put("receiverId", identity.receiverId()).put("name", identity.name()).put("fcmToken", token).put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName), authToken)
    }
}
