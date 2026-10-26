package com.qortn.certo.ui

import com.qortn.certo.findActivity

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.CertoApp
import com.qortn.certo.data.Appearance
import com.qortn.certo.data.StudyStore
import com.qortn.certo.net.CertoApi
import kotlinx.coroutines.launch

/** Instagram-style section: small grey heading, flat rows, a hairline between sections. */
@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(0.5.dp).background(P.stroke))
    if (title != null) Text(title, color = P.inkSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 2.dp))
    Column(Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun RowItem(icon: ImageVector, label: String, tint: Color = P.ink, value: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 16.sp, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = P.inkSoft, fontSize = 15.sp); Spacer(Modifier.width(4.dp)) }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = P.inkFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsTab(app: CertoApp, nav: Nav) {
    val store = app.store
    val auth = app.auth
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var erhaOn by remember { mutableStateOf(ErhaPrefs.enabled(context)) }
    var erhaChat by remember { mutableStateOf(false) }
    var liveChat by remember { mutableStateOf(false) }
    val nudges = remember { mutableStateMapOf<String, Boolean>().apply { listOf("streak", "facts", "comeback").forEach { put(it, com.qortn.certo.Nudges.enabled(context, it)) } } }
    val user = auth.user ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Settings", color = P.ink, fontSize = 32.sp, fontWeight = FontWeight.Black)

        Column {
            Row(Modifier.fillMaxWidth().clickable { nav.push(Page.Account) }.padding(vertical = 14.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Text(user.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, color = P.ink, fontWeight = FontWeight.Bold)
                    Text(user.email, color = P.inkSoft, fontSize = 13.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = P.inkFaint)
            }
        }

        if (store.subscriptionsEnabled) {
            Group("Your account") {
                if (store.hasPremium) RowItem(Icons.Filled.WorkspacePremium, "Premium active", value = "Google Play") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=com.qortn.certo")))
                }
                else RowItem(Icons.Filled.WorkspacePremium, "Upgrade to Premium") { nav.push(Page.Paywall) }
                RowItem(Icons.Filled.Restore, "Restore purchases") { app.billing.restore() }
            }
        }

        Group("Certification paths") {
            store.enrolledCerts.forEach { c ->
                RowItem(certIcon(c.symbol), c.name, trailing = {
                    if (store.currentCert?.id == c.id) Icon(Icons.Filled.Check, null, tint = Brand)
                }) { store.select(c) }
            }
            RowItem(Icons.Filled.GridView, "Manage Paths") { nav.push(Page.ManagePaths) }
        }

        Group("How you study") {
            RowItem(Icons.Filled.Flag, "Daily goal", value = "${store.data.dailyGoal} questions") {
                val next = listOf(5, 10, 20, 30, 50).let { l -> l[(l.indexOf(store.data.dailyGoal) + 1) % l.size] }
                store.update { it.copy(dailyGoal = next) }
            }
            RowItem(Icons.Filled.Notifications, "Notifications", trailing = {
                Switch(store.data.remindersOn || nudges.values.any { it }, { on ->
                    store.update { it.copy(remindersOn = on) }
                    listOf("streak", "facts", "comeback").forEach { k -> nudges[k] = on; com.qortn.certo.Nudges.setEnabled(context, k, on) }
                    if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                        (context as? android.app.Activity)?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
                    }
                    app.applyReminder()
                }, colors = SwitchDefaults.colors(checkedTrackColor = Brand))
            })
            store.currentCert?.let { c ->
                RowItem(Icons.Filled.RestartAlt, "Reset ${c.shortName} progress", tint = Bad) { confirmReset = true }
            }
        }

        Group("AI study buddy") {
            RowItem(Icons.Filled.SmartToy, "erha hints & answers", trailing = {
                Switch(erhaOn, { on -> erhaOn = on; ErhaPrefs.setEnabled(context, on) }, colors = SwitchDefaults.colors(checkedTrackColor = Brand))
            })
            if (erhaOn) RowItem(Icons.Filled.AutoAwesome, "Chat with erha") { erhaChat = true }
        }

        Group("Appearance") {
            Row(Modifier.fillMaxWidth().padding(8.dp).clip(CircleShape).background(P.cardRaised).padding(3.dp)) {
                Appearance.entries.forEach { a ->
                    val on = store.data.appearance == a
                    Box(
                        Modifier.weight(1f).clip(CircleShape).background(if (on) P.card else Color.Transparent).clickable { store.update { it.copy(appearance = a) } }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(a.label, color = if (on) P.ink else P.inkSoft, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }
        }

        if (auth.isAdmin) {
            Group("Admin") { RowItem(Icons.Filled.AdminPanelSettings, "Admin") { nav.push(Page.Admin) } }
        }

        Group("Support and about") {
            RowItem(Icons.Filled.PrivacyTip, "Privacy Policy") { nav.push(Page.Legal(false)) }
            RowItem(Icons.Filled.Description, "Terms of Use") { nav.push(Page.Legal(true)) }
            if (com.qortn.certo.Ads.privacyOptionsNeeded && !app.store.isPremium) {
                val adContext = androidx.compose.ui.platform.LocalContext.current
                RowItem(Icons.Filled.PrivacyTip, "Ad privacy") { adContext.findActivity()?.let { com.qortn.certo.Ads.showPrivacyOptions(it) } }
            }
            RowItem(Icons.Filled.Forum, "Live chat with the Certo team", trailing = {
                if (auth.state.supportUnread > 0) Text("${auth.state.supportUnread}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Bad).padding(horizontal = 7.dp, vertical = 2.dp))
            }) { liveChat = true }
            RowItem(Icons.Filled.Email, "Contact support") {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@qortyn.com?subject=Certo%20support"))) }
            }
            RowItem(Icons.Filled.Star, "Rate Certo") {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.qortn.certo&showAllReviews=true")).setPackage("com.android.vending")
                runCatching { context.startActivity(market) }.onFailure {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.qortn.certo"))) }
                }
            }
        }

        Group("About") {
            RowItem(Icons.Filled.Info, "Version", value = com.qortn.certo.BuildConfig.VERSION_NAME.split(".").take(2).joinToString("."))
            RowItem(Icons.Filled.AutoAwesome, "Made by", value = "Qortn")
        }
        Group("Login") {
            Text("Log out", color = Brand, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { scope.launch { auth.signOut() } }.padding(horizontal = 2.dp, vertical = 14.dp))
            Text(if (deleting) "Deleting…" else "Delete account", color = Bad, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(horizontal = 2.dp, vertical = 14.dp))
        }
        Spacer(Modifier.height(30.dp))
    }

    if (erhaChat) ErhaChatDialog(store.currentCert) { erhaChat = false }
    if (liveChat) SupportChatDialog { liveChat = false }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("This permanently removes your account and study progress. A Google Play subscription must be cancelled separately in Google Play.") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    deleting = true
                    scope.launch {
                        val id = user.id
                        runCatching { auth.deleteAccount() }
                            .onSuccess { store.clearLocal(id) }
                            .onFailure { error = friendly(it) }
                        deleting = false
                    }
                }) { Text("Delete account", color = Bad) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("This clears your answers and flags for ${store.currentCert?.name}.") },
            confirmButton = { TextButton({ store.currentCert?.let { store.reset(it) }; confirmReset = false }) { Text("Reset", color = Bad) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } },
        )
    }
    error?.let {
        AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("OK") } }, text = { Text(it) })
    }
}

