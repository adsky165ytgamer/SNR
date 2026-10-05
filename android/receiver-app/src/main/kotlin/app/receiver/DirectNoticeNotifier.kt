package app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object DirectNoticeNotifier {
    fun show(context: Context, notice: NoticeRecord) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("school_notice_direct", "School notices", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notices received directly from Firebase Firestore"
                setShowBadge(true)
            },
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("noticeId", notice.id)
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(context, notice.id.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, "school_notice_direct")
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()
        NotificationManagerCompat.from(context).notify(notice.id.hashCode(), notification)
    }
}
