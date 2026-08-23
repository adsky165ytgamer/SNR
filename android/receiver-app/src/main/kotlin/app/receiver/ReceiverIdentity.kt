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
                        add(NoticeRecord(id, title, body, receivedAt))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun recordNotice(title: String, body: String, noticeId: String? = null) {
        val receivedAt = System.currentTimeMillis()
        val id = noticeId?.trim()?.takeIf { it.isNotEmpty() } ?: "local-$receivedAt-${UUID.randomUUID()}"
        val next = listOf(NoticeRecord(id, title.trim(), body.trim(), receivedAt)) + noticeHistory()
        val array = JSONArray()
        next.filter { it.title.isNotBlank() && it.body.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_HISTORY)
            .forEach { notice ->
                array.put(JSONObject()
                    .put("id", notice.id)
                    .put("title", notice.title)
                    .put("body", notice.body)
                    .put("receivedAt", notice.receivedAt))
            }
        preferences.edit()
            .putString("notice_history", array.toString())
            .commit()
    }

    fun clearNoticeHistory() = preferences.edit()
        .remove("notice_history")
        .apply()

    companion object {
        private const val MAX_HISTORY = 20
    }
}

data class NoticeRecord(
    val id: String,
    val title: String,
    val body: String,
    val receivedAt: Long,
)
