package app.receiver

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * A single, permission-gated presentation surface for Notice-category deliveries.
 * It never starts a service, polls, or attempts to bypass lock-screen/system UI policies.
 */
class NoticeOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun presentIfAllowed(notice: NoticeRecord) {
        val identity = ReceiverIdentity(appContext)
        if (notice.category != NoticeCategory.NOTICE) {
            identity.recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_NOT_REQUESTED)
            return
        }
        if (!canDrawOverOtherApps(appContext)) {
            identity.recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_PERMISSION_UNAVAILABLE)
            Log.d(TAG, "Overlay unavailable; existing notification remains the fallback")
            return
        }
        Handler(Looper.getMainLooper()).post {
            runCatching { showOnMainThread(notice) }
                .onFailure { error ->
                    identity.recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_FAILED)
                    Log.w(TAG, "Unhandled overlay presentation failure; notification fallback remains", error)
                }
        }
    }

    private fun showOnMainThread(notice: NoticeRecord) {
        if (!canDrawOverOtherApps(appContext)) {
            ReceiverIdentity(appContext).recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_PERMISSION_UNAVAILABLE)
            Log.d(TAG, "Overlay permission was revoked before display; using notification fallback")
            return
        }
        val identity = ReceiverIdentity(appContext)
        if (!identity.claimOverlayPresentation(notice.id)) {
            identity.recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_DUPLICATE_SUPPRESSED)
            Log.d(TAG, "Duplicate overlay suppressed for noticeId=${notice.id}")
            return
        }

        try {
            dismissCurrent("replaced by a newer Notice-category delivery")
            val view = createOverlayView(notice)
            windowManager.addView(view, layoutParams())
            activeOverlay = ActiveOverlay(windowManager, view, notice.id)
            identity.recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_DISPLAYED)
            Log.d(TAG, "Overlay displayed for noticeId=${notice.id}")
        } catch (error: Exception) {
            identity.releaseOverlayPresentation(notice.id)
            identity.recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_FAILED)
            Log.w(TAG, "Overlay could not be attached; notification fallback remains", error)
        }
    }

    private fun createOverlayView(notice: NoticeRecord): View {
        val pad = dp(20)
        val content = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(21, 23, 25))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.rgb(82, 232, 201))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "NoticeFlow ${notice.category.label} notice overlay"

            addView(label("${notice.category.label.uppercase()} • NOTICEFLOW", 13, Color.rgb(141, 232, 201), true))
            addView(label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.receivedAt)), 13, Color.rgb(184, 191, 196), false).apply {
                setPadding(0, dp(6), 0, 0)
            })
            addView(label(notice.title, 24, Color.rgb(237, 237, 237), true).apply {
                setPadding(0, dp(14), 0, 0)
                maxLines = 3
            })
            addView(label(notice.body, 17, Color.rgb(213, 218, 221), false).apply {
                setPadding(0, dp(10), 0, 0)
                maxLines = 5
            })
            addView(LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(18), 0, 0)
                addView(actionButton("View notice", Color.rgb(141, 232, 201), Color.rgb(16, 32, 27)) {
                    dismissCurrent("view action")
                    openInbox(notice.id)
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(10) })
                addView(actionButton("Dismiss", Color.rgb(47, 52, 56), Color.rgb(237, 237, 237)) {
                    dismissCurrent("dismiss action")
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
            })
        }
        return ScrollView(appContext).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(content)
        }
    }

    private fun label(text: String, sizeSp: Int, color: Int, emphasized: Boolean) = TextView(appContext).apply {
        this.text = text
        textSize = sizeSp.toFloat()
        setTextColor(color)
        if (emphasized) typeface = android.graphics.Typeface.DEFAULT_BOLD
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun actionButton(text: String, background: Int, foreground: Int, action: () -> Unit) = Button(appContext).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        setTextColor(foreground)
        backgroundTintList = ColorStateList.valueOf(background)
        contentDescription = text
        setOnClickListener { action() }
    }

    private fun openInbox(noticeId: String) {
        runCatching {
            val intent = Intent(appContext, ReceiverActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("noticeId", noticeId)
            }
            appContext.startActivity(intent)
        }.onFailure { error ->
            ReceiverIdentity(appContext).recordDeliveryDiagnostic(ReceiverDeliveryState.OVERLAY_FAILED)
            Log.w(TAG, "Overlay view action could not open Inbox", error)
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        (appContext.resources.displayMetrics.widthPixels * 0.9f).toInt().coerceAtMost(dp(1200)),
        (appContext.resources.displayMetrics.heightPixels * 0.72f).toInt().coerceAtMost(dp(560)),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.CENTER
        title = "NoticeFlow notice overlay"
    }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()

    private fun dismissCurrent(reason: String) {
        val current = activeOverlay ?: return
        activeOverlay = null
        runCatching { current.windowManager.removeView(current.view) }
        Log.d(TAG, "Overlay dismissed for noticeId=${current.noticeId}; reason=$reason")
    }

    private data class ActiveOverlay(val windowManager: WindowManager, val view: View, val noticeId: String)

    companion object {
        private const val TAG = "NoticeOverlay"
        @Volatile private var activeOverlay: ActiveOverlay? = null

        fun canDrawOverOtherApps(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun overlaySettingsIntent(context: Context): Intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }
}
