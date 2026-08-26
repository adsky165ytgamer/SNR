package app.receiver

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.receiver.auth.AuthenticatedIdentity
import app.receiver.auth.FirebaseBootstrap
import app.receiver.auth.GoogleAuthSession
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

private enum class ReceiverTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), INBOX("Inbox", Icons.Default.Notifications),
    DEVICE("Device", Icons.Default.Devices), SETTINGS("Settings", Icons.Default.Settings)
}

private val Ink = Color(0xFFEDEDED)
private val Muted = Color(0xFF9EA4AA)
private val Panel = Color(0xFF151719)
private val Accent = Color(0xFF8DE8C9)
private val AccentDark = Color(0xFF173A31)

class ReceiverActivity : ComponentActivity() {
    private val identity by lazy { ReceiverIdentity(applicationContext) }
    private val authSession by lazy { GoogleAuthSession(this) }
    private var authIdentity by mutableStateOf<AuthenticatedIdentity?>(null)
    private var nameValue by mutableStateOf("")
    private var emailValue by mutableStateOf("")
    private var passwordValue by mutableStateOf("")
    private var statusTitle by mutableStateOf("Receiver ready")
    private var statusDetail by mutableStateOf("Sign in, name this screen, then connect it to receive live notices.")
    private var busy by mutableStateOf(false)
    private var tab by mutableStateOf(ReceiverTab.HOME)
    private var inboxRevision by mutableIntStateOf(0)
    private var overlayPermissionGranted by mutableStateOf(false)
    private val receiverPreferences by lazy { getSharedPreferences("receiver_identity", Context.MODE_PRIVATE) }
    private val noticeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "notice_history") runOnUiThread { inboxRevision++ }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overlayPermissionGranted = NoticeOverlayController.canDrawOverOtherApps(this)
        nameValue = identity.name().orEmpty()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "receiver-heartbeat", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReceiverHeartbeatWorker>(15, TimeUnit.MINUTES).build()
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { NoticeFlowReceiverTheme { ReceiverShell() } }
        lifecycleScope.launch {
            authIdentity = runCatching { authSession.current() }.getOrNull()
            if (authIdentity != null) {
                statusTitle = "Account connected"
                statusDetail = if (identity.lastRegisteredAt() > 0L) "This Receiver is registered and ready for notices." else "Name this Receiver and connect it."
            }
        }
        openInboxForNotification(intent)
    }

    override fun onStart() { super.onStart(); ReceiverPresentationState.markForeground(true); receiverPreferences.registerOnSharedPreferenceChangeListener(noticeListener) }
    override fun onStop() { ReceiverPresentationState.markForeground(false); receiverPreferences.unregisterOnSharedPreferenceChangeListener(noticeListener); super.onStop() }
    override fun onResume() { super.onResume(); overlayPermissionGranted = NoticeOverlayController.canDrawOverOtherApps(this); inboxRevision++ }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openInboxForNotification(intent) }

    private fun openInboxForNotification(intent: Intent?) {
        if (!intent?.getStringExtra("noticeId").isNullOrBlank()) { inboxRevision++; tab = ReceiverTab.INBOX }
    }

    @Composable private fun ReceiverShell() = Surface(Modifier.fillMaxSize(), color = Color(0xFF0E1011)) {
        Column(Modifier.fillMaxSize()) {
            ReceiverTopBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "receiver-tab") { current ->
                    when (current) {
                        ReceiverTab.HOME -> HomeScreen()
                        ReceiverTab.INBOX -> InboxScreen()
                        ReceiverTab.DEVICE -> DeviceScreen()
                        ReceiverTab.SETTINGS -> SettingsScreen()
                    }
                }
            }
            NavigationBar(containerColor = Color(0xFF111315), tonalElevation = 0.dp) {
                ReceiverTab.entries.forEach { item ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF10201B), indicatorColor = Accent, selectedTextColor = Accent, unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    }

    @Composable private fun ReceiverTopBar() = Surface(color = Color(0xFF111315)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NOTICEFLOW", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text(nameValue.ifBlank { "Receiver" }, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill()
        }
    }

    @Composable private fun StatusPill() {
        val connected = authIdentity != null && identity.lastRegisteredAt() > 0L
        Surface(shape = RoundedCornerShape(50), color = if (connected) AccentDark else Color(0xFF282C30)) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) Accent else Color(0xFFFFC86B)))
                Spacer(Modifier.width(7.dp)); Text(if (connected) "Connected" else "Setup", color = if (connected) Accent else Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    @Composable private fun HomeScreen() {
        val notices = remember(inboxRevision) { identity.noticeHistory() }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ScreenHeading("Good to see you.", "This screen is your live school notice hub.") }
            item { ConnectionCard() }
            item { NoticePreviewCard(notices.firstOrNull(), notices.size) { tab = ReceiverTab.INBOX } }
            item { SectionCard("How it works", "Sender → cloud → this Receiver", Icons.Default.Bolt) { Text("Every delivery is stored locally before this app displays its own notification, so your Inbox retains received notices after the alert disappears.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp) } }
        }
    }

    @Composable private fun ConnectionCard() {
        val connected = authIdentity != null && identity.lastRegisteredAt() > 0L
        Surface(shape = RoundedCornerShape(24.dp), color = if (connected) Accent else Panel) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (connected) Icons.Default.CloudDone else Icons.Default.CloudOff, null, tint = if (connected) Color(0xFF12372D) else Color(0xFFFFC86B), modifier = Modifier.size(38.dp))
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                    Text(if (connected) "Live connection" else "Not connected", color = if (connected) Color(0xFF10201B) else Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(if (connected) "Ready to receive notices" else statusDetail, color = if (connected) Color(0xFF35534A) else Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }

    @Composable private fun NoticePreviewCard(latest: NoticeRecord?, count: Int, openInbox: () -> Unit) = Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth().clickable { openInbox() }) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("INBOX", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f)); Text("$count saved", color = Muted, fontSize = 12.sp); Icon(Icons.Default.ChevronRight, null, tint = Muted) }
            Spacer(Modifier.height(12.dp)); Text(latest?.title ?: "No notices yet", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(latest?.body ?: "When a sender broadcasts to this Receiver, every received notice appears here.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }

    @Composable private fun InboxScreen() {
        val notices = remember(inboxRevision) { identity.noticeHistory() }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenHeading("Inbox", if (notices.isEmpty()) "No persisted deliveries yet. New live notices are saved here first." else "${notices.size} persisted delivery${if (notices.size == 1) "" else "ies"} on this Receiver.") }
            if (notices.isEmpty()) item { EmptyState() }
            items(notices, key = { it.id }) { notice -> NoticeCard(notice) }
            if (notices.isNotEmpty()) item { TextButton(onClick = { identity.clearNoticeHistory(); inboxRevision++ }, modifier = Modifier.fillMaxWidth()) { Text("Clear local inbox", color = Accent) } }
        }
    }

    @Composable private fun NoticeCard(notice: NoticeRecord) = Surface(shape = RoundedCornerShape(22.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("${notice.category.label.uppercase()} • ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.receivedAt))}", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(notice.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp))
            Text(notice.body, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    @Composable private fun DeviceScreen() = LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeading("This Receiver", "Give the panel a recognizable live identity before connecting it.") }
        item { SectionCard("Device name", "Visible to Sender after registration", Icons.Default.Label) {
            OutlinedTextField(value = nameValue, onValueChange = { nameValue = it.take(60) }, singleLine = true, label = { Text("Receiver name") }, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
            Spacer(Modifier.height(10.dp)); Button(onClick = { saveDeviceName() }, modifier = Modifier.fillMaxWidth(), colors = actionColors()) { Text("Save device name", fontWeight = FontWeight.Bold) }
        } }
        item { SectionCard("Connection identity", "Local installation details", Icons.Default.Fingerprint) { KeyValue("Receiver ID", identity.receiverId()); Spacer(Modifier.height(10.dp)); KeyValue("Last registered", registrationLabel()); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { copy(identity.receiverId()) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy receiver ID") } } }
        item { SectionCard("Cloud status", "Firebase + backend", Icons.Default.Cloud) { KeyValue("Account", authIdentity?.email ?: "Not signed in"); Spacer(Modifier.height(10.dp)); KeyValue("FCM", if (identity.lastRegisteredAt() > 0L) "Registered" else "Waiting for registration") } }
        item { OverlaySetupCard() }
        item { Button(onClick = { tab = ReceiverTab.SETTINGS }, modifier = Modifier.fillMaxWidth(), colors = actionColors()) { Text("Open connection settings", fontWeight = FontWeight.Bold) } }
    }

    @Composable private fun SettingsScreen() = LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeading("Settings", "Account, connection and application controls.") }
        item { AccountCard() }
        item { ConnectionActionCard() }
        item { OverlaySetupCard() }
        item { SectionCard("About NoticeFlow", "Receiver ${packageManager.getPackageInfo(packageName, 0).versionName}", Icons.Default.Info) { Text("Testing-1 build with live local Inbox persistence and optional Android overlay presentation.", color = Muted, fontSize = 14.sp) } }
    }

    @Composable private fun AccountCard() = SectionCard("School account", authIdentity?.email ?: "Sign in to connect this Receiver", Icons.Default.AccountCircle) {
        if (authIdentity == null) {
            OutlinedTextField(emailValue, { emailValue = it }, singleLine = true, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
            Spacer(Modifier.height(10.dp)); OutlinedTextField(passwordValue, { passwordValue = it }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
            Spacer(Modifier.height(12.dp)); Button(onClick = { signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = actionColors()) { Text(if (busy) "Signing in…" else "Sign in with email", fontWeight = FontWeight.Bold) }
            Row { TextButton(onClick = { createAccount() }, enabled = !busy) { Text("Create account", color = Accent) }; TextButton(onClick = { resetPassword() }, enabled = !busy) { Text("Reset password", color = Accent) } }
            Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Color(0xFF34393D)); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { signInWithGoogle() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Continue with Google") }
        } else {
            Text("Signed in as ${authIdentity?.email ?: "school account"}.", color = Ink, fontSize = 14.sp); Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { signOut() }, enabled = !busy) { Text("Sign out") }
        }
    }

    @Composable private fun ConnectionActionCard() = SectionCard("Live connection", statusTitle, Icons.Default.Wifi) {
        Text(statusDetail, color = Muted, fontSize = 14.sp, lineHeight = 21.sp); Spacer(Modifier.height(12.dp))
        Button(onClick = { registerReceiver() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = actionColors()) { Text(if (busy) "Connecting…" else if (identity.lastRegisteredAt() > 0L) "Refresh connection" else "Connect Receiver", fontWeight = FontWeight.Bold) }
    }

    @Composable private fun OverlaySetupCard() = SectionCard(
        "Display notices over other apps",
        if (overlayPermissionGranted) "Enabled by Android" else "Optional Android permission",
        Icons.Default.NotificationsActive,
    ) {
        Text(
            if (overlayPermissionGranted) {
                "Notice-category deliveries can appear above another app when this Receiver is in the background. Homework and News continue using the normal notification and Inbox flow."
            } else {
                "To show Notice-category deliveries while another app is open, Android requires Display Over Other Apps permission. Without it, every delivery remains in the local Inbox and the existing high-priority notification flow."
            },
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { startActivity(NoticeOverlayController.overlaySettingsIntent(this@ReceiverActivity)) },
            modifier = Modifier.fillMaxWidth(),
            colors = actionColors(),
        ) { Text(if (overlayPermissionGranted) "Open Android overlay settings" else "Enable overlay in Android settings", fontWeight = FontWeight.Bold) }
    }

    private fun saveDeviceName() { val value = nameValue.trim(); if (value.isBlank()) { statusTitle = "Name required"; statusDetail = "Choose a recognizable Receiver name."; return }; identity.setName(value); nameValue = value; statusTitle = "Device name saved"; statusDetail = "$value will appear to Sender after connection." }
    private fun signIn() = lifecycleScope.launch { busy = true; runCatching { authSession.signInWithEmail(emailValue.trim(), passwordValue) }.onSuccess { authIdentity = it; passwordValue = ""; statusTitle = "Account connected"; statusDetail = "Now connect this named Receiver." }.onFailure { statusTitle = "Sign-in failed"; statusDetail = it.message ?: "Firebase authentication did not complete." }; busy = false }
    private fun signInWithGoogle() = lifecycleScope.launch { busy = true; runCatching { authSession.signInWithGoogle(BuildConfig.GOOGLE_WEB_CLIENT_ID) }.onSuccess { authIdentity = it; statusTitle = "Google account connected"; statusDetail = "Now connect this named Receiver." }.onFailure { statusTitle = "Google Sign-In failed"; statusDetail = it.message ?: "Google Sign-In did not complete." }; busy = false }
    private fun createAccount() = lifecycleScope.launch { busy = true; runCatching { authSession.createEmailAccount(emailValue.trim(), passwordValue) }.onSuccess { authIdentity = it; passwordValue = ""; statusTitle = "Account created"; statusDetail = "Name the device and connect it." }.onFailure { statusTitle = "Could not create account"; statusDetail = it.message ?: "Firebase authentication did not complete." }; busy = false }
    private fun resetPassword() = lifecycleScope.launch { busy = true; runCatching { authSession.sendPasswordReset(emailValue.trim()) }.onSuccess { statusTitle = "Reset email sent"; statusDetail = "Check the inbox for $emailValue." }.onFailure { statusTitle = "Reset failed"; statusDetail = it.message ?: "Could not send the reset email." }; busy = false }
    private fun signOut() = lifecycleScope.launch { busy = true; runCatching { authSession.signOut() }; authIdentity = null; statusTitle = "Signed out"; statusDetail = "Sign in again before refreshing this Receiver connection."; busy = false }

    private fun registerReceiver() = lifecycleScope.launch {
        if (authIdentity == null) { tab = ReceiverTab.SETTINGS; statusTitle = "Account required"; statusDetail = "Sign in before connecting this Receiver."; return@launch }
        val name = nameValue.trim(); if (name.isBlank()) { tab = ReceiverTab.DEVICE; statusTitle = "Name required"; statusDetail = "Give the Receiver a recognizable name first."; return@launch }
        busy = true
        try {
            identity.setName(name); nameValue = name; check(BackendClient.isConfigured()) { "A reachable HTTPS backend URL has not been configured." }; withContext(Dispatchers.IO) { BackendClient.get("/health") }
            FirebaseBootstrap.ensureInitialized(this@ReceiverActivity); check(FirebaseApp.getApps(this@ReceiverActivity).isNotEmpty()) { "Firebase configuration is missing for app.receiver." }
            check(FirebaseInstallations.getInstance().id.await().isNotBlank()) { "Firebase could not create a device installation." }; val token = FirebaseMessaging.getInstance().token.await(); check(token.isNotBlank()) { "Firebase returned an empty FCM token." }
            withContext(Dispatchers.IO) { BackendClient.post("/api/v1/receivers/register", JSONObject().put("receiverId", identity.receiverId()).put("name", name).put("fcmToken", token).put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName), authIdentity!!.idToken) }
            identity.recordRegistered(); statusTitle = "Receiver connected"; statusDetail = "$name is registered and ready for notices."; tab = ReceiverTab.HOME
        } catch (error: Throwable) { statusTitle = "Connection failed"; statusDetail = error.message ?: "The connection stopped before registration completed."; tab = ReceiverTab.SETTINGS } finally { busy = false }
    }

    private fun registrationLabel() = if (identity.lastRegisteredAt() == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(identity.lastRegisteredAt()))
    private fun copy(text: String) { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Receiver ID", text)) }
    @Composable private fun ScreenHeading(title: String, subtitle: String) { Column { Text(title, color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp)) } }
    @Composable private fun SectionCard(title: String, subtitle: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) = Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(11.dp)); Column { Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }; Spacer(Modifier.height(16.dp)); content() } }
    @Composable private fun KeyValue(label: String, value: String) { Column { Text(label.uppercase(), color = Color(0xFF727A80), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(value, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp)) } }
    @Composable private fun EmptyState() = Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.NotificationsNone, null, tint = Accent, modifier = Modifier.size(40.dp)); Text("Inbox is quiet", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)); Text("Your first school notice will appear here.", color = Muted, fontSize = 14.sp) } }
    @Composable private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF3B4145), focusedLabelColor = Accent, unfocusedLabelColor = Muted, cursorColor = Accent, focusedTextColor = Ink, unfocusedTextColor = Ink)
    @Composable private fun actionColors() = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF10201B))
}

@Composable private fun NoticeFlowReceiverTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0E1011), surface = Panel, primary = Accent, onPrimary = Color(0xFF10201B), onBackground = Ink, onSurface = Ink, onSurfaceVariant = Muted), content = content)
