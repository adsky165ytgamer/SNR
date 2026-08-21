package app.receiver

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Receiver dashboard: every live connection stage is visible and failures expose copyable diagnostics. */
class ReceiverActivity : ComponentActivity() {
    private val identity by lazy { ReceiverIdentity(applicationContext) }
    private lateinit var statusChip: Chip
    private lateinit var statusText: TextView
    private lateinit var stageText: TextView
    private lateinit var diagnosticText: TextView
    private lateinit var copyDiagnosticButton: MaterialButton
    private lateinit var lastNoticeText: TextView
    private lateinit var connectButton: MaterialButton
    private lateinit var nameInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        requestNotificationPermission()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "receiver-heartbeat", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReceiverHeartbeatWorker>(15, TimeUnit.MINUTES).build()
        )
        refreshPresentation()
    }

    override fun onResume() { super.onResume(); if (::lastNoticeText.isInitialized) refreshPresentation() }

    private fun buildScreen(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F6F8F7")) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(28), dp(24), dp(22)); setBackgroundColor(Color.parseColor("#172B30")) }
        header.addView(label("NOTICEFLOW / RECEIVER", Color.parseColor("#A6DBD0"), 12f, Typeface.BOLD))
        header.addView(label("Ready for school notices", Color.WHITE, 27f, Typeface.BOLD).apply { setPadding(0, dp(8), 0, 0) })
        header.addView(label("Connect this device to receive real Firebase Cloud Messaging notices.", Color.parseColor("#E7F3EF"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(6), 0, 0) })
        page.addView(header)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(32)) }
        statusChip = Chip(this).apply { isClickable = false; chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#D8F3EE")); setTextColor(Color.parseColor("#0E5D5A")); text = "Checking connection" }
        body.addView(statusChip)
        statusText = label("", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(10), 0, dp(4)) }
        body.addView(statusText)
        stageText = label("", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD).apply { setPadding(0, 0, 0, dp(14)) }
        body.addView(stageText)

        body.addView(card().apply { addView(identityPanel()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })

        val nameContainer = TextInputLayout(this).apply { hint = "Display name (optional)"; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        nameInput = TextInputEditText(this).apply { setText(identity.name()); setSingleLine() }
        nameContainer.addView(nameInput)
        body.addView(nameContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })

        connectButton = MaterialButton(this).apply { text = "Connect this receiver"; setOnClickListener { registerReceiver() } }
        body.addView(connectButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(10) })
        diagnosticText = label("", Color.parseColor("#8D3030"), 13f, Typeface.NORMAL).apply { visibility = View.GONE; setPadding(dp(4), 0, dp(4), dp(8)) }
        body.addView(diagnosticText)
        copyDiagnosticButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy connection details"; visibility = View.GONE
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Receiver diagnostics", diagnosticText.text))
                text = "Details copied"
            }
        }
        body.addView(copyDiagnosticButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })

        body.addView(card().apply { addView(noticePanel()) })
        body.addView(label("BACKEND: ${BackendClient.endpointLabel()}", Color.parseColor("#76858A"), 11f, Typeface.NORMAL).apply { setPadding(dp(4), dp(16), dp(4), 0) })
        page.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }

    private fun identityPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(label("DEVICE IDENTITY", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
        addView(label(identity.receiverId(), Color.parseColor("#172B30"), 14f, Typeface.BOLD).apply { setPadding(0, dp(8), 0, dp(10)) })
        addView(MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy device ID"
            setOnClickListener { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Receiver ID", identity.receiverId())); text = "Copied" }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun noticePanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(label("LATEST RECEIVED NOTICE", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
        lastNoticeText = label("No notice has been received on this device yet.", Color.parseColor("#526168"), 15f, Typeface.NORMAL).apply { setPadding(0, dp(8), 0, 0) }
        addView(lastNoticeText)
    }

    private fun registerReceiver() = lifecycleScope.launch {
        identity.setName(nameInput.text?.toString())
        clearDiagnostic()
        try {
            setConnecting("Checking secure backend", "Confirming the live backend is reachable…")
            check(BackendClient.isConfigured()) { "A reachable HTTPS backend URL has not been configured." }
            withContext(Dispatchers.IO) { BackendClient.get("/health") }

            setConnecting("Preparing Firebase", "Creating this device’s Firebase installation…")
            check(FirebaseApp.initializeApp(this@ReceiverActivity) != null) { "Firebase configuration is missing for app.receiver." }
            check(FirebaseInstallations.getInstance().id.await().isNotBlank()) { "Firebase could not create a device installation." }

            setConnecting("Requesting push token", "Requesting the real FCM token for this device…")
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            val token = FirebaseMessaging.getInstance().token.await()
            check(token.isNotBlank()) { "Firebase returned an empty FCM token." }

            setConnecting("Registering device", "Saving this device with the school backend…")
            withContext(Dispatchers.IO) {
                BackendClient.post("/api/v1/receivers/register", JSONObject()
                    .put("receiverId", identity.receiverId())
                    .put("name", identity.name())
                    .put("fcmToken", token)
                    .put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName)
                )
            }
            identity.recordRegistered()
            statusChip.text = "Receiver connected"; statusText.text = "Registered with the live backend. You can now send a test notice from Sender."; stageText.text = "COMPLETE"; connectButton.text = "Refresh registration"
        } catch (error: Throwable) {
            statusChip.text = "Connection needs attention"; statusText.text = "The connection stopped before registration completed."; stageText.text = "ACTION REQUIRED"
            showDiagnostic(error)
        } finally {
            connectButton.isEnabled = true
        }
    }

    private fun refreshPresentation() {
        val registeredAt = identity.lastRegisteredAt()
        val firebaseReady = FirebaseApp.initializeApp(this) != null
        when {
            !BackendClient.isConfigured() -> { statusChip.text = "Backend URL needed"; statusText.text = "This build needs its real reachable HTTPS backend URL before it can connect."; stageText.text = "CONFIGURATION" }
            !firebaseReady -> { statusChip.text = "Firebase file needed"; statusText.text = "Firebase configuration is missing for app.receiver."; stageText.text = "CONFIGURATION" }
            registeredAt > 0L -> { statusChip.text = "Receiver connected"; statusText.text = "Last registered ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(registeredAt))}."; stageText.text = "READY FOR NOTICES" }
            else -> { statusChip.text = "Ready to connect"; statusText.text = "Tap Connect to request an FCM token and register this device."; stageText.text = "WAITING" }
        }
        val title = identity.lastNoticeTitle(); val body = identity.lastNoticeBody()
        if (title != null && body != null) lastNoticeText.text = "$title\n\n$body"
    }

    private fun setConnecting(stage: String, text: String) { connectButton.isEnabled = false; statusChip.text = "Connecting"; statusText.text = text; stageText.text = stage.uppercase() }
    private fun clearDiagnostic() { diagnosticText.visibility = View.GONE; copyDiagnosticButton.visibility = View.GONE; diagnosticText.text = "" }
    private fun showDiagnostic(error: Throwable) {
        val chain = generateSequence(error) { it.cause }.mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }.take(3).joinToString(" → ")
        diagnosticText.text = if (chain.isNotBlank()) "Diagnostic: $chain" else "Diagnostic: ${error.javaClass.simpleName}. Firebase did not return a token; confirm Google Play services and try again."
        diagnosticText.visibility = View.VISIBLE; copyDiagnosticButton.visibility = View.VISIBLE
    }
    private fun requestNotificationPermission() { if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
    private fun card() = MaterialCardView(this).apply { radius = dp(18).toFloat(); cardElevation = dp(1).toFloat(); setCardBackgroundColor(Color.WHITE); strokeColor = Color.parseColor("#E0E7E4"); strokeWidth = dp(1) }
    private fun label(text: String, color: Int, size: Float, style: Int) = TextView(this).apply { this.text = text; setTextColor(color); textSize = size; typeface = Typeface.create("sans", style); gravity = Gravity.START }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
