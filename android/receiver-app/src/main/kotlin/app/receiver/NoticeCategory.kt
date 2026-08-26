package app.receiver

/** Wire-stable notice categories shared by local storage and FCM presentation. */
enum class NoticeCategory(val wireValue: String, val label: String) {
    HOMEWORK("HOMEWORK", "Homework"),
    NOTICE("NOTICE", "Notice"),
    NEWS("NEWS", "News");

    companion object {
        fun fromWire(value: String?): NoticeCategory = entries.firstOrNull {
            it.wireValue == value?.trim()?.uppercase()
        } ?: NOTICE
    }
}
