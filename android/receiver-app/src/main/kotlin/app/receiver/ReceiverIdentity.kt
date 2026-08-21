package app.receiver

import android.content.Context
import java.util.UUID

class ReceiverIdentity(context: Context) {
    private val preferences = context.getSharedPreferences("receiver_identity", Context.MODE_PRIVATE)
    fun receiverId(): String = preferences.getString("receiver_id", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("receiver_id", it).apply() }
    fun name(): String? = preferences.getString("receiver_name", null)
    fun setName(value: String?) = preferences.edit().putString("receiver_name", value?.trim()?.takeIf { it.isNotEmpty() }).apply()
    fun lastRegisteredAt(): Long = preferences.getLong("last_registered_at", 0L)
    fun recordRegistered() = preferences.edit().putLong("last_registered_at", System.currentTimeMillis()).apply()
    fun lastNoticeTitle(): String? = preferences.getString("last_notice_title", null)
    fun lastNoticeBody(): String? = preferences.getString("last_notice_body", null)
    fun recordNotice(title: String, body: String) = preferences.edit().putString("last_notice_title", title).putString("last_notice_body", body).apply()
}
