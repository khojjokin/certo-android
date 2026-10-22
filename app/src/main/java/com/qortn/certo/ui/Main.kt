package com.qortn.certo.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.CertoApp
import com.qortn.certo.data.Certification
import com.qortn.certo.data.QuizMode
import com.qortn.certo.data.StudyStore
import java.util.Calendar

fun certIcon(symbol: String): ImageVector = when {
    symbol.contains("network") -> Icons.Filled.Hub
    symbol.contains("lock") || symbol.contains("shield") -> Icons.Filled.Shield
    symbol.contains("cloud") -> Icons.Filled.Cloud
    symbol.contains("cpu") || symbol.contains("desktop") || symbol.contains("pc") -> Icons.Filled.Computer
    symbol.contains("arrow") || symbol.contains("cycle") -> Icons.Filled.Autorenew
    symbol.contains("server") -> Icons.Filled.Dns
    symbol.contains("building") -> Icons.Filled.AccountBalance
    symbol.contains("key") -> Icons.Filled.VpnKey
    symbol.contains("antenna") || symbol.contains("wave") -> Icons.Filled.Router
    else -> Icons.Filled.School
}

fun modeIcon(m: QuizMode): ImageVector = when (m) {
    QuizMode.QuestionOfDay -> Icons.Filled.CalendarToday
    QuizMode.Pop -> Icons.Filled.Bolt
    QuizMode.Quick10 -> Icons.Filled.Filter9Plus
    QuizMode.Timed -> Icons.Filled.Timer
    is QuizMode.LevelUp -> Icons.Filled.BarChart
    QuizMode.Missed -> Icons.Filled.Cancel
    QuizMode.Flagged -> Icons.Filled.Flag
    QuizMode.Weakest -> Icons.Filled.GpsFixed
}

// MARK: Onboarding

