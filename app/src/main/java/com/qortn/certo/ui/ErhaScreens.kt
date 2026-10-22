package com.qortn.certo.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qortn.certo.CertoApp
import com.qortn.certo.data.Certification
import com.qortn.certo.data.Question
import com.qortn.certo.net.CertoApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ErhaLight = Color(0xFFC4B5FD)
private val ErhaMid = Color(0xFF8B5CF6)
private val ErhaDeep = Color(0xFF6D28D9)
private val ErhaFace = Color(0xFF1A1033)
private val ErhaEye = Color(0xFFEDE9FE)
private val ErhaThink = Color(0xFF67E8F9)

/** erha's face: a rounded purple bot with glowing eyes that blink and an antenna light that pulses. */
@Composable
fun ErhaAvatar(size: Dp = 44.dp, thinking: Boolean = false, animated: Boolean = true) {
    val t = rememberInfiniteTransition(label = "erha")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 3600
        1f at 0; 1f at 3400; 0.1f at 3480; 1f at 3600
    }), label = "blink")
    val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val look by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "look")
    val b = if (animated) blink else 1f
    val glow = if (animated) pulse else 0.7f
    Canvas(Modifier.size(size, size * 1.12f)) {
        val s = this.size.width
        val antenna = if (thinking) ErhaThink else ErhaLight
        // Antenna
        drawCircle(antenna.copy(alpha = 0.25f * glow), radius = s * 0.16f, center = Offset(s / 2, s * 0.08f))
        drawCircle(antenna, radius = s * 0.08f, center = Offset(s / 2, s * 0.08f))
        drawRect(ErhaLight, topLeft = Offset(s / 2 - s * 0.022f, s * 0.15f), size = Size(s * 0.045f, s * 0.12f))
        val top = s * 0.26f
        // Ears
        drawRoundRect(ErhaDeep, Offset(s * -0.01f, top + s * 0.24f), Size(s * 0.1f, s * 0.3f), CornerRadius(s * 0.05f))
        drawRoundRect(ErhaDeep, Offset(s * 0.91f, top + s * 0.24f), Size(s * 0.1f, s * 0.3f), CornerRadius(s * 0.05f))
        // Head
        drawRoundRect(
            Brush.linearGradient(listOf(ErhaLight, ErhaMid, ErhaDeep), Offset(s * 0.05f, top), Offset(s * 0.95f, top + s * 0.82f)),
            Offset(s * 0.05f, top), Size(s * 0.9f, s * 0.82f), CornerRadius(s * 0.3f),
        )
        // Face plate
        val fx = s * 0.16f; val fy = top + s * 0.16f
        drawRoundRect(ErhaFace, Offset(fx, fy), Size(s * 0.68f, s * 0.5f), CornerRadius(s * 0.2f))
        // Eyes
        val eye = if (thinking) ErhaThink else ErhaEye
        val eh = s * 0.15f * b
        val shift = if (thinking && animated) look * s * 0.05f else 0f
        listOf(0.37f, 0.63f).forEach { x ->
            val cx = s * x + shift - s * 0.055f
            val cy = fy + s * 0.2f - eh / 2
            drawRoundRect(eye.copy(alpha = 0.35f), Offset(cx - s * 0.02f, cy - s * 0.02f), Size(s * 0.15f, eh + s * 0.04f), CornerRadius(s * 0.07f))
            drawRoundRect(eye, Offset(cx, cy), Size(s * 0.11f, eh), CornerRadius(s * 0.055f))
        }
        // Mouth
        drawRoundRect(ErhaEye.copy(alpha = 0.75f), Offset(s / 2 - s * 0.07f, fy + s * 0.37f), Size(s * 0.14f, s * 0.03f), CornerRadius(s * 0.015f))
    }
}

/** The name, always lowercase, with a small AI badge. */
@Composable
fun ErhaName(size: Int = 17) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("erha", color = P.ink, fontSize = size.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(5.dp))
        Text("AI", color = Color.White, fontSize = (size * 0.55).sp, fontWeight = FontWeight.Black,
            modifier = Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED)))).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

object ErhaPrefs {
    private fun p(c: Context) = c.getSharedPreferences("certo", Context.MODE_PRIVATE)
    fun consent(@Suppress("UNUSED_PARAMETER") c: Context) = true
    fun setConsent(c: Context, v: Boolean) = p(c).edit().putBoolean("erha.consent", v).apply()
    fun enabled(c: Context) = p(c).getBoolean("erha.enabled", true)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("erha.enabled", v).apply()
}

