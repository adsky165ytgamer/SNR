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
        val title = message.data["title"].orEmpty().trim()
            .ifBlank { message.notification?.title.orEmpty().trim().ifBlank { "School Notice" } }
        val body = message.data["body"].orEmpty().trim()
            .ifBlank { message.notification?.body.orEmpty().trim().ifBlank { "You have a new notice." } }
        val noticeId = message.data["noticeId"]?.takeIf { it.isNotBlank() } ?: "notice-${System.currentTimeMillis()}"
        val category = NoticeCategory.fromWire(message.data["type"])
        val notice = NoticeRecord(noticeId, title.trim(), body.trim(), System.currentTimeMillis(), category)
        val identity = ReceiverIdentity(applicationContext)
        identity.apply {
            recordNotice(notice.title, notice.body, notice.id, notice.category)
            recordDeliveryDiagnostic(ReceiverDeliveryState.NOTICE_SAVED)
        }
        Log.d("NoticeMessaging", "Notice received noticeId=$noticeId category=${category.wireValue}")
        if (ReceiverPresentationState.shouldPresentOverlay()) {
            NoticeOverlayController(applicationContext).presentIfAllowed(notice)
        } else {
            identity.recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_FOREGROUND_DEFERRED)
            Log.d("NoticeMessaging", "Receiver is focused; keeping notice in app and notification presentation")
        }

        FirebaseBootstrap.ensureInitialized(applicationContext)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(NOTICE_CHANNEL_ID, "School notice alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "High-priority local alerts for notices delivered by NoticeFlow"
                setShowBadge(true)
            },
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            identity.recordNotificationDiagnostic(ReceiverNotificationState.RUNTIME_PERMISSION_DISABLED)
            return
        }
        val notificationManager = NotificationManagerCompat.from(this)
        if (!notificationManager.areNotificationsEnabled()) {
            identity.recordNotificationDiagnostic(ReceiverNotificationState.APP_NOTIFICATIONS_DISABLED)
            return
        }
        if (manager.getNotificationChannel(NOTICE_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            identity.recordNotificationDiagnostic(ReceiverNotificationState.CHANNEL_BLOCKED)
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("noticeId", noticeId)
        }
        val pendingIntent = launchIntent?.let {
            androidx.core.app.PendingIntentCompat.getActivity(this, noticeId.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT, false)
        }
        val notification = NotificationCompat.Builder(this, NOTICE_CHANNEL_ID)
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
        runCatching { notificationManager.notify(noticeId.hashCode(), notification) }
            .onSuccess { identity.recordNotificationDiagnostic(ReceiverNotificationState.POSTED) }
            .onFailure { error ->
                identity.recordNotificationDiagnostic(ReceiverNotificationState.POST_FAILED)
                Log.w("NoticeMessaging", "Local notification could not be posted", error)
            }
    }

    private suspend fun register(token: String) {
        FirebaseBootstrap.ensureInitialized(applicationContext)
        val authToken = FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token ?: return
        val identity = ReceiverIdentity(applicationContext)
        BackendClient.post("/api/v1/receivers/register", JSONObject().put("receiverId", identity.receiverId()).put("name", identity.name()).put("fcmToken", token).put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName), authToken)
    }

    companion object {
        const val NOTICE_CHANNEL_ID = "school_notice_alerts_v2"
    }
}
