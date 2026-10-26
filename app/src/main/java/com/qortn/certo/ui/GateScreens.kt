package com.qortn.certo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.BuildConfig
import com.qortn.certo.CertoApp
import com.qortn.certo.net.MemberNotice
import kotlinx.coroutines.launch

sealed class GateKind {
    data class Blocked(val status: String, val message: String?) : GateKind()
    data class Maintenance(val message: String) : GateKind()
    data object Update : GateKind()
}

/** Full-screen states set from the admin console: waiting for approval, suspended, banned,
 *  maintenance mode, or an app update that's required. */
@Composable
fun GateScreen(app: CertoApp, kind: GateKind) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val (icon, tint, title, message) = when (kind) {
        is GateKind.Blocked -> when (kind.status) {
            "pending" -> Quad(Icons.Filled.HourglassTop, Brand, "Almost there", kind.message ?: "Your account is waiting for approval.")
            "banned" -> Quad(Icons.Filled.Block, Bad, "Account closed", kind.message ?: "This account can't be used.")
            else -> Quad(Icons.Filled.PauseCircle, Warn, "Account suspended", kind.message ?: "Your account is suspended.")
        }
        is GateKind.Maintenance -> Quad(Icons.Filled.Build, Warn, "Back soon", kind.message)
        GateKind.Update -> Quad(Icons.Filled.SystemUpdate, Brand, "Update required", "A newer version of Certo is needed to keep studying. It only takes a moment.")
    }

    Column(
        Modifier.fillMaxSize().background(P.bg).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(110.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(title, color = P.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, color = P.inkSoft, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.weight(1f))
        if (kind is GateKind.Update) {
            PrimaryButton("Update Certo") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.qortn.certo")).setPackage("com.android.vending")) }
                    .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.qortn.certo"))) } }
            }
        } else {
            PrimaryButton("Check again", busy = checking) {
                scope.launch { checking = true; app.auth.refreshState(); checking = false }
            }
            Spacer(Modifier.height(14.dp))
            Text("Contact support", color = Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@qortyn.com?subject=Certo%20account"))) }
            })
        }
        if (kind is GateKind.Blocked) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Text("Sign out", color = P.inkSoft, fontSize = 14.sp, modifier = Modifier.clickable { scope.launch { app.auth.signOut() } })
                Text("Delete account", color = P.inkSoft, fontSize = 14.sp, modifier = Modifier.clickable { confirmDelete = true })
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your Certo account?") },
            text = { Text("Your account and study progress will be removed. This can't be undone.") },
            confirmButton = { TextButton({ confirmDelete = false; scope.launch { runCatching { app.auth.deleteAccount() } } }) { Text("Delete", color = Bad) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private data class Quad(val icon: ImageVector, val tint: androidx.compose.ui.graphics.Color, val title: String, val message: String)

/** A warning or message from the Certo team. The user has to acknowledge it. */
@Composable
fun NoticeDialog(notice: MemberNotice, onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(if (notice.isWarning) Icons.Filled.Warning else Icons.Filled.Chat, null, tint = if (notice.isWarning) Warn else Brand) },
        title = { Text(notice.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(if (notice.isWarning) "Warning" else "Message from Certo", color = P.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(notice.body, color = P.ink)
            }
        },
        confirmButton = { TextButton(onAcknowledge) { Text(if (notice.isWarning) "I understand" else "OK", color = if (notice.isWarning) Warn else Brand, fontWeight = FontWeight.Bold) } },
        containerColor = P.card,
    )
}

/** A slim banner for announcements set in the admin console. */
@Composable
fun AnnouncementBanner(text: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brand.copy(alpha = 0.14f)).statusBarsPadding().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Campaign, null, tint = Brand, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = P.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 3, modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Dismiss", tint = P.inkSoft, modifier = Modifier.size(18.dp))
        }
    }
}

object AppVersion {
    /** True when the installed version is lower than [minimum] (for example 1.3 < 1.3.1). */
    fun isOlder(minimum: String?): Boolean {
        if (minimum.isNullOrBlank()) return false
        val a = BuildConfig.VERSION_NAME.split(".").map { it.toIntOrNull() ?: 0 }
        val b = minimum.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }
}