@Composable
fun ErhaConsent(onAllow: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        ErhaAvatar(84.dp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Meet ", color = P.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold); ErhaName(26) }
        Text("Your AI study buddy. Ask for a hint when you're stuck, or ask anything about your certification.",
            color = P.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsentPoint("Powered by a third-party AI", "erha uses an AI service (currently Anthropic's Claude) to write its answers.")
                ConsentPoint("What's shared", "Your message and the question you're on are sent to that service. Your name, email and account details are not.")
                ConsentPoint("Kept for 30 days", "We keep erha conversations for 30 days to keep it safe and improve Certo, then delete them.")
                ConsentPoint("Keep it about IT", "erha only helps with Certo and IT certifications. Please don't share personal information.")
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Allow and continue", tint = ErhaDeep, onClick = onAllow)
        Text("Not now", color = P.inkSoft, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp).clickable(onClick = onCancel))
        Text("You can turn erha off any time in Settings.", color = P.inkFaint, fontSize = 12.sp)
    }
}

@Composable
private fun ConsentPoint(title: String, text: String) {
    Column {
        Text(title, color = P.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(text, color = P.inkSoft, fontSize = 13.sp)
    }
}

private data class ErhaMsg(val fromUser: Boolean, val text: String, val serverId: Int? = null, val error: Boolean = false, val rating: Int = 0)

/**
 * Full-screen erha chat. [explain] = the learner already answered [question] (erha explains it);
 * otherwise [question] is context only and the correct answer is never sent.
 */
@Composable
fun ErhaChatDialog(cert: Certification?, question: Question? = null, explain: Boolean = false, picked: Int? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CertoApp
    var consent by remember { mutableStateOf(ErhaPrefs.consent(context)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (!consent) {
            ErhaConsent(onAllow = { ErhaPrefs.setConsent(context, true); consent = true }, onCancel = onDismiss)
            return@Dialog
        }
        val messages = remember { mutableStateListOf<ErhaMsg>() }
        var input by remember { mutableStateOf("") }
        var thinking by remember { mutableStateOf(false) }
        var remaining by remember { mutableStateOf<Int?>(null) }
        val scope = rememberCoroutineScope()
        val list = rememberLazyListState()

        suspend fun send(mode: String) {
            thinking = true
            val token = app.auth.token()
            if (token == null) { messages.add(ErhaMsg(false, "Please sign in to use erha.", error = true)); thinking = false; return }
            val history = messages.filter { !it.error }.takeLast(10).map { (if (it.fromUser) "user" else "assistant") to it.text }
            runCatching { CertoApi.erha(token, mode, cert?.id, question, picked, revealAnswer = explain, messages = history) }
                .onSuccess { r -> remaining = r.remaining; messages.add(ErhaMsg(false, r.text, r.id)) }
                .onFailure { e -> messages.add(ErhaMsg(false, e.message ?: "erha couldn't answer just now.", error = true)) }
            thinking = false
        }
        LaunchedEffect(Unit) {
            if (explain && question != null) { messages.add(ErhaMsg(true, "Why is this the answer?")); send("explain") }
        }
        LaunchedEffect(messages.size, thinking) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }

        Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ErhaAvatar(40.dp, thinking = thinking)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    ErhaName()
                    Text(if (thinking) "thinking…" else "Your ${cert?.shortName ?: ""} study buddy".replace("  ", " "), color = P.inkSoft, fontSize = 12.sp)
                }
                remaining?.let { Text("$it left today", color = P.inkSoft, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(P.card).padding(horizontal = 8.dp, vertical = 4.dp)) }
                Spacer(Modifier.width(8.dp))
                CircleIcon(Icons.Filled.Close, onDismiss)
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (messages.isEmpty() && !thinking) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            ErhaAvatar(30.dp, animated = false)
                            Spacer(Modifier.width(8.dp))
                            Text("Hi! I'm erha. Ask me anything about ${cert?.shortName ?: "your IT certification"}, or tap an idea below.",
                                color = P.ink, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(P.card).padding(12.dp))
                        }
                        suggestions(cert).forEach { s ->
                            Text("✦  $s", color = P.ink, fontSize = 14.sp, modifier = Modifier.padding(start = 38.dp).clip(CircleShape)
                                .background(Brand.copy(alpha = 0.1f)).clickable {
                                    messages.add(ErhaMsg(true, s)); scope.launch { send("chat") }
                                }.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
                items(messages.size) { i ->
                    val m = messages[i]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!m.fromUser) { ErhaAvatar(30.dp, animated = false); Spacer(Modifier.width(8.dp)) }
                        Column(horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
                            Text(m.text, color = if (m.fromUser) Color.White else if (m.error) Bad else P.ink, fontSize = 15.sp,
                                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (m.fromUser) Brush.linearGradient(listOf(ErhaMid, ErhaDeep)) else Brush.linearGradient(listOf(P.card, P.card)))
                                    .padding(12.dp))
                            if (!m.fromUser && m.serverId != null) Row(Modifier.padding(top = 4.dp, start = 6.dp)) {
                                listOf(true, false).forEach { up ->
                                    val sel = m.rating == (if (up) 1 else -1)
                                    Icon(if (up) (if (sel) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp) else (if (sel) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown),
                                        if (up) "Helpful" else "Not helpful", tint = if (sel) Brand else P.inkFaint,
                                        modifier = Modifier.size(28.dp).padding(5.dp).clickable {
                                            messages[i] = m.copy(rating = if (up) 1 else -1)
                                            scope.launch { app.auth.token()?.let { CertoApi.rateErha(it, m.serverId, up) } }
                                        })
                                }
                            }
                        }
                    }
                }
                if (thinking) item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        ErhaAvatar(30.dp, thinking = true)
                        Spacer(Modifier.width(8.dp))
                        TypingDots()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input, onValueChange = { input = it.take(1000) },
                    placeholder = { Text("Ask erha about ${cert?.shortName ?: "IT certs"}…", color = P.inkFaint) },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).border(1.dp, P.stroke, RoundedCornerShape(22.dp)),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(focusedContainerColor = P.card, unfocusedContainerColor = P.card,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = P.ink, unfocusedTextColor = P.ink, cursorColor = Brand),
                )
                Spacer(Modifier.width(8.dp))
                val can = input.isNotBlank() && !thinking
                Box(Modifier.size(46.dp).clip(CircleShape).background(if (can) Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))) else Brush.linearGradient(listOf(P.inkFaint, P.inkFaint)))
                    .clickable(enabled = can) {
                        messages.add(ErhaMsg(true, input.trim())); input = ""
                        scope.launch { send("chat") }
                    }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowUpward, "Send", tint = Color.White)
                }
            }
        }
    }
}

