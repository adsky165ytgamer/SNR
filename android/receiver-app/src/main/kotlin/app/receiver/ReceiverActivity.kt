package app.receiver

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.net.Uri
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

private enum class ReceiverSection(val label: String, val description: String, val icon: ImageVector) {
    HOME("Overview", "Live status", Icons.Default.Home),
    INBOX("Inbox", "Saved notices", Icons.Default.Notifications),
    DEVICE("This Receiver", "Identity and device", Icons.Default.Devices),
    SETTINGS("Settings", "Account and connection", Icons.Default.Settings),
}

private val ReceiverInk = Color(0xFFEAF0ED)
private val ReceiverMuted = Color(0xFF9BA8A3)
private val ReceiverCanvas = Color(0xFF0B1110)
private val ReceiverSurface = Color(0xFF131C1A)
private val ReceiverSurfaceRaised = Color(0xFF192521)
private val ReceiverAccent = Color(0xFF9FE6C5)
private val ReceiverAccentDeep = Color(0xFF244D3D)
private val ReceiverWarning = Color(0xFFFFC86B)

class ReceiverActivity : ComponentActivity() {
    private val identity by lazy { ReceiverIdentity(applicationContext) }
    private val authSession by lazy { GoogleAuthSession(this) }
    private var authIdentity by mutableStateOf<AuthenticatedIdentity?>(null)
    private var nameValue by mutableStateOf("")
    private var emailValue by mutableStateOf("")
    private var passwordValue by mutableStateOf("")
    private var statusTitle by mutableStateOf("Ready for setup")
    private var statusDetail by mutableStateOf("Sign in, name this screen, and connect it to receive notices.")
    private var busy by mutableStateOf(false)
    private var section by mutableStateOf(ReceiverSection.HOME)
    private var inboxRevision by mutableIntStateOf(0)
    private val receiverPreferences by lazy { getSharedPreferences("receiver_identity", Context.MODE_PRIVATE) }
    private var firestoreNoticeListener: ListenerRegistration? = null
    private var notificationPermissionRequested = false
    private val noticeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "notice_history") runOnUiThread { inboxRevision++ } }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nameValue = identity.name().orEmpty()
        setContent { NoticeFlowReceiverTheme { ReceiverShell() } }
        runCatching {
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "receiver-heartbeat",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReceiverHeartbeatWorker>(15, TimeUnit.MINUTES).build(),
            )
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "receiver-notice-sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReceiverNoticeSyncWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
        lifecycleScope.launch {
            authIdentity = runCatching { authSession.current() }.getOrNull()
            if (authIdentity != null) {
                statusTitle = if (identity.lastRegisteredAt() > 0L) "Connected and listening" else "Account connected"
                statusDetail = if (identity.lastRegisteredAt() > 0L) "This Receiver is ready for live notices." else "Name this Receiver and connect it."
                if (identity.lastRegisteredAt() > 0L) startDirectNoticeListener()
            }
        }
        openInboxForNotification(intent)
    }

    override fun onStart() { super.onStart(); receiverPreferences.registerOnSharedPreferenceChangeListener(noticeListener) }
    override fun onStop() { firestoreNoticeListener?.remove(); firestoreNoticeListener = null; receiverPreferences.unregisterOnSharedPreferenceChangeListener(noticeListener); super.onStop() }
    override fun onResume() { super.onResume(); inboxRevision++ }
    override fun onPostResume() {
        super.onPostResume()
        if (!notificationPermissionRequested && android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionRequested = true
            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openInboxForNotification(intent) }
    private fun openInboxForNotification(intent: Intent?) { if (!intent?.getStringExtra("noticeId").isNullOrBlank()) { inboxRevision++; section = ReceiverSection.INBOX } }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun ReceiverShell() {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
            ModalDrawerSheet(drawerContainerColor = ReceiverSurface, windowInsets = WindowInsets.safeDrawing) {
                DrawerHeader()
                ReceiverSection.entries.forEach { item ->
                    NavigationDrawerItem(
                        label = { Column { Text(item.label, fontWeight = FontWeight.SemiBold); Text(item.description, color = ReceiverMuted, fontSize = 12.sp) } },
                        selected = section == item,
                        onClick = { section = item; scope.launch { drawerState.close() } },
                        icon = { Icon(item.icon, null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f)); Text("Direct Firebase receiver", color = ReceiverMuted, fontSize = 12.sp, modifier = Modifier.padding(24.dp))
            }
        }) {
            Scaffold(containerColor = ReceiverCanvas, contentWindowInsets = WindowInsets.safeDrawing, topBar = { ReceiverTopBar { scope.launch { drawerState.open() } } }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedContent(targetState = section, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "receiver-page") { current ->
                        when (current) { ReceiverSection.HOME -> HomeScreen(); ReceiverSection.INBOX -> InboxScreen(); ReceiverSection.DEVICE -> DeviceScreen(); ReceiverSection.SETTINGS -> SettingsScreen() }
                    }
                }
            }
        }
    }

    @Composable private fun DrawerHeader() = Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text("NOTICEFLOW", color = ReceiverAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Text("Receiver", color = ReceiverInk, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(nameValue.ifBlank { "Name this screen" }, color = ReceiverMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp)); HorizontalDivider(color = Color(0xFF2B3833))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun ReceiverTopBar(onMenu: () -> Unit) = TopAppBar(
        title = { Column { Text(section.label, fontWeight = FontWeight.Bold); Text(section.description, color = ReceiverMuted, fontSize = 12.sp) } },
        navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Open menu") } },
        actions = { StatusPill() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ReceiverCanvas, titleContentColor = ReceiverInk, navigationIconContentColor = ReceiverInk),
    )

    @Composable private fun StatusPill() { val connected = authIdentity != null && identity.lastRegisteredAt() > 0L; Surface(shape = RoundedCornerShape(50), color = if (connected) ReceiverAccentDeep else Color(0xFF2A302E)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) ReceiverAccent else ReceiverWarning)); Spacer(Modifier.width(7.dp)); Text(if (connected) "Listening" else "Setup", color = ReceiverInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } } }

    @Composable private fun HomeScreen() { val notices = remember(inboxRevision) { identity.noticeHistory() }; LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { Intro("Good to see you.", "This screen is a quiet, dependable home for school notices.") }; item { ConnectionCard() }; item { InboxPreview(notices.firstOrNull(), notices.size) }; item { SectionCard("Receiver identity", nameValue.ifBlank { "A name is required before connection" }, Icons.Default.Devices) { Text("The Sender sees this name when choosing a delivery destination.", color = ReceiverMuted, fontSize = 14.sp, lineHeight = 21.sp); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { section = ReceiverSection.DEVICE }) { Text("Manage identity") } } }; item { SectionCard("How delivery works", "Direct Firebase listener", Icons.Default.Wifi) { Text("This app listens to its own Firestore notice stream, saves each notice locally, and posts a native Android notification.", color = ReceiverMuted, fontSize = 14.sp, lineHeight = 21.sp) } } } }

    @Composable private fun ConnectionCard() { val connected = authIdentity != null && identity.lastRegisteredAt() > 0L; Surface(shape = RoundedCornerShape(24.dp), color = if (connected) ReceiverAccent else ReceiverSurfaceRaised, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (connected) Icons.Default.CloudDone else Icons.Default.CloudOff, null, tint = if (connected) Color(0xFF12372D) else ReceiverWarning, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(if (connected) "Live connection" else "Setup needed", color = if (connected) Color(0xFF10201B) else ReceiverInk, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(if (connected) "Ready to receive notices" else statusDetail, color = if (connected) Color(0xFF35534A) else ReceiverMuted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp)) } } } }

    @Composable private fun InboxPreview(latest: NoticeRecord?, count: Int) = Surface(shape = RoundedCornerShape(20.dp), color = ReceiverSurface, modifier = Modifier.fillMaxWidth().clickable { section = ReceiverSection.INBOX }) { Column(Modifier.padding(19.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("INBOX", color = ReceiverAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f)); Text("$count saved", color = ReceiverMuted, fontSize = 12.sp) }; Spacer(Modifier.height(12.dp)); Text(latest?.title ?: "Inbox is quiet", color = ReceiverInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(latest?.body ?: "Your next delivered notice will appear here and as an Android notification.", color = ReceiverMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 7.dp)) } }

    @Composable private fun InboxScreen() { val notices = remember(inboxRevision) { identity.noticeHistory() }; LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Intro("Inbox", "Every notice received and saved on this Receiver.") }; if (notices.isEmpty()) item { EmptyState("Inbox is quiet", "The next live notice will appear here.", Icons.Default.NotificationsNone) }; items(notices, key = { it.id }) { notice -> NoticeCard(notice) }; if (notices.isNotEmpty()) item { TextButton(onClick = { identity.clearNoticeHistory(); inboxRevision++ }, modifier = Modifier.fillMaxWidth()) { Text("Clear local inbox", color = ReceiverAccent) } } } }

    @Composable private fun NoticeCard(notice: NoticeRecord) = Surface(shape = RoundedCornerShape(18.dp), color = ReceiverSurface, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.receivedAt)), color = ReceiverAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(notice.title, color = ReceiverInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)); Text(notice.body, color = ReceiverMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp)) } }

    @Composable private fun DeviceScreen() = LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { Intro("This Receiver", "Give this screen a recognizable identity before connecting.") }; item { SectionCard("Display name", "Visible to the Sender", Icons.Default.Label) { OutlinedTextField(nameValue, { nameValue = it.take(60) }, label = { Text("Receiver name") }, modifier = Modifier.fillMaxWidth(), colors = fields(), singleLine = true); Spacer(Modifier.height(11.dp)); Button(onClick = { saveDeviceName() }, modifier = Modifier.fillMaxWidth(), colors = actionColors()) { Text("Save display name", fontWeight = FontWeight.Bold) } } }; item { SectionCard("Installation identity", "Stable local details", Icons.Default.Fingerprint) { KeyValue("Receiver ID", identity.receiverId()); Spacer(Modifier.height(12.dp)); KeyValue("Last registered", registrationLabel()); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { copy(identity.receiverId()) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy Receiver ID") } } }; item { SectionCard("Connection state", "Direct Firebase", Icons.Default.Cloud) { KeyValue("Account", authIdentity?.email ?: "Not signed in"); Spacer(Modifier.height(10.dp)); KeyValue("Listener", if (identity.lastRegisteredAt() > 0L) "Active when app is open" else "Waiting for registration") } } }

    @Composable private fun SettingsScreen() = LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { Intro("Settings", "Account, connection, and app controls.") }; item { AccountCard() }; item { SectionCard("Connection", statusTitle, Icons.Default.Wifi) { Text(statusDetail, color = ReceiverMuted, fontSize = 14.sp, lineHeight = 21.sp); Spacer(Modifier.height(12.dp)); Button(onClick = { registerReceiver() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = actionColors()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Connecting…" else if (identity.lastRegisteredAt() > 0L) "Refresh connection" else "Connect Receiver", fontWeight = FontWeight.Bold) } } }; item { SectionCard("Quick notice bubble", if (Settings.canDrawOverlays(this@ReceiverActivity)) "Enabled" else "Optional overlay access", Icons.Default.Notifications) { Text("Show a small movable notice window above other apps for 15 seconds. It is optional and can be disabled in Android settings.", color = ReceiverMuted, fontSize = 14.sp, lineHeight = 21.sp); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { openOverlaySettings() }, modifier = Modifier.fillMaxWidth()) { Text(if (Settings.canDrawOverlays(this@ReceiverActivity)) "Manage overlay permission" else "Enable quick bubble") } } }; item { SectionCard("Device layout", if (Build.MANUFACTURER.equals("samsung", true)) "Samsung One UI profile" else "Standard Android profile", Icons.Default.Devices) { Text("The interface uses responsive spacing and system font scaling. Samsung Galaxy devices use the One UI profile label and the same safe controls without changing Firebase behavior.", color = ReceiverMuted, fontSize = 14.sp, lineHeight = 21.sp) } }; item { SectionCard("About NoticeFlow", "Receiver workspace", Icons.Default.Info) { Text("A focused notice destination with a local inbox and direct Firebase connection.", color = ReceiverMuted, fontSize = 14.sp, lineHeight = 21.sp) } } }

    @Composable private fun AccountCard() = SectionCard("School account", authIdentity?.email ?: "Sign in to connect this Receiver", Icons.Default.AccountCircle) { if (authIdentity == null) { OutlinedTextField(emailValue, { emailValue = it }, singleLine = true, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), colors = fields()); Spacer(Modifier.height(10.dp)); OutlinedTextField(passwordValue, { passwordValue = it }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fields()); Spacer(Modifier.height(12.dp)); Button(onClick = { signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = actionColors()) { Text(if (busy) "Signing in…" else "Sign in with email", fontWeight = FontWeight.Bold) }; Row { TextButton(onClick = { createAccount() }, enabled = !busy) { Text("Create account", color = ReceiverAccent) }; TextButton(onClick = { resetPassword() }, enabled = !busy) { Text("Reset password", color = ReceiverAccent) } }; Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Color(0xFF2B3833)); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { signInWithGoogle() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Continue with Google") } } else { Text("Signed in as ${authIdentity?.email ?: "school account"}.", color = ReceiverInk, fontSize = 14.sp); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { signOut() }, enabled = !busy) { Text("Sign out") } } }

    private fun saveDeviceName() { val value = nameValue.trim(); if (value.isBlank()) { statusTitle = "Name required"; statusDetail = "Choose a recognizable Receiver name."; return }; identity.setName(value); nameValue = value; statusTitle = "Name saved"; statusDetail = "$value will appear to the Sender after connection." }
    private fun signIn() = lifecycleScope.launch { busy = true; runCatching { authSession.signInWithEmail(emailValue.trim(), passwordValue) }.onSuccess { authIdentity = it; passwordValue = ""; statusTitle = "Account connected"; statusDetail = "Now connect this named Receiver." }.onFailure { statusTitle = "Sign-in failed"; statusDetail = it.message ?: "Firebase authentication did not complete." }; busy = false }
    private fun signInWithGoogle() = lifecycleScope.launch { busy = true; runCatching { authSession.signInWithGoogle(BuildConfig.GOOGLE_WEB_CLIENT_ID) }.onSuccess { authIdentity = it; statusTitle = "Google account connected"; statusDetail = "Now connect this named Receiver." }.onFailure { statusTitle = "Google Sign-In failed"; statusDetail = it.message ?: "Google Sign-In did not complete." }; busy = false }
    private fun createAccount() = lifecycleScope.launch { busy = true; runCatching { authSession.createEmailAccount(emailValue.trim(), passwordValue) }.onSuccess { authIdentity = it; passwordValue = ""; statusTitle = "Account created"; statusDetail = "Name the device and connect it." }.onFailure { statusTitle = "Could not create account"; statusDetail = it.message ?: "Firebase authentication did not complete." }; busy = false }
    private fun resetPassword() = lifecycleScope.launch { busy = true; runCatching { authSession.sendPasswordReset(emailValue.trim()) }.onSuccess { statusTitle = "Reset email sent"; statusDetail = "Check the inbox for $emailValue." }.onFailure { statusTitle = "Reset failed"; statusDetail = it.message ?: "Could not send the reset email." }; busy = false }
    private fun signOut() = lifecycleScope.launch { busy = true; runCatching { authSession.signOut() }; authIdentity = null; statusTitle = "Signed out"; statusDetail = "Sign in again before refreshing this Receiver connection."; busy = false }
    private fun registerReceiver() = lifecycleScope.launch { if (authIdentity == null) { section = ReceiverSection.SETTINGS; statusTitle = "Account required"; statusDetail = "Sign in before connecting this Receiver."; return@launch }; val name = nameValue.trim(); if (name.isBlank()) { section = ReceiverSection.DEVICE; statusTitle = "Name required"; statusDetail = "Give the Receiver a recognizable name first."; return@launch }; busy = true; try { identity.setName(name); nameValue = name; FirebaseBootstrap.ensureInitialized(this@ReceiverActivity); check(FirebaseApp.getApps(this@ReceiverActivity).isNotEmpty()) { "Firebase configuration is missing for app.receiver." }; withContext(Dispatchers.IO) { DirectFirebaseStore.registerReceiver(identity.receiverId(), authIdentity!!.uid, name, packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()) }; identity.recordRegistered(); runCatching { val token = FirebaseMessaging.getInstance().token.await(); DirectFirebaseStore.updateFcmToken(identity.receiverId(), token) }; startDirectNoticeListener(); statusTitle = "Receiver connected"; statusDetail = "$name is registered and listening directly to Firebase."; section = ReceiverSection.HOME } catch (error: Throwable) { statusTitle = "Connection failed"; statusDetail = error.message ?: "The connection stopped before registration completed."; section = ReceiverSection.SETTINGS } finally { busy = false } }
    private fun startDirectNoticeListener() { firestoreNoticeListener?.remove(); FirebaseBootstrap.ensureInitialized(this); firestoreNoticeListener = DirectFirebaseStore.listenForNotices(identity.receiverId(), { notice -> lifecycleScope.launch(Dispatchers.IO) { identity.recordNotice(notice.title, notice.body, notice.id); DirectNoticeNotifier.show(applicationContext, notice); withContext(Dispatchers.Main) { inboxRevision++; section = ReceiverSection.INBOX } } }, { error -> runOnUiThread { statusTitle = "Firebase listener error"; statusDetail = error.message ?: "Direct Firestore listening failed." } }) }
    private fun registrationLabel() = if (identity.lastRegisteredAt() == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(identity.lastRegisteredAt()))
    private fun copy(text: String) { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Receiver ID", text)) }
    private fun openOverlaySettings() { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } }

    @Composable private fun Intro(title: String, subtitle: String) = Column { Text(title, color = ReceiverInk, fontSize = 29.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = ReceiverMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp)) }
    @Composable private fun SectionCard(title: String, subtitle: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) = Surface(shape = RoundedCornerShape(20.dp), color = ReceiverSurface, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = ReceiverAccent, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(11.dp)); Column { Text(title, color = ReceiverInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(subtitle, color = ReceiverMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }; Spacer(Modifier.height(15.dp)); content() } }
    @Composable private fun KeyValue(label: String, value: String) { Column { Text(label.uppercase(), color = ReceiverMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(value, color = ReceiverInk, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) } }
    @Composable private fun EmptyState(title: String, detail: String, icon: ImageVector) = Surface(shape = RoundedCornerShape(20.dp), color = ReceiverSurface, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = ReceiverAccent, modifier = Modifier.size(34.dp)); Text(title, color = ReceiverInk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp)); Text(detail, color = ReceiverMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp)) } }
    @Composable private fun fields() = OutlinedTextFieldDefaults.colors(focusedBorderColor = ReceiverAccent, unfocusedBorderColor = Color(0xFF3A4942), focusedLabelColor = ReceiverAccent, unfocusedLabelColor = ReceiverMuted, focusedTextColor = ReceiverInk, unfocusedTextColor = ReceiverInk, cursorColor = ReceiverAccent)
    @Composable private fun actionColors() = ButtonDefaults.buttonColors(containerColor = ReceiverAccent, contentColor = Color(0xFF10201B))
}

@Composable private fun NoticeFlowReceiverTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(background = ReceiverCanvas, surface = ReceiverSurface, primary = ReceiverAccent, onPrimary = Color(0xFF10201B), onBackground = ReceiverInk, onSurface = ReceiverInk, onSurfaceVariant = ReceiverMuted), content = content)
