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
import app.receiver.auth.AuthenticatedIdentity
import app.receiver.auth.FirebaseBootstrap
import app.receiver.auth.GoogleAuthSession
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

/** NoticeFlow Receiver v1.1.1 Alpha: Material 3 guided setup, inbox, and live device connection. */
class ReceiverActivity : ComponentActivity() {
    private val identity by lazy { ReceiverIdentity(applicationContext) }
    private val authSession by lazy { GoogleAuthSession(this) }
    private var authIdentity: AuthenticatedIdentity? = null
    private var activeSection = Section.HOME
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private lateinit var statusChip: Chip
    private lateinit var statusText: TextView
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var nameInput: TextInputEditText
    private lateinit var connectButton: MaterialButton
    private var statusTitle = "Welcome to NoticeFlow"
    private var statusDetail = "Follow the short setup to make this screen ready for live school notices."

    private enum class Section(val label: String) {
        HOME("Home"), SETUP("Setup"), INBOX("Inbox"), ABOUT("About")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        requestNotificationPermission()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "receiver-heartbeat", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReceiverHeartbeatWorker>(15, TimeUnit.MINUTES).build(),
        )
        lifecycleScope.launch { authIdentity = runCatching { authSession.current() }.getOrNull() }
        if (identity.hasCompletedOnboarding()) showApplication() else showOnboarding()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && activeSection == Section.HOME) renderSection(Section.HOME)
    }

    private fun showOnboarding() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#102E32"))
            setPadding(dp(24), dp(24), dp(24), dp(30))
        }
        page.addView(label("NOTICEFLOW  /  RECEIVER", Color.parseColor("#9CE1D2"), 12f, Typeface.BOLD))
        page.addView(label("A softer way to stay in the loop.", Color.WHITE, 31f, Typeface.BOLD).apply {
            setPadding(0, dp(18), 0, 0)
        })
        page.addView(label("This device becomes a calm, dependable place for school notices to arrive.", Color.parseColor("#D9EFEB"), 16f, Typeface.NORMAL).apply {
            setPadding(0, dp(12), 0, dp(20))
        })
        page.addView(onboardingCard("01  Secure the screen", "Use your Receiver Email/Password account. Your account authorizes the live connection."))
        page.addView(onboardingCard("02  Give it a real name", "Choose a name such as Front Office, Class 8A, or Library Display so Senders know exactly where a notice goes."), margins(top = 12))
        page.addView(onboardingCard("03  Let notices flow", "Connect once. NoticeFlow then keeps a private inbox on this device and refreshes its live connection."), margins(top = 12))
        val start = MaterialButton(this).apply {
            text = "Begin Receiver setup"
            isAllCaps = false
            minHeight = dp(52)
            cornerRadius = dp(16)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0E5D5A"))
            setOnClickListener {
                identity.completeOnboarding()
                showApplication()
            }
        }
        page.addView(start, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(24) })
        page.addView(label("v1.1.1 Alpha  ·  Crafted by ad_vibe_dev", Color.parseColor("#9CE1D2"), 12f, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        })
        applyInsets(page, page, null)
        setContentView(page)
        page.alpha = 0f
        page.animate().alpha(1f).setDuration(360L).start()
    }

    private fun showApplication() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F6F7F7"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
            setBackgroundColor(Color.parseColor("#102E32"))
        }
        header.addView(label("NOTICEFLOW  /  RECEIVER", Color.parseColor("#9CE1D2"), 11f, Typeface.BOLD))
        header.addView(label("Your notice space", Color.WHITE, 25f, Typeface.BOLD).apply { setPadding(0, dp(7), 0, 0) })
        header.addView(label(identity.name() ?: "Name this device in Setup", Color.parseColor("#D9EFEB"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(4), 0, 0) })
        page.addView(header)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        Section.entries.forEach { section ->
            nav.addView(MaterialButton(this).apply {
                text = section.label
                isAllCaps = false
                setOnClickListener { renderSection(section) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = if (section == Section.HOME) 0 else dp(5) })
        }
        page.addView(nav)
        applyInsets(page, header, nav)
        setContentView(page)
        renderSection(activeSection)
    }

    private fun renderSection(section: Section) {
        activeSection = section
        content.removeAllViews()
        navButtons(section)
        when (section) {
            Section.HOME -> renderHome()
            Section.SETUP -> renderSetup()
            Section.INBOX -> renderInbox()
            Section.ABOUT -> renderAbout()
        }
        content.alpha = 0f
        content.translationY = dp(8).toFloat()
        content.animate().alpha(1f).translationY(0f).setDuration(220L).start()
    }

    private fun navButtons(selected: Section) {
        Section.entries.forEachIndexed { index, section ->
            val button = nav.getChildAt(index) as MaterialButton
            button.setTextColor(Color.parseColor(if (section == selected) "#FFFFFF" else "#0E5D5A"))
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (section == selected) "#0E5D5A" else "#E7F4F0"))
        }
    }

    private fun renderHome() {
        val body = sectionBody()
        statusChip = Chip(this).apply {
            text = readinessLabel()
            isClickable = false
            chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#D8F3EE"))
            setTextColor(Color.parseColor("#0E5D5A"))
        }
        body.addView(statusChip)
        statusText = label(statusDetailForHome(), Color.parseColor("#526168"), 15f, Typeface.NORMAL).apply { setPadding(0, dp(10), 0, dp(16)) }
        body.addView(statusText)
        body.addView(featureCard("Your next move", nextMove(), "Open Setup to complete the account, device name, and connection steps."))
        val latest = identity.noticeHistory().firstOrNull()
        body.addView(featureCard("Latest notice", latest?.title ?: "Nothing new yet", latest?.body ?: "Your incoming notices will appear here after the Sender delivers them."), margins(top = 14))
        body.addView(featureCard("Connection identity", identity.name() ?: "Unnamed Receiver", "Device ID: ${identity.receiverId().take(8)}…  ·  ${if (authIdentity == null) "Account not signed in" else "Account connected"}"), margins(top = 14))
        content.addView(body)
    }

    private fun renderSetup() {
        val body = sectionBody()
        body.addView(sectionTitle("Make this Receiver real", "Three small steps establish an authenticated, named connection."))
        if (statusTitle != "Welcome to NoticeFlow") body.addView(statusCard(), margins(top = 14))
        body.addView(setupAccountCard(), margins(top = 14))
        body.addView(setupNameCard(), margins(top = 14))
        body.addView(setupConnectCard(), margins(top = 14))
        content.addView(body)
    }

    private fun setupAccountCard(): MaterialCardView = card().apply {
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(label("1  ·  ACCOUNT", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
            addView(label(authIdentity?.let { "Signed in as ${it.email ?: "Receiver account"}." } ?: "Use the Email/Password account enabled in school-notics.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(7), 0, dp(12)) })
            if (authIdentity == null) {
                emailInput = emailField("Receiver email")
                passwordInput = passwordField()
                addView(outlinedInput("Receiver email", emailInput))
                addView(outlinedInput("Password", passwordInput), margins(top = 10))
                addView(primaryButton("Sign in securely") { signInWithEmail() }, margins(top = 14))
                val row = LinearLayout(this@ReceiverActivity).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(secondaryButton("Create account") { createAccount() }, LinearLayout.LayoutParams(0, dp(46), 1f))
                row.addView(secondaryButton("Reset password") { resetPassword() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
                addView(row, margins(top = 8))
            } else {
                addView(secondaryButton("Sign out") { signOut() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)))
            }
        })
    }

    private fun setupNameCard(): MaterialCardView = card().apply {
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(label("2  ·  DEVICE NAME", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
            addView(label("This is the name the Sender sees. Keep it human and unmistakable.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(7), 0, dp(12)) })
            nameInput = TextInputEditText(this@ReceiverActivity).apply {
                setText(identity.name().orEmpty())
                setSingleLine()
                filters = arrayOf(InputFilter.LengthFilter(60))
            }
            addView(outlinedInput("Examples: Front Office, Class 8A", nameInput, "Saved when you connect"))
            addView(label("Private device ID  ·  ${identity.receiverId()}", Color.parseColor("#76858A"), 11f, Typeface.NORMAL).apply { setPadding(0, dp(10), 0, dp(4)) })
            addView(secondaryButton("Copy device ID") {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Receiver ID", identity.receiverId()))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)))
        })
    }

    private fun setupConnectCard(): MaterialCardView = card().apply {
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(label("3  ·  LIVE CONNECTION", Color.parseColor("#0E5D5A"), 12f, Typeface.BOLD))
            addView(label("NoticeFlow requests a real FCM token and registers this named screen with the live backend.", Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(7), 0, dp(12)) })
            connectButton = primaryButton(if (identity.lastRegisteredAt() > 0L) "Refresh live connection" else "Connect this Receiver") { registerReceiver() }
            addView(connectButton)
        })
    }

    private fun renderInbox() {
        val body = sectionBody()
        body.addView(sectionTitle("Notice inbox", "Notices are kept locally on this device so they remain visible after the alert disappears."))
        val notices = identity.noticeHistory()
        if (notices.isEmpty()) {
            body.addView(featureCard("The inbox is quiet", "No notices yet", "Connect this Receiver, then use Sender to deliver the first message."), margins(top = 14))
        } else {
            notices.forEach { notice -> body.addView(noticeCard(notice), margins(top = 10)) }
            body.addView(secondaryButton("Clear local inbox") { identity.clearNoticeHistory(); renderSection(Section.INBOX) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)).apply { topMargin = dp(16) })
        }
        content.addView(body)
    }

    private fun renderAbout() {
        val body = sectionBody()
        body.addView(sectionTitle("NoticeFlow Receiver", "A focused receiving space for reliable school communication."))
        body.addView(featureCard("v1.1.1 Alpha", "Built for deliberate delivery", "This Material 3 update refines navigation, interaction feedback, safe-area behavior, and accessible touch targets without changing the live delivery contract."), margins(top = 14))
        body.addView(featureCard("Created by", "ad_vibe_dev", "NoticeFlow is designed as a proprietary school communication product."), margins(top = 14))
        body.addView(featureCard("License and access", "Proprietary — not open source", "No permission is granted to copy, redistribute, reverse engineer, or publish this application or its source without written authorization from the creator."), margins(top = 14))
        body.addView(secondaryButton("Replay introduction") { identity.resetOnboarding(); showOnboarding() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)).apply { topMargin = dp(16) })
        content.addView(body)
    }

    private fun signInWithEmail() = lifecycleScope.launch {
        val result = runCatching { authSession.signInWithEmail(emailInput.text?.toString().orEmpty(), passwordInput.text?.toString().orEmpty()) }
        result.onSuccess { authIdentity = it; setStatus("Account connected", "Your Receiver account is verified. Next, name this device and connect it."); renderSection(Section.SETUP) }
            .onFailure { setStatus("Sign-in needs attention", it.message ?: "Firebase authentication did not complete."); renderSection(Section.SETUP) }
    }

    private fun createAccount() = lifecycleScope.launch {
        val result = runCatching { authSession.createEmailAccount(emailInput.text?.toString().orEmpty(), passwordInput.text?.toString().orEmpty()) }
        result.onSuccess { authIdentity = it; setStatus("Account created", "Choose a device name, then connect this Receiver."); renderSection(Section.SETUP) }
            .onFailure { setStatus("Could not create account", it.message ?: "Firebase authentication did not complete."); renderSection(Section.SETUP) }
    }

    private fun resetPassword() = lifecycleScope.launch {
        val result = runCatching { authSession.sendPasswordReset(emailInput.text?.toString().orEmpty()) }
        setStatus(if (result.isSuccess) "Reset email sent" else "Reset needs attention", result.exceptionOrNull()?.message ?: "Check your inbox, set a new password, then return here.")
        renderSection(Section.SETUP)
    }

    private fun signOut() = lifecycleScope.launch {
        authSession.signOut()
        authIdentity = null
        setStatus("Signed out", "Sign in again before connecting or refreshing this Receiver.")
        renderSection(Section.SETUP)
    }

    private fun registerReceiver() = lifecycleScope.launch {
        if (authIdentity == null) { setStatus("Account required", "Complete Step 1 before connecting this Receiver."); renderSection(Section.SETUP); return@launch }
        val name = nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { setStatus("Device name required", "Choose a recognizable name in Step 2 before connecting."); renderSection(Section.SETUP); return@launch }
        identity.setName(name)
        connectButton.isEnabled = false
        connectButton.text = "Connecting…"
        try {
            check(BackendClient.isConfigured()) { "A reachable HTTPS backend URL has not been configured." }
            withContext(Dispatchers.IO) { BackendClient.get("/health") }
            FirebaseBootstrap.ensureInitialized(this@ReceiverActivity)
            check(FirebaseApp.getApps(this@ReceiverActivity).isNotEmpty()) { "Firebase configuration is missing for app.receiver." }
            check(FirebaseInstallations.getInstance().id.await().isNotBlank()) { "Firebase could not create a device installation." }
            val token = FirebaseMessaging.getInstance().token.await()
            check(token.isNotBlank()) { "Firebase returned an empty FCM token." }
            withContext(Dispatchers.IO) {
                BackendClient.post("/api/v1/receivers/register", JSONObject()
                    .put("receiverId", identity.receiverId())
                    .put("name", identity.name())
                    .put("fcmToken", token)
                    .put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName), authIdentity!!.idToken)
            }
            identity.recordRegistered()
            setStatus("Receiver connected", "${identity.name()} is ready. New notices will appear in Inbox.")
            renderSection(Section.HOME)
        } catch (error: Throwable) {
            setStatus("Connection needs attention", error.message ?: "The connection stopped before registration completed.")
            renderSection(Section.SETUP)
        }
    }

    private fun readinessLabel(): String = when {
        !BackendClient.isConfigured() -> "Backend URL needed"
        authIdentity == null -> "Account required"
        identity.name().isNullOrBlank() -> "Name this device"
        identity.lastRegisteredAt() <= 0L -> "Ready to connect"
        else -> "Receiver live"
    }

    private fun nextMove(): String = when {
        authIdentity == null -> "Sign in to your Receiver account"
        identity.name().isNullOrBlank() -> "Give this device a name"
        identity.lastRegisteredAt() <= 0L -> "Connect to live notices"
        else -> "You are ready for notices"
    }

    private fun statusDetailForHome(): String = if (identity.lastRegisteredAt() > 0L) {
        "${identity.name() ?: "This device"} last registered ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(identity.lastRegisteredAt()))}."
    } else statusDetail

    private fun setStatus(title: String, detail: String) {
        statusTitle = title
        statusDetail = detail
        if (::statusChip.isInitialized) statusChip.text = title
        if (::statusText.isInitialized) statusText.text = detail
    }

    private fun sectionBody(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(24))
    }

    private fun sectionTitle(title: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title, Color.parseColor("#102E32"), 26f, Typeface.BOLD))
        addView(label(detail, Color.parseColor("#526168"), 15f, Typeface.NORMAL).apply { setPadding(0, dp(7), 0, 0) })
    }

    private fun featureCard(eyebrow: String, title: String, detail: String): MaterialCardView = card().apply {
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(label(eyebrow.uppercase(), Color.parseColor("#0E5D5A"), 11f, Typeface.BOLD))
            addView(label(title, Color.parseColor("#102E32"), 18f, Typeface.BOLD).apply { setPadding(0, dp(6), 0, 0) })
            addView(label(detail, Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(7), 0, 0) })
        })
    }

    private fun statusCard(): MaterialCardView = card().apply {
        val isAttention = statusTitle.contains("attention", ignoreCase = true) ||
            statusTitle.contains("failed", ignoreCase = true) ||
            statusTitle.contains("required", ignoreCase = true)
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(label(statusTitle, if (isAttention) Color.parseColor("#8A5A00") else Color.parseColor("#0E5D5A"), 16f, Typeface.BOLD))
            addView(label(statusDetail, Color.parseColor("#526168"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(6), 0, 0) })
        })
    }

    private fun noticeCard(notice: NoticeRecord): MaterialCardView = featureCard(
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.receivedAt)), notice.title, notice.body,
    )

    private fun onboardingCard(title: String, detail: String): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        setCardBackgroundColor(Color.parseColor("#173E43"))
        strokeColor = Color.parseColor("#316267")
        strokeWidth = dp(1)
        addView(LinearLayout(this@ReceiverActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(label(title, Color.parseColor("#9CE1D2"), 13f, Typeface.BOLD))
            addView(label(detail, Color.parseColor("#E5F4F0"), 14f, Typeface.NORMAL).apply { setPadding(0, dp(6), 0, 0) })
        })
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        cardElevation = dp(1).toFloat()
        setCardBackgroundColor(Color.WHITE)
        strokeColor = Color.parseColor("#DCE8E4")
        strokeWidth = dp(1)
    }

    private fun primaryButton(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        minHeight = dp(48)
        cornerRadius = dp(16)
        insetTop = 0
        insetBottom = 0
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0E5D5A"))
        setTextColor(Color.WHITE)
        setOnClickListener { action() }
    }
    private fun secondaryButton(text: String, action: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        minHeight = dp(46)
        cornerRadius = dp(16)
        insetTop = 0
        insetBottom = 0
        strokeColor = ColorStateList.valueOf(Color.parseColor("#B0CFC8"))
        setTextColor(Color.parseColor("#0E5D5A"))
        setOnClickListener { action() }
    }
    private fun emailField(hint: String) = TextInputEditText(this).apply { setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; contentDescription = hint }
    private fun passwordField() = TextInputEditText(this).apply { setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
    private fun outlinedInput(hint: String, input: TextInputEditText, helper: String? = null) = TextInputLayout(this).apply { this.hint = hint; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; helperText = helper; if (hint == "Password") endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE; addView(input) }
    private fun margins(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun label(text: String, color: Int, size: Float, style: Int) = TextView(this).apply { this.text = text; setTextColor(color); textSize = size; typeface = Typeface.create("sans", style) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun applyInsets(page: View, header: View, bottom: View?) {
        val headerLeft = header.paddingLeft
        val headerTop = header.paddingTop
        val headerRight = header.paddingRight
        val headerBottom = header.paddingBottom
        val bottomLeft = bottom?.paddingLeft ?: 0
        val bottomTop = bottom?.paddingTop ?: 0
        val bottomRight = bottom?.paddingRight ?: 0
        val bottomBottom = bottom?.paddingBottom ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            header.setPadding(headerLeft + safe.left, headerTop + safe.top, headerRight + safe.right, headerBottom)
            bottom?.setPadding(bottomLeft + safe.left, bottomTop, bottomRight + safe.right, bottomBottom + safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(page)
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