private fun suggestions(cert: Certification?): List<String> {
    val name = cert?.shortName ?: "my exam"
    val first = when (cert?.id) {
        "netplus", "ccna" -> "Explain subnetting with a simple example"
        "secplus", "isc2cc", "sc900" -> "What's the difference between authentication and authorisation?"
        "awsccp", "saa" -> "When should I use S3, EBS or EFS?"
        "az900" -> "Explain IaaS, PaaS and SaaS with examples"
        "itil4" -> "What are the ITIL guiding principles?"
        "aplus" -> "How do I troubleshoot a PC that won't boot?"
        else -> "Explain the OSI model simply"
    }
    return listOf(first, "How should I plan my study for $name?", "Quiz me with a tricky $name question")
}

@Composable
fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(P.card).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (0..2).forEach { i ->
            val y by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(450, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.padding(top = (3 + y).dp.coerceAtLeast(0.dp)).size(7.dp).clip(CircleShape).background(P.inkSoft))
        }
    }
}

/** Hint card shown under the question. */
@Composable
fun ErhaHintCard(text: String?, loading: Boolean, onFollowUp: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.copy(alpha = 0.1f)).border(1.dp, Brand.copy(alpha = 0.3f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ErhaAvatar(36.dp, thinking = loading)
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { ErhaName(14); Text(if (loading) "  is thinking…" else "  hint", color = P.inkSoft, fontSize = 12.sp) }
            if (text != null) Text(text, color = P.ink, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            if (!loading) Text("Ask a follow-up", color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).clickable(onClick = onFollowUp))
        }
    }
}

/** "Ask erha" card on the Study tab. */
@Composable
fun ErhaPromoCard(cert: Certification?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Brand.copy(alpha = 0.16f), P.card)))
            .border(1.dp, Brand.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ErhaAvatar(50.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Ask ", color = P.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp); ErhaName(17) }
            Text("Stuck on a topic? erha explains ${cert?.shortName ?: "it"} in plain English.", color = P.inkSoft, fontSize = 13.sp)
        }
    }
}

