package app.receiver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local device identity and a small offline-friendly notice history. */
class ReceiverIdentity(context: Context) {
    private val preferences = context.getSharedPreferences("receiver_identity", Context.MODE_PRIVATE)

    fun receiverId(): String = preferences.getString("receiver_id", null)
        ?: UUID.randomUUID().toString().also { preferences.edit().putString("receiver_id", it).apply() }

    fun name(): String? = preferences.getString("receiver_name", null)

    fun setName(value: String?) = preferences.edit()
        .putString("receiver_name", value?.trim()?.takeIf { it.isNotEmpty() })
        .apply()

    fun hasCompletedOnboarding(): Boolean = preferences.getBoolean("onboarding_complete", false)

    fun completeOnboarding() = preferences.edit()
        .putBoolean("onboarding_complete", true)
        .apply()

    fun resetOnboarding() = preferences.edit()
        .putBoolean("onboarding_complete", false)
        .apply()

    fun lastRegisteredAt(): Long = preferences.getLong("last_registered_at", 0L)

    fun recordRegistered() = preferences.edit()
        .putLong("last_registered_at", System.currentTimeMillis())
        .apply()

    fun lastNoticeTitle(): String? = noticeHistory().firstOrNull()?.title

    fun lastNoticeBody(): String? = noticeHistory().firstOrNull()?.body

    fun lastDeliveryDiagnostic(): ReceiverDeliveryDiagnostic? {
        val state = preferences.getString("last_delivery_diagnostic", null)
            ?.let(ReceiverDeliveryState::fromStorage)
            ?: return null
        return ReceiverDeliveryDiagnostic(state, preferences.getLong("last_delivery_diagnostic_at", 0L))
    }

    fun recordDeliveryDiagnostic(state: ReceiverDeliveryState) = preferences.edit()
        .putString("last_delivery_diagnostic", state.storageValue)
        .putLong("last_delivery_diagnostic_at", System.currentTimeMillis())
        .commit()

    fun lastNotificationDiagnostic(): ReceiverNotificationDiagnostic? {
        val state = preferences.getString("last_notification_diagnostic", null)
            ?.let(ReceiverNotificationState::fromStorage)
            ?: return null
        return ReceiverNotificationDiagnostic(state, preferences.getLong("last_notification_diagnostic_at", 0L))
    }

    fun recordNotificationDiagnostic(state: ReceiverNotificationState) = preferences.edit()
        .putString("last_notification_diagnostic", state.storageValue)
        .putLong("last_notification_diagnostic_at", System.currentTimeMillis())
        .commit()

