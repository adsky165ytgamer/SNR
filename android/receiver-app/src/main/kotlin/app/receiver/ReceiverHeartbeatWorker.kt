package app.receiver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ReceiverHeartbeatWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        runCatching {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@runCatching
            BackendClient.post("/api/v1/receivers/heartbeat", JSONObject().put("receiverId", ReceiverIdentity(applicationContext).receiverId()).put("appVersion", applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName), token)
        }.fold({ Result.success() }, { Result.retry() })
    }
}
