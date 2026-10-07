package app.receiver

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NoticeOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE)?.trim().orEmpty()
        val body = intent?.getStringExtra(EXTRA_BODY)?.trim().orEmpty()
        val noticeId = intent?.getStringExtra(EXTRA_ID).orEmpty()
        if (title.isBlank() || body.isBlank() || !Settings.canDrawOverlays(this)) return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, "school_notice_direct")
            .setSmallIcon(R.drawable.ic_notice_notification)
            .setContentTitle("NoticeFlow quick notice")
            .setContentText(title)
            .setOngoing(true)
            .setSilent(true)
            .build())
        showOverlay(title, body, noticeId)
        return START_NOT_STICKY
    }

    private fun showOverlay(title: String, body: String, noticeId: String) {
        removeOverlay()
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply { setColor(Color.rgb(19, 28, 26)); cornerRadius = 22 * density }
            elevation = 12 * density
            setOnClickListener {
                val open = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("noticeId", noticeId)
                }
                if (open != null) startActivity(open)
                stopSelf()
            }
        }
        container.addView(TextView(this).apply { text = title; setTextColor(Color.rgb(234, 240, 237)); textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        container.addView(TextView(this).apply { text = body; setTextColor(Color.rgb(155, 168, 163)); textSize = 13f; maxLines = 3; setPadding(0, (5 * density).toInt(), 0, 0) })
        container.setOnTouchListener(DragTouchListener())
        val params = WindowManager.LayoutParams(
            (minOf(360, (resources.displayMetrics.widthPixels / density).toInt() - 32) * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = (16 * density).toInt(); y = (72 * density).toInt() }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager?.addView(container, params) }.onSuccess { overlay = container }
        container.postDelayed({ if (overlay === container) stopSelf() }, 15_000L)
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; return false }
                MotionEvent.ACTION_MOVE -> { params.x = startX - (event.rawX - downX).toInt(); params.y = startY + (event.rawY - downY).toInt(); runCatching { windowManager?.updateViewLayout(view, params) }; return true }
            }
            return false
        }
    }

    private fun removeOverlay() { overlay?.let { runCatching { windowManager?.removeView(it) } }; overlay = null }
    override fun onDestroy() { removeOverlay(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4107
        const val EXTRA_ID = "overlay_notice_id"
        const val EXTRA_TITLE = "overlay_notice_title"
        const val EXTRA_BODY = "overlay_notice_body"
    }
}