/** Report a wrong or unclear question. */
@Composable
fun ReportQuestionDialog(cert: Certification, question: Question, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as CertoApp
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("wrong_answer") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val reasons = listOf("wrong_answer" to "The answer is wrong", "unclear" to "The question is unclear", "outdated" to "It's out of date", "typo" to "Typo or wording", "other" to "Something else")
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.bg).padding(20.dp)) {
            if (sent) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ErhaAvatar(64.dp)
                    Text("Thanks for the report!", color = P.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
                    Text("We'll check this question and fix it in an update.", color = P.inkSoft, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Done", onClick = onDismiss)
                }
                return@Column
            }
            Text("Report a problem", color = P.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(question.prompt, color = P.inkSoft, fontSize = 13.sp, maxLines = 3, modifier = Modifier.padding(vertical = 8.dp))
            reasons.forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { reason = id }.padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (reason == id) Brand else P.stroke, CircleShape).padding(4.dp)) {
                        if (reason == id) Box(Modifier.fillMaxSize().clip(CircleShape).background(Brand))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = P.ink)
                }
            }
            TextField(value = note, onValueChange = { note = it.take(600) }, placeholder = { Text("Details (optional)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = TextFieldDefaults.colors(focusedContainerColor = P.card, unfocusedContainerColor = P.card,
                    focusedTextColor = P.ink, unfocusedTextColor = P.ink))
            error?.let { Text(it, color = Bad, fontSize = 13.sp) }
            PrimaryButton("Send report", busy = busy) {
                scope.launch {
                    busy = true
                    val t = app.auth.token()
                    if (t == null) error = "Please sign in to send a report." else
                        runCatching { CertoApi.reportQuestion(t, cert.id, question, reason, note) }.onSuccess { sent = true }.onFailure { error = it.message }
                    busy = false
                }
            }
        }
    }
}

/** Live chat with the Certo team (answered from the admin console). */
@Composable
fun SupportChatDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as CertoApp
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<CertoApi.SupportMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    suspend fun load(all: Boolean) {
        val t = app.auth.token() ?: return
        runCatching { CertoApi.supportMessages(t, if (all) null else messages.lastOrNull()?.id) }
            .onSuccess { new -> if (all) { messages.clear(); messages.addAll(new) } else messages.addAll(new.filter { n -> messages.none { it.id == n.id } }); app.auth.clearSupportUnread() }
            .onFailure { if (all) error = it.message }
    }
    LaunchedEffect(Unit) { load(true); while (true) { delay(5000); load(false) } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Certo team", color = P.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("We usually reply within a few hours", color = P.inkSoft, fontSize = 12.sp)
                }
                CircleIcon(Icons.Filled.Close, onDismiss)
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Hi! Ask us anything about Certo: your account, Premium, a question you think is wrong, or ideas for the app. For study questions, erha can answer instantly.",
                        color = P.inkSoft, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.card).padding(14.dp))
                }
                items(messages, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromTeam) Arrangement.Start else Arrangement.End) {
                        Text(m.body, color = if (m.fromTeam) P.ink else Color.White,
                            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (m.fromTeam) Brush.linearGradient(listOf(P.card, P.card)) else Brush.linearGradient(listOf(ErhaMid, ErhaDeep))).padding(12.dp))
                    }
                }
                error?.let { item { Text(it, color = Bad, fontSize = 13.sp) } }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = input, onValueChange = { input = it.take(2000) }, placeholder = { Text("Message the Certo team…", color = P.inkFaint) },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).border(1.dp, P.stroke, RoundedCornerShape(22.dp)), maxLines = 5,
                    colors = TextFieldDefaults.colors(focusedContainerColor = P.card, unfocusedContainerColor = P.card,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = P.ink, unfocusedTextColor = P.ink, cursorColor = Brand))
                Spacer(Modifier.width(8.dp))
                val can = input.isNotBlank() && !sending
                Box(Modifier.size(46.dp).clip(CircleShape).background(if (can) Brand else P.inkFaint).clickable(enabled = can) {
                    val text = input.trim()
                    scope.launch {
                        sending = true
                        val t = app.auth.token()
                        if (t != null) runCatching { CertoApi.sendSupport(t, text) }.onSuccess { input = ""; load(false) }.onFailure { error = it.message }
                        sending = false
                    }
                }, contentAlignment = Alignment.Center) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.ArrowUpward, "Send", tint = Color.White)
                }
            }
        }
    }
}
