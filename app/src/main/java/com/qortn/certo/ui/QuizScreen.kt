package com.qortn.certo.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.data.QuizMode
import com.qortn.certo.data.StudyStore
import kotlinx.coroutines.delay
import com.qortn.certo.net.CertoApi
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(store: StudyStore, page: Page.Quiz, onClose: () -> Unit, onPaywall: () -> Unit) {
    val cert = page.cert
    val questions = page.questions
    val mode = page.mode
    val haptic = LocalHapticFeedback.current
    var index by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    val answers = remember { mutableStateMapOf<String, Int>() }
    var finished by remember { mutableStateOf(false) }
    val totalLimit = remember { store.timeLimit(mode, questions.size) }
    var secondsLeft by remember { mutableIntStateOf(totalLimit ?: mode.perQuestionSeconds ?: 0) }

    val q = questions.getOrNull(index)
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.qortn.certo.CertoApp
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val erhaOn = remember { ErhaPrefs.enabled(context) }
    val hints = remember { mutableStateMapOf<String, String>() }
    var hintLoading by remember { mutableStateOf(false) }
    var erhaChat by remember { mutableStateOf<Pair<com.qortn.certo.data.Question, Boolean>?>(null) }
    var reporting by remember { mutableStateOf<com.qortn.certo.data.Question?>(null) }
    var needConsent by remember { mutableStateOf(false) }
    // Hints are for practice; never in the exam simulator or timed pop quiz.
    val hintsAllowed = erhaOn && mode.instantFeedback && mode.perQuestionSeconds == null && totalLimit == null

    fun askHint() {
        val cur = q ?: return
        if (hints[cur.id] != null || hintLoading) return
        if (!ErhaPrefs.consent(context)) { needConsent = true; return }
        scope.launch {
            hintLoading = true
            val t = app.auth.token()
            hints[cur.id] = if (t == null) "Please sign in to use erha." else
                runCatching { CertoApi.erha(t, "hint", cert.id, cur).text }.getOrElse { it.message ?: "erha couldn't answer just now." }
            hintLoading = false
        }
    }

    fun next() {
        if (index + 1 >= questions.size) {
            if (!mode.instantFeedback) answers.forEach { (qid, c) -> questions.firstOrNull { it.id == qid }?.let { store.record(it, c, cert) } }
            finished = true
        } else {
            index++; chosen = null
            mode.perQuestionSeconds?.let { secondsLeft = it }
        }
    }

    fun choose(i: Int) {
        val cur = q ?: return
        if (mode.instantFeedback) {
            if (chosen != null) return
            chosen = i
            answers[cur.id] = i
            store.record(cur, i, cert)
            haptic.performHapticFeedback(if (i == cur.answer) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        } else {
            chosen = i
            answers[cur.id] = i
        }
    }

    // Celebrate any milestone once the results are on screen.
    val achContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        delay(1100)
        val correct = questions.count { answers[it.id] == it.answer }
        Achievements.evaluate(achContext, store, correct, questions.size)
    }

    // Timers: whole-exam countdown (simulator) or per-question (pop quiz).
    LaunchedEffect(index, finished) {
        if (finished) return@LaunchedEffect
        if (totalLimit == null && mode.perQuestionSeconds == null) return@LaunchedEffect
        while (secondsLeft > 0 && !finished) {
            delay(1000)
            if (mode.perQuestionSeconds != null && chosen != null) return@LaunchedEffect
            secondsLeft--
        }
        if (finished) return@LaunchedEffect
        if (totalLimit != null) {
            answers.forEach { (qid, c) -> questions.firstOrNull { it.id == qid }?.let { store.record(it, c, cert) } }
            finished = true
        } else if (chosen == null) {
            q?.let { store.record(it, -1, cert) }
            chosen = -1
        }
    }

    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(Icons.Filled.Close, onClose)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.title, color = P.ink, fontWeight = FontWeight.Bold)
                if (!finished) Text("Question ${index + 1} of ${questions.size}", color = P.inkSoft, fontSize = 12.sp)
            }
            if (!finished && (totalLimit != null || mode.perQuestionSeconds != null)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(CircleShape).background(P.card).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.Timer, null, tint = if (secondsLeft < 10) Bad else P.ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("%d:%02d".format(secondsLeft / 60, secondsLeft % 60), color = if (secondsLeft < 10) Bad else P.ink, fontWeight = FontWeight.Bold)
                }
            }
            if (!finished && q != null) {
                Spacer(Modifier.width(8.dp))
                val flagged = store.isFlagged(q, cert)
                CircleIcon(if (flagged) Icons.Filled.Flag else Icons.Filled.OutlinedFlag) { store.toggleFlag(q, cert) }
            }
        }
        ProgressBar(if (finished) 1f else index / questions.size.toFloat(), mode.tint, height = 4.dp)
        Spacer(Modifier.height(16.dp))

        if (finished || q == null) {
            Results(store, page, answers, onClose, onPaywall)
            return@Column
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(cert.domainName(q.domain).uppercase(), color = cert.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(q.prompt, color = P.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
            Spacer(Modifier.height(18.dp))
            q.choices.forEachIndexed { i, choice ->
                val reveal = mode.instantFeedback && chosen != null
                val border = when {
                    reveal && i == q.answer -> Good
                    reveal && i == chosen -> Bad
                    !reveal && i == chosen -> Brand
                    else -> P.stroke
                }
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape)
                        .background(if (reveal && i == q.answer) Good.copy(alpha = 0.12f) else if (reveal && i == chosen) Bad.copy(alpha = 0.12f) else P.card)
                        .border(if (border == P.stroke) 1.dp else 2.dp, border, shape)
                        .clickable { choose(i) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(P.cardRaised).border(1.dp, P.stroke, CircleShape), contentAlignment = Alignment.Center) {
                        Text(('A' + i).toString(), color = P.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(choice, color = P.ink, fontSize = 16.sp)
                }
            }
            val hint = hints[q.id]
            if (hint != null) ErhaHintCard(hint, false) { erhaChat = q to (chosen != null) }
            else if (hintLoading) ErhaHintCard(null, true) {}
            else if (hintsAllowed && chosen == null) {
                Row(
                    Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(Brand.copy(alpha = 0.1f))
                        .border(1.dp, Brand.copy(alpha = 0.25f), CircleShape).clickable { askHint() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ErhaAvatar(24.dp, animated = false)
                    Spacer(Modifier.width(8.dp))
                    Text("Stuck? Get a hint from ", color = P.ink, fontSize = 14.sp)
                    ErhaName(15)
                }
            }
            if (hint != null || hintLoading || (hintsAllowed && chosen == null)) Spacer(Modifier.height(12.dp))
            if (mode.instantFeedback && chosen != null) {
                val right = chosen == q.answer
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        Text(if (chosen == -1) "Time's up" else if (right) "Correct" else "Not quite", color = if (right) Good else Bad, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(q.explanation, color = P.inkSoft, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (erhaOn) Row(Modifier.clickable { erhaChat = q to true }, verticalAlignment = Alignment.CenterVertically) {
                                ErhaAvatar(20.dp, animated = false)
                                Spacer(Modifier.width(6.dp))
                                Text("Ask erha why", color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("Report", color = P.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { reporting = q })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            if (index + 1 >= questions.size) "Finish" else "Next",
            enabled = chosen != null || !mode.instantFeedback,
        ) { next() }
        Spacer(Modifier.height(12.dp))
    }

    erhaChat?.let { (question, answered) ->
        ErhaChatDialog(cert, question, explain = answered, picked = chosen?.takeIf { it >= 0 }) { erhaChat = null }
    }
    reporting?.let { ReportQuestionDialog(cert, it) { reporting = null } }
    if (needConsent) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { needConsent = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            ErhaConsent(onAllow = { ErhaPrefs.setConsent(context, true); needConsent = false; askHint() }, onCancel = { needConsent = false })
        }
    }
}

@Composable
private fun Results(store: StudyStore, page: Page.Quiz, answers: Map<String, Int>, onClose: () -> Unit, onPaywall: () -> Unit) {
    val correct = page.questions.count { answers[it.id] == it.answer }
    val total = page.questions.size
    val pct = if (total == 0) 0 else correct * 100 / total
    val passed = pct >= page.cert.passingPercent
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Ring(pct / 100f, if (passed) Good else Warn, 140.dp) {
            Text("$pct%", color = P.ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(18.dp))
        Text(if (passed) "Great work!" else "Keep going", color = P.ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("$correct of $total correct · pass mark ${page.cert.passingPercent}%", color = P.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        if (store.subscriptionsEnabled && !store.hasPremium) {
            SecondaryButton("Unlock every question with Premium", onClick = onPaywall)
            Spacer(Modifier.height(10.dp))
        }
        PrimaryButton("Done", onClick = onClose)
        Spacer(Modifier.height(12.dp))
    }
}