@Composable
fun OnboardingScreen(store: StudyStore) {
    var chosen by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Choose your\nfirst path", color = P.ink, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (store.subscriptionsEnabled) "Start free with one path. Certo Premium unlocks them all." else "Pick a path to start with. You can add more later.",
            color = P.inkSoft, fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            store.certs.forEach { cert ->
                CertRow(cert, selected = chosen == cert.id) { chosen = cert.id }
            }
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton("Start studying", enabled = chosen != null) {
            val cert = store.certs.first { it.id == chosen }
            store.update { it.copy(onboarded = true, enrolled = listOf(cert.id), selectedCertID = cert.id) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CertRow(cert: Certification, selected: Boolean, locked: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(P.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand else P.stroke, shape)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(certIcon(cert.symbol), cert.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cert.name, color = P.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${cert.examCode} · ${cert.questions.size} questions", color = P.inkSoft, fontSize = 13.sp)
            Text(cert.summary, color = P.inkFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            when { locked -> Icons.Filled.Lock; selected -> Icons.Filled.CheckCircle; else -> Icons.Filled.RadioButtonUnchecked },
            null, tint = if (selected) Brand else P.inkFaint,
        )
    }
}

// MARK: Tabs

private data class TabItem(val label: String, val icon: ImageVector)
private val tabs = listOf(
    TabItem("Study", Icons.Filled.Checklist),
    TabItem("Learn", Icons.Filled.MenuBook),
    TabItem("Stats", Icons.Filled.Insights),
    TabItem("Review", Icons.Filled.Inventory2),
    TabItem("Settings", Icons.Filled.Settings),
)

@Composable
fun MainTabs(app: CertoApp, nav: Nav) {
    val store = app.store
    Column(Modifier.fillMaxSize().background(P.bg)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            when (store.tab) {
                0 -> StudyTab(store, nav)
                1 -> LearnTab(store, nav)
                2 -> StatsTab(store)
                3 -> ReviewTab(store, nav)
                else -> SettingsTab(app, nav)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(P.bg).border(1.dp, P.stroke).navigationBarsPadding().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { i, t ->
                val on = store.tab == i
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { store.tab = i }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, tint = if (on) Brand else P.inkFaint, modifier = Modifier.size(24.dp))
                    Text(t.label, color = if (on) Brand else P.inkFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

fun startQuiz(store: StudyStore, nav: Nav, mode: QuizMode, cert: Certification) {
    if (store.isLocked(mode)) { nav.push(Page.Paywall); return }
    val qs = store.questionsFor(mode, cert)
    if (qs.isEmpty()) return
    nav.push(Page.Quiz(mode, cert, qs))
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when { h < 12 -> "Good Morning"; h < 18 -> "Good Afternoon"; else -> "Good Evening" }
}

@Composable
fun StudyTab(store: StudyStore, nav: Nav) {
    val cert = store.currentCert ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape).background(P.card).border(1.dp, P.stroke, CircleShape)
                    .clickable { nav.push(Page.SwitchPath) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(certIcon(cert.symbol), null, tint = cert.color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(cert.name, color = P.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Icon(Icons.Filled.ExpandMore, null, tint = P.inkSoft, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = Warn, modifier = Modifier.size(18.dp))
                Text("${store.streak}", color = P.ink, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(greeting(), color = P.ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text(
            if (store.todayCount > 0) "${store.todayCount} of ${store.data.dailyGoal} questions done today." else "Ready for today's study session?",
            color = P.inkSoft, fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))

        val readiness = store.readiness(cert)
        val (levelText, levelColor) = readinessLevel(readiness)
        Box(Modifier.fillMaxWidth()) {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(readiness.toFloat(), cert.color, 76.dp) {
                            Text("${(readiness * 100).toInt()}%", color = P.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Exam readiness", color = P.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    levelText, color = levelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(CircleShape).background(levelColor.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                            Text("${store.answeredCount(cert)} of ${cert.questions.size} questions answered", color = P.inkSoft, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            ProgressBar(store.todayCount / store.data.dailyGoal.coerceAtLeast(1).toFloat(), Good, height = 6.dp)
                            Text("Daily goal: ${minOf(store.todayCount, store.data.dailyGoal)}/${store.data.dailyGoal}", color = P.inkFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.ink.copy(alpha = 0.05f)).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeroStat("${(store.accuracy(cert) * 100).toInt()}%", "Accuracy", Icons.Filled.GpsFixed, Modifier.weight(1f))
                        HeroStat("${store.todayCount}", "Today", Icons.Filled.Bolt, Modifier.weight(1f))
                        HeroStat("${store.streak}", "Day streak", Icons.Filled.LocalFireDepartment, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Continue Studying") { startQuiz(store, nav, QuizMode.Quick10, cert) }
                }
            }
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(cert.color.copy(alpha = 0.16f), Color.Transparent), end = Offset(700f, 500f)))
            )
        }

        if (store.subscriptionsEnabled && !store.hasPremium) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), onClick = { nav.push(Page.Paywall) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Unlock Certo Premium", color = P.ink, fontWeight = FontWeight.Bold)
                        Text("${store.freeQuestionsUsed(cert)} of ${StudyStore.FREE_QUESTION_LIMIT} free questions", color = P.inkSoft, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = P.inkFaint)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Quiz Modes")
        val qodDone = store.questionOfDayDone(cert)
        val locked = { m: QuizMode -> store.isLocked(m) }
        Card(Modifier.fillMaxWidth(), padding = 14.dp, onClick = { startQuiz(store, nav, QuizMode.QuestionOfDay, cert) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(modeIcon(QuizMode.QuestionOfDay), QuizMode.QuestionOfDay.tint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(QuizMode.QuestionOfDay.title, color = P.ink, fontWeight = FontWeight.Bold)
                    Text(if (qodDone) "Done for today ✓" else QuizMode.QuestionOfDay.subtitle, color = P.inkSoft, fontSize = 13.sp)
                }
                if (qodDone) Icon(Icons.Filled.CheckCircle, null, tint = Good)
                else Box(Modifier.size(9.dp).clip(CircleShape).background(Warn))
            }
        }
        Spacer(Modifier.height(10.dp))
        val p = store.progress(cert)
        val missed = p.answers.values.count { !it.correct }
        val grid = listOf(QuizMode.Pop, QuizMode.Quick10, QuizMode.Timed, QuizMode.Weakest, QuizMode.Missed, QuizMode.Flagged)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grid.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { m ->
                        val badge = when (m) {
                            QuizMode.Missed -> missed.takeIf { it > 0 }?.toString()
                            QuizMode.Flagged -> p.flagged.size.takeIf { it > 0 }?.toString()
                            else -> null
                        }
                        Card(Modifier.weight(1f).height(128.dp), padding = 14.dp, onClick = { startQuiz(store, nav, m, cert) }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(modeIcon(m), m.tint, size = 40.dp)
                                    Spacer(Modifier.weight(1f))
                                    if (locked(m)) Icon(Icons.Filled.Lock, null, tint = P.inkFaint, modifier = Modifier.size(16.dp))
                                    else if (badge != null) Text(
                                        badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(CircleShape).background(m.tint).padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(m.title, color = P.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                                Text(m.subtitle, color = P.inkSoft, fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroStat(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = P.inkSoft, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, color = P.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(label, color = P.inkSoft, fontSize = 11.sp)
    }
}

fun readinessLevel(r: Double): Pair<String, Color> = when {
    r < 0.25 -> "Getting started" to Color(0xFF9A9AA5)
    r < 0.55 -> "Building up" to Warn
    r < 0.75 -> "On track" to Brand
    else -> "Exam ready" to Good
}

@Composable
fun Ring(value: Float, color: Color, size: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val track = P.stroke
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 8.dp.toPx()
            val s = Size(this.size.width - w, this.size.height - w)
            val o = Offset(w / 2, w / 2)
            drawArc(track, -90f, 360f, false, o, s, style = Stroke(w))
            drawArc(color, -90f, 360f * value.coerceIn(0f, 1f), false, o, s, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

// MARK: Learn

@Composable
fun LearnTab(store: StudyStore, nav: Nav) {
    val cert = store.currentCert ?: return
    var open by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Learn", color = P.ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text("${cert.name} · ${cert.examCode}", color = P.inkSoft)
        Spacer(Modifier.height(8.dp))
        Text(cert.summary, color = P.inkSoft, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        val scores = store.domainScores(cert).associateBy { it.id }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cert.domains.forEach { d ->
                val s = scores[d.id]
                Card(Modifier.fillMaxWidth(), onClick = { open = if (open == d.id) null else d.id }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, color = P.ink, fontWeight = FontWeight.Bold)
                                Text("${s?.answered ?: 0}/${s?.total ?: 0} answered · ${((s?.accuracy ?: 0.0) * 100).toInt()}% correct", color = P.inkSoft, fontSize = 12.sp)
                            }
                            Icon(if (open == d.id) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = P.inkFaint)
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(((s?.answered ?: 0).toFloat() / (s?.total ?: 1).coerceAtLeast(1)), cert.color, height = 6.dp)
                        if (open == d.id) {
                            Spacer(Modifier.height(10.dp))
                            d.summary?.let { Text(it, color = P.inkSoft, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                            d.topics.forEach { t ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text("•", color = cert.color, modifier = Modifier.width(14.dp))
                                    Text(t, color = P.ink, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton("Level Up this domain", tint = cert.color) { startQuiz(store, nav, QuizMode.LevelUp(d.id), cert) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// MARK: Stats

@Composable
fun StatsTab(store: StudyStore) {
    val cert = store.currentCert ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Stats", color = P.ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text(cert.name, color = P.inkSoft)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Readiness", "${(store.readiness(cert) * 100).toInt()}%", cert.color, Modifier.weight(1f))
            StatTile("Accuracy", "${(store.accuracy(cert) * 100).toInt()}%", Good, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Answered", "${store.answeredCount(cert)}", Brand, Modifier.weight(1f))
            StatTile("Day streak", "${store.streak}", Warn, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("Last 7 days")
        Card(Modifier.fillMaxWidth()) {
            val cal = Calendar.getInstance()
            val days = (6 downTo 0).map { back ->
                val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
                c.timeInMillis
            }
            val max = days.maxOf { store.count(it) }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                days.forEach { d ->
                    val n = store.count(d)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", color = P.inkSoft, fontSize = 10.sp)
                        Box(Modifier.width(20.dp).height((80 * n / max).coerceAtLeast(3).dp).clip(RoundedCornerShape(6.dp)).background(if (n > 0) Brand else P.stroke))
                        val c = Calendar.getInstance().apply { timeInMillis = d }
                        Text(listOf("S", "M", "T", "W", "T", "F", "S")[c.get(Calendar.DAY_OF_WEEK) - 1], color = P.inkFaint, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("Domains")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            store.domainScores(cert).forEach { s ->
                Card(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Column {
                        Row {
                            Text(s.name, color = P.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("${(s.accuracy * 100).toInt()}%", color = P.ink, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(s.accuracy.toFloat(), if (s.accuracy >= cert.passingPercent / 100.0) Good else if (s.answered == 0) P.stroke else Warn, height = 6.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column {
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(label, color = P.inkSoft, fontSize = 13.sp)
        }
    }
}

// MARK: Review

@Composable
fun ReviewTab(store: StudyStore, nav: Nav) {
    val cert = store.currentCert ?: return
    var filter by remember { mutableStateOf(0) }
    val p = store.progress(cert)
    val answered = cert.questions.filter { p.answers.containsKey(it.id) || p.flagged.contains(it.id) }
    val list = when (filter) {
        1 -> answered.filter { p.answers[it.id]?.correct == false }
        2 -> answered.filter { p.answers[it.id]?.correct == true }
        3 -> answered.filter { p.flagged.contains(it.id) }
        else -> answered
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(cert.name, color = cert.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Review", color = P.ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(P.card).padding(3.dp)) {
            listOf("All", "Missed", "Correct", "Flagged").forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(if (filter == i) P.cardRaised else Color.Transparent)
                        .clickable { filter = i }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (filter == i) P.ink else P.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Inbox, null, tint = P.inkFaint, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text("Nothing here yet", color = P.ink, fontWeight = FontWeight.Bold)
                Text("Answer some questions and they'll show up here for review.", color = P.inkSoft, fontSize = 13.sp)
            }
        } else {
            if (filter == 1 && list.isNotEmpty()) {
                PrimaryButton("Retry missed questions") { startQuiz(store, nav, QuizMode.Missed, cert) }
                Spacer(Modifier.height(10.dp))
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { q ->
                    val rec = p.answers[q.id]
                    Card(Modifier.fillMaxWidth(), padding = 14.dp, onClick = { expanded = if (expanded == q.id) null else q.id }) {
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    when { rec == null -> Icons.Filled.Flag; rec.correct -> Icons.Filled.CheckCircle; else -> Icons.Filled.Cancel },
                                    null, tint = when { rec == null -> Warn; rec.correct -> Good; else -> Bad }, modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(q.prompt, color = P.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Icon(
                                    if (p.flagged.contains(q.id)) Icons.Filled.Flag else Icons.Filled.OutlinedFlag, null,
                                    tint = if (p.flagged.contains(q.id)) Warn else P.inkFaint,
                                    modifier = Modifier.size(20.dp).clickable { store.toggleFlag(q, cert) },
                                )
                            }
                            if (expanded == q.id) {
                                Spacer(Modifier.height(8.dp))
                                Text("Answer: ${q.choices.getOrNull(q.answer) ?: ""}", color = Good, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(q.explanation, color = P.inkSoft, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