@Composable
private fun PageScaffold(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = P.ink, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            CircleIcon(Icons.Filled.Close, onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
    }
}

@Composable
fun AccountScreen(app: CertoApp, onClose: () -> Unit) {
    val user = app.auth.user ?: return
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf(user.firstName ?: "") }
    var last by remember { mutableStateOf(user.lastName ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    PageScaffold("Account", onClose) {
        Spacer(Modifier.height(10.dp))
        Field(first, { first = it }, "First name", Icons.Filled.Person)
        Spacer(Modifier.height(10.dp))
        Field(last, { last = it }, "Last name", null)
        Spacer(Modifier.height(16.dp))
        Group {
            RowItem(Icons.Filled.Email, "Email", value = user.email)
            RowItem(Icons.Filled.Login, "Signed in with", value = user.provider.replaceFirstChar { it.uppercase() })
        }
        Spacer(Modifier.height(16.dp))
        error?.let { ErrorBanner(it); Spacer(Modifier.height(10.dp)) }
        PrimaryButton("Save", enabled = first.isNotBlank(), busy = busy) {
            busy = true
            scope.launch {
                runCatching { app.auth.updateName(first.trim(), last.trim().ifBlank { null }) }
                    .onSuccess { onClose() }.onFailure { error = friendly(it) }
                busy = false
            }
        }
    }
}

@Composable
fun ManagePathsScreen(store: StudyStore, onClose: () -> Unit) {
    PageScaffold("Manage Paths", onClose) {
        Text(if (store.hasPremium) "Add or remove the paths you're studying." else "Free includes one path. Premium unlocks them all.", color = P.inkSoft, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            store.certs.forEach { c ->
                CertRow(c, selected = store.data.enrolled.contains(c.id), locked = !store.canAccess(c)) { store.toggleEnroll(c) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SwitchPathScreen(store: StudyStore, onClose: () -> Unit) {
    PageScaffold("Switch Path", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            store.certs.forEach { c ->
                CertRow(c, selected = store.currentCert?.id == c.id, locked = !store.canAccess(c)) {
                    if (store.canAccess(c)) { store.select(c); onClose() } else { onClose(); store.showPaywall = true }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AdminScreen(app: CertoApp, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    PageScaffold("Admin", onClose) {
        Group("Subscriptions") {
            RowItem(Icons.Filled.WorkspacePremium, "Subscriptions enabled", trailing = {
                Switch(app.store.subscriptionsEnabled, { on ->
                    scope.launch {
                        val t = app.auth.token() ?: return@launch
                        runCatching {
                            CertoApi.setSubscriptions(t, on)
                            app.store.applySubscriptionsEnabled(on)
                        }.onFailure { error = it.message }
                    }
                }, colors = SwitchDefaults.colors(checkedTrackColor = Brand))
            })
        }
        Text("When off, everything is free and Premium is hidden for everyone.", color = P.inkSoft, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        error?.let { ErrorBanner(it) }
    }
}

private val privacy = listOf(
    "Who we are" to "Certo is made by Qortn (Qortyn), an independent software studio in the United Kingdom. This policy explains what Certo collects and why. Questions: support@qortyn.com.",
    "Your account" to "When you create an account we store your name, email address and, if you use Google or Apple sign-in, the identifier those services give us. Sign-in is provided by Clerk, which processes this data for us.",
    "Your study progress" to "Your answers, flagged questions, streaks, exam dates and settings are saved to your account so you can pick up on another device. They're stored in our Supabase database in London (EU/UK).",
    "Subscriptions" to "Certo Premium is bought through Google Play on Android (and Apple on iPhone). Google handles payment; we never see your card details. We only receive confirmation that your subscription is active.",
    "erha, the study helper" to "When you ask erha for a hint or send it a question, the practice question and your message are processed by our AI service provider to write the reply. It doesn't receive your name, email or account details, and it doesn't use your messages to train its models. We keep erha conversations for up to 30 days to prevent misuse, then delete them.",
    "Support chat" to "Messages you send in live chat are stored with your account so our team can reply. Delete account removes them.",
    "Crash reports" to "If the app crashes or freezes, a diagnostic report (device model, Android version, and what the app was doing) is sent to our crash-reporting provider, Sentry, so we can fix it.",
    "Notifications" to "Study reminders are scheduled on your device. They're optional and you can turn them off in Settings at any time.",
    "Ads" to "Free accounts may see ads from Google AdMob, which can collect device and ad-interaction data to show and measure ads. Certo Premium has no ads.",
    "What we don't do" to "No selling or renting your data, and no cross-app tracking.",
    "Keeping and deleting data" to "We keep your data while your account is open. Delete account in Settings removes your account and your study progress from our systems. Copies in backups are removed within 30 days. You can also ask us to delete your account at support@qortyn.com.",
    "Your rights" to "Under UK GDPR you can ask to see, correct, export or delete your data, and you can complain to the Information Commissioner's Office (ico.org.uk). Email support@qortyn.com and we'll respond within one month.",
    "Children" to "Certo isn't directed at children under 13 and we don't knowingly collect their data.",
)

private val terms = listOf(
    "Using Certo" to "Qortn gives you a personal, non-transferable licence to use Certo on devices you own or control, under these terms and the Google Play Terms of Service.",
    "Your account" to "Keep your sign-in details safe. You're responsible for activity on your account. You can delete it at any time in Settings.",
    "Certo Premium" to "Certo Premium is an auto-renewing subscription: a monthly or yearly plan at the price shown in Google Play for your country. Payment is charged to your Google account when you confirm. It renews automatically until you cancel. Manage or cancel in Google Play › Payments & subscriptions.",
    "Study content" to "Certo is an independent study tool. It isn't affiliated with or endorsed by CompTIA, Cisco, Microsoft, Amazon Web Services, PeopleCert/AXELOS, ISC2 or any certification body, and all trademarks belong to their owners. Questions are written by us for practice; we can't guarantee you'll pass an exam.",
    "Fair use" to "Don't copy, resell or redistribute Certo's question banks, try to break or reverse-engineer the app, or use it to harm others.",
    "Liability" to "Certo is provided as is. To the extent the law allows, Qortn isn't liable for indirect losses, such as exam fees or lost opportunities. Nothing here limits rights you have under UK consumer law.",
    "Law" to "These terms are governed by the laws of England and Wales.",
    "Contact" to "support@qortyn.com",
)

@Composable
fun LegalScreen(isTerms: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    PageScaffold(if (isTerms) "Terms of Use" else "Privacy Policy", onClose) {
        Text("Updated 20 October 2026", color = P.inkSoft, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        (if (isTerms) terms else privacy).forEach { (h, b) ->
            Text(h, color = P.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(b, color = P.inkSoft, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
        }
        Text("View on qortyn.com", color = Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://qortyn.com/certo/${if (isTerms) "terms" else "privacy"}"))) }
        })
        Spacer(Modifier.height(30.dp))
    }
}
