package app.receiver

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import app.receiver.auth.AuthenticatedIdentity
import app.receiver.auth.FirebaseBootstrap
import app.receiver.auth.GoogleAuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val authSession by lazy { GoogleAuthSession(this) }
    private var authIdentity: AuthenticatedIdentity? = null
    private lateinit var statusChip: Chip
    private lateinit var statusText: TextView
    private lateinit var stageText: TextView
    private lateinit var diagnosticText: TextView
    private lateinit var copyDiagnosticButton: MaterialButton
    private lateinit var lastNoticeText: TextView
    private lateinit var historyList: LinearLayout
    private lateinit var clearHistoryButton: MaterialButton
    private lateinit var connectButton: MaterialButton
    private lateinit var nameInput: TextInputEditText
    private lateinit var authSummary: TextView
    private lateinit var authButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        setContentView(buildScreen())
        requestNotificationPermission()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "receiver-heartbeat", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReceiverHeartbeatWorker>(15, TimeUnit.MINUTES).build()
        )
        refreshPresentation()
        lifecycleScope.launch { refreshAuth() }
    }

    override fun onResume() {
        super.onResume()
        if (::lastNoticeText.isInitialized) refreshPresentation()
    }

    private fun buildScreen(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F6F8F7"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(22))
            setBackgroundColor(Color.parseColor("#172B30"))
        }
        header.addView(label("NOTICEFLOW / RECEIVER", Color.parseColor("#A6DBD0"), 12f, Typeface.BOLD))
        header.addView(label("Ready for school notices", Color.WHITE, 27f, Typeface.BOLD).apply {
            setPadding(0, dp(8), 0, 0)
        })
        header.addView(label("Connect this device to receive real Firebase Cloud Messaging notices.", Color.parseColor("#E7F3EF"), 14f, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, 0)
        })
        page.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        statusChip = Chip(this).apply {
            isClickable = false
            chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#D8F3EE"))
            setTextColor(Color.parseColor("#0E5D5A"))
            text = "Checking connection"
        }
        body.addView(statusChip)
        statusText = label("", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply {
            setPadding(0, dp(10), 0, dp(4))
        }
        body.addView(statusText)
        stageText = label("", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD).apply {
            setPadding(0, 0, 0, dp(14))
        }
        body.addView(stageText)
        body.addView(card().apply { addView(authPanel()) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) })

        body.addView(card().apply { addView(identityPanel()) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) })

        val nameContainer = TextInputLayout(this).apply {
            hint = "Display name (optional)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        nameInput = TextInputEditText(this).apply {
            setText(identity.name())
            setSingleLine()
        }
        nameContainer.addView(nameInput)
        body.addView(nameContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) })

        connectButton = MaterialButton(this).apply {
            text = "Connect this receiver"
            setOnClickListener { registerReceiver() }
        }
        body.addView(connectButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { bottomMargin = dp(10) })
        diagnosticText = label("", Color.parseColor("#8D3030"), 13f, Typeface.NORMAL).apply {
            visibility = View.GONE
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        body.addView(diagnosticText)
        copyDiagnosticButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy connection details"
            visibility = View.GONE
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Receiver diagnostics", diagnosticText.text))
                text = "Details copied"
            }
        }
        body.addView(copyDiagnosticButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) })

        body.addView(card().apply { addView(noticePanel()) })
        body.addView(label("BACKEND: ${BackendClient.endpointLabel()}", Color.parseColor("#76858A"), 11f, Typeface.NORMAL).apply {
            setPadding(dp(4), dp(16), dp(4), 0)
        })
        page.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            header.setPadding(dp(24) + safe.left, dp(28) + safe.top, dp(24) + safe.right, dp(22))
            body.setPadding(dp(20) + safe.left, dp(20), dp(20) + safe.right, dp(32) + safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(page)
        return page
    }

    private fun authPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(label("GOOGLE ACCOUNT", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
        authSummary = label("Not signed in. Sign in to bind this Receiver to your Google account.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(7), 0, dp(10)) }
        addView(authSummary)
        authButton = MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = authSession.preferredButtonText()
            setOnClickListener { authenticate() }
        }
        addView(authButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun identityPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(label("DEVICE IDENTITY", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
        addView(label(identity.receiverId(), Color.parseColor("#172B30"), 14f, Typeface.BOLD).apply {
            setPadding(0, dp(8), 0, dp(10))
        })
        addView(MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy device ID"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Receiver ID", identity.receiverId()))
                text = "Copied"
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun noticePanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(label("NOTICE HISTORY", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
        lastNoticeText = label("No notice has been received on this device yet.", Color.parseColor("#526168"), 15f, Typeface.NORMAL).apply {
            setPadding(0, dp(8), 0, dp(12))
        }
        addView(lastNoticeText)
        historyList = LinearLayout(this@ReceiverActivity).apply { orientation = LinearLayout.VERTICAL }
        addView(historyList)
        clearHistoryButton = MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Clear local history"
            setOnClickListener {
                identity.clearNoticeHistory()
                renderNoticeHistory()
            }
        }
        addView(clearHistoryButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
    }

    private fun registerReceiver() = lifecycleScope.launch {
        identity.setName(nameInput.text?.toString())
        if (authIdentity == null) {
            authIdentity = authSession.signIn()
            renderAuth(authIdentity!!)
        }
        clearDiagnostic()
        try {
            setConnecting("Checking secure backend", "Confirming the live backend is reachable…")
            check(BackendClient.isConfigured()) { "A reachable HTTPS backend URL has not been configured." }
            withContext(Dispatchers.IO) { BackendClient.get("/health") }

            setConnecting("Preparing Firebase", "Creating this device’s Firebase installation…")
            FirebaseBootstrap.ensureInitialized(this@ReceiverActivity)
            check(FirebaseApp.getApps(this@ReceiverActivity).isNotEmpty()) { "Firebase configuration is missing for app.receiver." }
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
                    .put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName),
                    authIdentity?.idToken ?: error("Sign in with Google before connecting this receiver.")
                )
            }
            identity.recordRegistered()
            statusChip.text = "Receiver connected"
            statusText.text = "Registered with the live backend. You can now send a notice from Sender."
            stageText.text = "COMPLETE"
            connectButton.text = "Refresh registration"
        } catch (error: Throwable) {
            statusChip.text = "Connection needs attention"
            statusText.text = "The connection stopped before registration completed."
            stageText.text = "ACTION REQUIRED"
            connectButton.text = "Try again"
            showDiagnostic(error)
        } finally {
            connectButton.isEnabled = true
        }
    }

    private fun authenticate(): Job = lifecycleScope.launch {
        authButton.isEnabled = false
        authButton.text = "Signing in…"
        runCatching { authSession.signIn() }.onSuccess {
            authIdentity = it
            renderAuth(it)
            statusChip.text = "Google account connected"
            statusText.text = "${it.authMethod} is active. This Receiver can now register securely."
        }.onFailure { error ->
            authSummary.text = error.message ?: "Secure authentication did not complete."
            statusChip.text = "Sign-in needs attention"
            statusText.text = "Retry authentication before registering this Receiver."
        }.also {
            authButton.isEnabled = true
            authButton.text = if (authIdentity == null) authSession.preferredButtonText() else "Sign out"
            authButton.setOnClickListener { if (authIdentity == null) authenticate() else signOut() }
        }
    }

    private fun signOut(): Job = lifecycleScope.launch {
        authSession.signOut()
        authIdentity = null
        authSummary.text = "Not authenticated. Secure this Receiver before connecting it."
        authButton.text = authSession.preferredButtonText()
        authButton.setOnClickListener { authenticate() }
    }

    private suspend fun refreshAuth() {
        runCatching { authSession.current() }.onSuccess {
            authIdentity = it
            if (it != null && ::authSummary.isInitialized) renderAuth(it)
        }
    }

    private fun renderAuth(identity: AuthenticatedIdentity) {
        authSummary.text = "${identity.authMethod}: ${identity.email ?: identity.displayName ?: "device identity"}. This session identifies the Receiver installation."
        authButton.text = "Sign out"
        authButton.setOnClickListener { signOut() }
    }

    private fun refreshPresentation() {
        val registeredAt = identity.lastRegisteredAt()
        val firebaseReady = runCatching { FirebaseBootstrap.ensureInitialized(this); true }.getOrDefault(false)
        when {
            !BackendClient.isConfigured() -> {
                statusChip.text = "Backend URL needed"
                statusText.text = "This build needs its real reachable HTTPS backend URL before it can connect."
                stageText.text = "CONFIGURATION"
            }
            !firebaseReady -> {
                statusChip.text = "Firebase file needed"
                statusText.text = "Firebase configuration is missing for app.receiver."
                stageText.text = "CONFIGURATION"
            }
            registeredAt > 0L -> {
                statusChip.text = "Receiver connected"
                statusText.text = "Last registered ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(registeredAt))}."
                stageText.text = "READY FOR NOTICES"
            }
            else -> {
                statusChip.text = "Ready to connect"
                statusText.text = "Tap Connect to request an FCM token and register this device."
                stageText.text = "WAITING"
            }
        }
        renderNoticeHistory()
    }

    private fun renderNoticeHistory() {
        if (!::historyList.isInitialized) return
        val history = identity.noticeHistory()
        historyList.removeAllViews()
        if (history.isEmpty()) {
            lastNoticeText.text = "No notice has been received on this device yet."
            clearHistoryButton.isEnabled = false
            return
        }
        val newest = history.first()
        lastNoticeText.text = "Latest\n${newest.title}\n\n${newest.body}"
        clearHistoryButton.isEnabled = true
        history.forEach { notice ->
            historyList.addView(historyItem(notice), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) })
        }
    }

    private fun historyItem(notice: NoticeRecord): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(12).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.parseColor("#F3F8F6"))
        strokeColor = Color.parseColor("#DCE8E3")
        strokeWidth = dp(1)
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(label(notice.title, Color.parseColor("#172B30"), 15f, Typeface.BOLD))
            addView(label(notice.body, Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply {
                setPadding(0, dp(5), 0, 0)
            })
            if (notice.receivedAt > 0L) {
                addView(label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.receivedAt)), Color.parseColor("#0E5D5A"), 11f, Typeface.BOLD).apply {
                    setPadding(0, dp(7), 0, 0)
                })
            }
        })
    }

    private fun setConnecting(stage: String, text: String) {
        connectButton.isEnabled = false
        connectButton.text = "Connecting…"
        statusChip.text = "Connecting"
        statusText.text = text
        stageText.text = stage.uppercase()
    }

    private fun clearDiagnostic() {
        diagnosticText.visibility = View.GONE
        copyDiagnosticButton.visibility = View.GONE
        diagnosticText.text = ""
    }

    private fun showDiagnostic(error: Throwable) {
        val chain = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
            .take(3)
            .joinToString(" → ")
        diagnosticText.text = if (chain.isNotBlank()) {
            "Diagnostic: $chain"
        } else {
            "Diagnostic: ${error.javaClass.simpleName}. Firebase did not return a token; confirm Google Play services and try again."
        }
        diagnosticText.visibility = View.VISIBLE
        copyDiagnosticButton.visibility = View.VISIBLE
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        cardElevation = dp(1).toFloat()
        setCardBackgroundColor(Color.WHITE)
        strokeColor = Color.parseColor("#E0E7E4")
        strokeWidth = dp(1)
    }

    private fun label(text: String, color: Int, size: Float, style: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = size
        typeface = Typeface.create("sans", style)
        gravity = Gravity.START
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
