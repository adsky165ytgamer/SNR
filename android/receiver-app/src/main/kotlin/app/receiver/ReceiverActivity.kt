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
import android.text.InputFilter
import android.text.InputType
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

/** Guided Receiver setup and local notice history dashboard. */
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
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
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
            PeriodicWorkRequestBuilder<ReceiverHeartbeatWorker>(15, TimeUnit.MINUTES).build(),
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
            setBackgroundColor(Color.parseColor("#F4F7F6"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#102E32"))
        }
        header.addView(label("NOTICEFLOW  /  RECEIVER", Color.parseColor("#9CE1D2"), 12f, Typeface.BOLD))
        header.addView(label("Make this device the place notices arrive.", Color.WHITE, 28f, Typeface.BOLD).apply {
            setPadding(0, dp(10), 0, 0)
        })
        header.addView(label("A short setup connects this screen to your school’s live notice network.", Color.parseColor("#D9EFEB"), 15f, Typeface.NORMAL).apply {
            setPadding(0, dp(8), 0, 0)
        })
        page.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(34))
        }
        val setupRail = label("1  ACCOUNT     2  DEVICE NAME     3  CONNECT", Color.parseColor("#0E5D5A"), 11f, Typeface.BOLD).apply {
            setPadding(dp(4), 0, 0, dp(12))
        }
        body.addView(setupRail)
        statusChip = Chip(this).apply {
            isClickable = false
            chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#D9F2EC"))
            setTextColor(Color.parseColor("#0E5D5A"))
            text = "Preparing setup"
        }
        body.addView(statusChip)
        statusText = label("Sign in, name this Receiver, then connect it to the live backend.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply {
            setPadding(dp(2), dp(10), dp(2), dp(4))
        }
        body.addView(statusText)
        stageText = label("ACCOUNT SETUP", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD).apply {
            setPadding(dp(2), 0, 0, dp(16))
        }
        body.addView(stageText)

        body.addView(sectionCard("STEP 1  ·  SECURE ACCOUNT", authPanel(), 14))
        body.addView(sectionCard("STEP 2  ·  NAME THIS DEVICE", deviceSetupPanel(), 14))
        body.addView(sectionCard("STEP 3  ·  CONNECT TO LIVE NOTICES", connectionPanel(), 14))
        body.addView(sectionCard("NOTICE INBOX", noticePanel(), 0))
        body.addView(label("BACKEND  ·  ${BackendClient.endpointLabel()}", Color.parseColor("#76858A"), 11f, Typeface.NORMAL).apply {
            setPadding(dp(4), dp(16), dp(4), 0)
        })
        page.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            header.setPadding(dp(24) + safe.left, dp(28) + safe.top, dp(24) + safe.right, dp(24))
            body.setPadding(dp(20) + safe.left, dp(20), dp(20) + safe.right, dp(34) + safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(page)
        body.post {
            for (index in 0 until body.childCount) {
                val child = body.getChildAt(index)
                child.alpha = 0f
                child.translationY = dp(12).toFloat()
                child.animate().alpha(1f).translationY(0f).setStartDelay(index * 45L).setDuration(260L).start()
            }
        }
        return page
    }

    private fun sectionCard(title: String, content: View, marginBottom: Int): MaterialCardView = card().apply {
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD).apply {
                setPadding(dp(18), dp(18), dp(18), dp(2))
            })
            addView(content)
        })
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            this.bottomMargin = dp(marginBottom)
        }
    }

    private fun authPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(8), dp(18), dp(18))
        authSummary = label("Use the Email/Password provider enabled in school-notics.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(10))
        }
        addView(authSummary)
        val emailLayout = TextInputLayout(this@ReceiverActivity).apply {
            hint = "Account email"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        emailInput = TextInputEditText(this@ReceiverActivity).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        emailLayout.addView(emailInput)
        addView(emailLayout)
        val passwordLayout = TextInputLayout(this@ReceiverActivity).apply {
            hint = "Password"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        passwordInput = TextInputEditText(this@ReceiverActivity).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        passwordLayout.addView(passwordInput)
        addView(passwordLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        authButton = MaterialButton(this@ReceiverActivity).apply {
            text = "Sign in securely"
            setOnClickListener { signInWithEmail() }
        }
        addView(authButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(14) })
        val actions = LinearLayout(this@ReceiverActivity).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Create account"
            setOnClickListener { createAccount() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Reset password"
            setOnClickListener { resetPassword() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
    }

    private fun deviceSetupPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(8), dp(18), dp(18))
        addView(label("Choose a name people will recognize in the Sender app.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(10))
        })
        val nameLayout = TextInputLayout(this@ReceiverActivity).apply {
            hint = "Device name"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            helperText = "Examples: Front Office, Class 8A, Library Display"
        }
        nameInput = TextInputEditText(this@ReceiverActivity).apply {
            setText(identity.name().orEmpty())
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(60))
        }
        nameLayout.addView(nameInput)
        addView(nameLayout)
        addView(label("Device ID  ·  ${identity.receiverId()}", Color.parseColor("#76858A"), 11f, Typeface.NORMAL).apply {
            setPadding(0, dp(10), 0, dp(6))
        })
        addView(MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy device ID"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Receiver ID", identity.receiverId()))
                text = "Device ID copied"
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun connectionPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(8), dp(18), dp(18))
        addView(label("When you connect, NoticeFlow requests a live FCM push token and registers this named device.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(12))
        })
        connectButton = MaterialButton(this@ReceiverActivity).apply {
            text = "Connect this Receiver"
            setOnClickListener { registerReceiver() }
        }
        addView(connectButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        diagnosticText = label("", Color.parseColor("#8D3030"), 13f, Typeface.NORMAL).apply {
            visibility = View.GONE
            setPadding(dp(4), dp(10), dp(4), dp(4))
        }
        addView(diagnosticText)
        copyDiagnosticButton = MaterialButton(this@ReceiverActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy diagnostics"
            visibility = View.GONE
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Receiver diagnostics", diagnosticText.text))
                text = "Diagnostics copied"
            }
        }
        addView(copyDiagnosticButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun noticePanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(8), dp(18), dp(18))
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
        addView(clearHistoryButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    private fun registerReceiver() = lifecycleScope.launch {
        val signedIn = authIdentity
        if (signedIn == null) {
            statusChip.text = "Account required"
            statusText.text = "Complete Step 1 before connecting this Receiver."
            stageText.text = "ACCOUNT SETUP"
            return@launch
        }
        val chosenName = nameInput.text?.toString()?.trim().orEmpty()
        if (chosenName.isBlank()) {
            statusChip.text = "Name required"
            statusText.text = "Choose a device name so the Sender can identify this screen."
            stageText.text = "DEVICE NAME"
            nameInput.requestFocus()
            return@launch
        }
        identity.setName(chosenName)
        clearDiagnostic()
        connectButton.isEnabled = false
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
            setConnecting("Registering device", "Saving ${identity.name()} with the school backend…")
            withContext(Dispatchers.IO) {
                BackendClient.post("/api/v1/receivers/register", JSONObject()
                    .put("receiverId", identity.receiverId())
                    .put("name", identity.name())
                    .put("fcmToken", token)
                    .put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName),
                    signedIn.idToken,
                )
            }
            identity.recordRegistered()
            statusChip.text = "Receiver connected"
            statusText.text = "${identity.name()} is live. The Sender can now deliver notices here."
            stageText.text = "READY FOR NOTICES"
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

    private fun signInWithEmail(): Job = lifecycleScope.launch {
        setAuthBusy(true, "Signing in…")
        runCatching { authSession.signInWithEmail(emailInput.text?.toString().orEmpty(), passwordInput.text?.toString().orEmpty()) }
            .onSuccess { completeAuthentication(it) }
            .onFailure { showAuthError(it) }
            .also { setAuthBusy(false, if (authIdentity == null) "Sign in securely" else "Sign out") }
    }

    private fun createAccount(): Job = lifecycleScope.launch {
        setAuthBusy(true, "Creating account…")
        runCatching { authSession.createEmailAccount(emailInput.text?.toString().orEmpty(), passwordInput.text?.toString().orEmpty()) }
            .onSuccess { completeAuthentication(it) }
            .onFailure { showAuthError(it) }
            .also { setAuthBusy(false, if (authIdentity == null) "Sign in securely" else "Sign out") }
    }

    private fun resetPassword(): Job = lifecycleScope.launch {
        setAuthBusy(true, "Sending reset email…")
        runCatching { authSession.sendPasswordReset(emailInput.text?.toString().orEmpty()) }
            .onSuccess {
                authSummary.text = "Password reset email sent. Check ${emailInput.text}."
                statusChip.text = "Reset email sent"
                statusText.text = "Set a new password, then return to complete setup."
            }
            .onFailure { showAuthError(it) }
            .also { setAuthBusy(false, "Sign in securely") }
    }

    private fun completeAuthentication(identity: AuthenticatedIdentity) {
        authIdentity = identity
        authSummary.text = "${identity.authMethod}: ${identity.email ?: "account"}. Now choose a device name."
        authButton.text = "Sign out"
        authButton.setOnClickListener { signOut() }
        statusChip.text = "Account connected"
        statusText.text = "Account verified. Choose a name for this Receiver, then connect it."
        stageText.text = "DEVICE NAME"
    }

    private fun showAuthError(error: Throwable) {
        authSummary.text = error.message ?: "Firebase authentication did not complete."
        statusChip.text = "Authentication failed"
        statusText.text = "Check the account details, then try again."
        stageText.text = "ACCOUNT SETUP"
    }

    private fun setAuthBusy(busy: Boolean, buttonText: String) {
        authButton.isEnabled = !busy
        authButton.text = buttonText
        if (::emailInput.isInitialized) emailInput.isEnabled = !busy
        if (::passwordInput.isInitialized) passwordInput.isEnabled = !busy
    }

    private fun signOut(): Job = lifecycleScope.launch {
        authSession.signOut()
        authIdentity = null
        authSummary.text = "Sign in with the Email/Password account enabled in school-notics."
        authButton.text = "Sign in securely"
        authButton.setOnClickListener { signInWithEmail() }
        statusChip.text = "Signed out"
        statusText.text = "Sign in again to manage this Receiver."
        stageText.text = "ACCOUNT SETUP"
    }

    private suspend fun refreshAuth() {
        runCatching { authSession.current() }.onSuccess { current ->
            authIdentity = current
            if (current != null && ::authSummary.isInitialized) {
                authSummary.text = "${current.authMethod}: ${current.email ?: "account"}. Choose a device name, then connect."
                authButton.text = "Sign out"
                authButton.setOnClickListener { signOut() }
            }
        }
    }

    private fun refreshPresentation() {
        val registeredAt = identity.lastRegisteredAt()
        val firebaseReady = runCatching { FirebaseBootstrap.ensureInitialized(this); true }.getOrDefault(false)
        when {
            !BackendClient.isConfigured() -> { statusChip.text = "Backend URL needed"; statusText.text = "This build needs its reachable HTTPS backend URL before it can connect."; stageText.text = "CONFIGURATION" }
            !firebaseReady -> { statusChip.text = "Firebase setup needed"; statusText.text = "Firebase configuration is missing for app.receiver."; stageText.text = "CONFIGURATION" }
            registeredAt > 0L -> { statusChip.text = "Receiver connected"; statusText.text = "${identity.name() ?: "This device"} was last registered ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(registeredAt))}."; stageText.text = "READY FOR NOTICES" }
            else -> { statusChip.text = "Ready for setup"; statusText.text = "Sign in, choose a name, and connect this Receiver."; stageText.text = "ACCOUNT SETUP" }
        }
        renderNoticeHistory()
    }

    private fun renderNoticeHistory() {
        if (!::historyList.isInitialized) return
        val history = identity.noticeHistory()
        historyList.removeAllViews()
        if (history.isEmpty()) {
            lastNoticeText.text = "Your incoming notices will appear here."
            clearHistoryButton.isEnabled = false
            return
        }
        val newest = history.first()
        lastNoticeText.text = "LATEST NOTICE\n${newest.title}\n\n${newest.body}"
        clearHistoryButton.isEnabled = true
        history.forEach { notice -> historyList.addView(historyItem(notice), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }) }
    }

    private fun historyItem(notice: NoticeRecord): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(14).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.parseColor("#F1F8F5"))
        strokeColor = Color.parseColor("#D5E8E0")
        strokeWidth = dp(1)
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(label(notice.title, Color.parseColor("#102E32"), 15f, Typeface.BOLD))
            addView(label(notice.body, Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(5), 0, 0) })
            if (notice.receivedAt > 0L) addView(label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.receivedAt)), Color.parseColor("#0E5D5A"), 11f, Typeface.BOLD).apply { setPadding(0, dp(7), 0, 0) })
        })
    }

    private fun setConnecting(stage: String, text: String) {
        connectButton.isEnabled = false
        connectButton.text = "Connecting…"
        statusChip.text = "Connecting"
        statusText.text = text
        stageText.text = stage.uppercase()
    }

    private fun clearDiagnostic() { diagnosticText.visibility = View.GONE; copyDiagnosticButton.visibility = View.GONE; diagnosticText.text = "" }

    private fun showDiagnostic(error: Throwable) {
        val chain = generateSequence(error) { it.cause }.mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }.take(3).joinToString(" → ")
        diagnosticText.text = if (chain.isNotBlank()) "Diagnostic: $chain" else "Diagnostic: ${error.javaClass.simpleName}. Confirm the backend URL and Firebase setup, then retry."
        diagnosticText.visibility = View.VISIBLE
        copyDiagnosticButton.visibility = View.VISIBLE
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        cardElevation = dp(1).toFloat()
        setCardBackgroundColor(Color.WHITE)
        strokeColor = Color.parseColor("#DDE8E4")
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
