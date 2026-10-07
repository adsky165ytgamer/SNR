package app.receiver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deployment-free fallback for devices that do not receive FCM while the app is closed.
 * It checks the Receiver's Firestore notice stream periodically and creates the same
 * local notification/overlay as FCM when a new notice is found.
 */
class ReceiverNoticeSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val user = FirebaseAuth.getInstance().currentUser ?: return@runCatching
            val receiver = ReceiverIdentity(applicationContext)
            if (receiver.lastRegisteredAt() == 0L) return@runCatching
            DirectFirebaseStore.fetchRecentNotices(receiver.receiverId(), user.uid).forEach { notice ->
                if (receiver.noticeHistory().none { it.id == notice.id }) {
                    receiver.recordNotice(notice.title, notice.body, notice.id)
                    DirectNoticeNotifier.show(applicationContext, notice)
                }
            }
        }.fold({ Result.success() }, { Result.retry() })
    }
}
