package app.receiver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReceiverHeartbeatWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        runCatching {
            DirectFirebaseStore.heartbeat(
                ReceiverIdentity(applicationContext).receiverId(),
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName.orEmpty(),
            )
        }.fold({ Result.success() }, { Result.retry() })
    }
}