    fun noticeHistory(): List<NoticeRecord> {
        val raw = preferences.getString("notice_history", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    val body = item.optString("body").trim()
                    if (title.isNotEmpty() && body.isNotEmpty()) {
                        val receivedAt = item.optLong("receivedAt", 0L)
                        val id = item.optString("id").trim().ifBlank { "legacy-$index-$receivedAt" }
                        add(NoticeRecord(id, title, body, receivedAt, NoticeCategory.fromWire(item.optString("category"))))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun recordNotice(title: String, body: String, noticeId: String? = null, category: NoticeCategory = NoticeCategory.NOTICE) {
        synchronized(noticeHistoryLock) {
            val receivedAt = System.currentTimeMillis()
            val id = noticeId?.trim()?.takeIf { it.isNotEmpty() } ?: "local-$receivedAt-${UUID.randomUUID()}"
            val next = listOf(NoticeRecord(id, title.trim(), body.trim(), receivedAt, category)) + noticeHistory()
            val array = JSONArray()
            next.filter { it.title.isNotBlank() && it.body.isNotBlank() }
                .distinctBy { it.id }
                .take(MAX_HISTORY)
                .forEach { notice ->
                    array.put(JSONObject()
                        .put("id", notice.id)
                        .put("title", notice.title)
                        .put("body", notice.body)
                        .put("receivedAt", notice.receivedAt)
                        .put("category", notice.category.wireValue))
                }
            preferences.edit()
                .putString("notice_history", array.toString())
                .commit()
        }
    }

    fun clearNoticeHistory() = preferences.edit()
        .remove("notice_history")
        .apply()

    fun claimOverlayPresentation(noticeId: String): Boolean {
        synchronized(noticeHistoryLock) {
            val id = noticeId.trim()
            if (id.isBlank()) return false
            val shown = overlayPresentationIds()
            if (id in shown) return false
            preferences.edit()
                .putString("overlay_displayed_ids", JSONArray((listOf(id) + shown).distinct().take(MAX_OVERLAY_HISTORY)).toString())
                .commit()
            return true
        }
    }

    fun releaseOverlayPresentation(noticeId: String) {
        synchronized(noticeHistoryLock) {
            val id = noticeId.trim()
            if (id.isBlank()) return
            preferences.edit()
                .putString("overlay_displayed_ids", JSONArray(overlayPresentationIds().filterNot { it == id }).toString())
                .commit()
        }
    }

    private fun overlayPresentationIds(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("overlay_displayed_ids", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        private val noticeHistoryLock = Any()
        private const val MAX_HISTORY = 20
        private const val MAX_OVERLAY_HISTORY = 50
    }
}

data class NoticeRecord(
    val id: String,
    val title: String,
    val body: String,
    val receivedAt: Long,
    val category: NoticeCategory,
)

data class ReceiverDeliveryDiagnostic(
    val state: ReceiverDeliveryState,
    val occurredAt: Long,
)

data class ReceiverNotificationDiagnostic(
    val state: ReceiverNotificationState,
    val occurredAt: Long,
)

enum class ReceiverDeliveryState(val storageValue: String, val label: String, val detail: String) {
    NOTICE_SAVED("NOTICE_SAVED", "Saved to Inbox", "The real notice was persisted on this Receiver."),
    OVERLAY_NOT_REQUESTED("OVERLAY_NOT_REQUESTED", "Overlay not requested", "Homework and News use the standard notification pathway."),
    OVERLAY_FOREGROUND_DEFERRED("OVERLAY_FOREGROUND_DEFERRED", "Overlay deferred while Receiver is focused", "The notice remains in Inbox and uses the standard notification pathway while Receiver is the active screen."),
    OVERLAY_PERMISSION_UNAVAILABLE("OVERLAY_PERMISSION_UNAVAILABLE", "Overlay permission unavailable", "Android special access is disabled; the normal notification remains the fallback."),
    OVERLAY_DUPLICATE_SUPPRESSED("OVERLAY_DUPLICATE_SUPPRESSED", "Duplicate overlay skipped", "The same notice ID already requested an overlay."),
    OVERLAY_DISPLAYED("OVERLAY_DISPLAYED", "Overlay displayed", "The Notice overlay was attached above other apps."),
    OVERLAY_FAILED("OVERLAY_FAILED", "Overlay failed safely", "The notice remains in Inbox and the normal notification remains the fallback.");

    companion object {
        fun fromStorage(value: String): ReceiverDeliveryState? = entries.firstOrNull { it.storageValue == value }
    }
}

enum class ReceiverNotificationState(val storageValue: String, val label: String, val detail: String) {
    POSTED("POSTED", "Notification posted", "Notice Receiver asked Android to show the local notice alert."),
    RUNTIME_PERMISSION_DISABLED("RUNTIME_PERMISSION_DISABLED", "Notification permission disabled", "Android notification permission is off for Notice Receiver."),
    APP_NOTIFICATIONS_DISABLED("APP_NOTIFICATIONS_DISABLED", "App notifications disabled", "Android app-level notifications are off for Notice Receiver."),
    CHANNEL_BLOCKED("CHANNEL_BLOCKED", "Notice channel blocked", "The Android School notice alerts channel is blocked or silenced."),
    POST_FAILED("POST_FAILED", "Notification post failed", "Android rejected the local notification request. The notice remains in Inbox.");

    companion object {
        fun fromStorage(value: String): ReceiverNotificationState? = entries.firstOrNull { it.storageValue == value }
    }
}
