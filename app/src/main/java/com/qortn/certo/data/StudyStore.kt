package com.qortn.certo.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min

/** All study state: paths, answers, streaks. Saved per account and synced to the cloud. */
class StudyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("certo", Context.MODE_PRIVATE)
    val certs: List<Certification> = CertLibrary.all(context)

    var data by mutableStateOf(AppData())
        private set
    var isPremium by mutableStateOf(false)
    var subscriptionsEnabled by mutableStateOf(prefs.getBoolean("subsEnabled", true))
        private set
    var showPaywall by mutableStateOf(false)
    var tab by mutableIntStateOf(0)

    /** Called with the encoded progress after every change (cloud sync). */
    var onSave: ((JSONObject) -> Unit)? = null

    private var storageKey = "appdata.none"

    fun switchProfile(userId: String?) {
        storageKey = if (userId == null) "appdata.none" else "appdata.user.$userId"
        data = load(storageKey)
        tab = 0
    }

    private fun load(key: String): AppData = prefs.getString(key, null)?.let {
        runCatching { AppData.fromJson(JSONObject(it)) }.getOrNull()
    } ?: AppData()

    fun update(transform: (AppData) -> AppData) {
        data = transform(data)
        val json = data.toJson()
        runCatching { prefs.edit().putString(storageKey, json.toString()).apply() }
        onSave?.invoke(json)
    }

    fun applyRemote(remote: JSONObject) {
        val appearance = data.appearance
        val decoded = runCatching { AppData.fromJson(remote) }.getOrNull() ?: return
        data = decoded.copy(appearance = appearance)
        runCatching { prefs.edit().putString(storageKey, data.toJson().toString()).apply() }
    }

    fun encoded(): JSONObject = data.toJson()

    fun clearLocal(userId: String) {
        runCatching { prefs.edit().remove("appdata.user.$userId").apply() }
    }

    fun applySubscriptionsEnabled(on: Boolean) {
        subscriptionsEnabled = on
        runCatching { prefs.edit().putBoolean("subsEnabled", on).apply() }
    }

    /** Premium is effectively on when the admin has switched subscriptions off. */
    val hasPremium: Boolean get() = isPremium || !subscriptionsEnabled

    // MARK: Paths

    val enrolledCerts: List<Certification> get() = certs.filter { data.enrolled.contains(it.id) }
    val freeCertID: String? get() = data.enrolled.firstOrNull() ?: certs.firstOrNull()?.id

    fun canAccess(cert: Certification) = hasPremium || cert.id == freeCertID

    val currentCert: Certification?
        get() {
            data.selectedCertID?.let { id -> certs.firstOrNull { it.id == id }?.let { if (canAccess(it)) return it } }
            freeCertID?.let { id -> certs.firstOrNull { it.id == id }?.let { return it } }
            return certs.firstOrNull()
        }

    fun select(cert: Certification) {
        if (!canAccess(cert) && data.enrolled.isNotEmpty()) { showPaywall = true; return }
        update { d ->
            d.copy(
                enrolled = if (d.enrolled.contains(cert.id)) d.enrolled else d.enrolled + cert.id,
                selectedCertID = cert.id,
            )
        }
    }

    fun toggleEnroll(cert: Certification) {
        if (!hasPremium && !data.enrolled.contains(cert.id) && data.enrolled.isNotEmpty()) { showPaywall = true; return }
        update { d ->
            if (d.enrolled.contains(cert.id)) {
                if (d.enrolled.size <= 1) d
                else {
                    val list = d.enrolled - cert.id
                    d.copy(enrolled = list, selectedCertID = if (d.selectedCertID == cert.id) list.firstOrNull() else d.selectedCertID)
                }
            } else d.copy(enrolled = d.enrolled + cert.id)
        }
    }

    fun availableQuestions(cert: Certification): List<Question> =
        if (hasPremium) cert.questions else cert.questions.take(FREE_QUESTION_LIMIT)

    fun freeQuestionsUsed(cert: Certification): Int {
        val ids = cert.questions.take(FREE_QUESTION_LIMIT).map { it.id }.toSet()
        return progress(cert).answers.keys.count { it in ids }
    }

    fun isLocked(mode: QuizMode): Boolean = !hasPremium && (mode == QuizMode.Timed || mode == QuizMode.Weakest)

    fun progress(cert: Certification): CertProgress = data.progress[cert.id] ?: CertProgress()

    private fun setProgress(cert: Certification, p: CertProgress) =
        update { it.copy(progress = it.progress + (cert.id to p)) }

    // MARK: Recording

    fun record(q: Question, chosen: Int, cert: Certification) {
        val p = progress(cert)
        val correct = chosen == q.answer
        val old = p.answers[q.id]
        val rec = AnswerRecord(
            chosen = chosen, correct = correct, date = nowAppleSeconds(),
            attempts = (old?.attempts ?: 0) + 1,
            timesCorrect = (old?.timesCorrect ?: 0) + if (correct) 1 else 0,
        )
        val day = dayKey(System.currentTimeMillis())
        update { d ->
            d.copy(
                progress = d.progress + (cert.id to p.copy(answers = p.answers + (q.id to rec))),
                dailyCounts = d.dailyCounts + (day to ((d.dailyCounts[day] ?: 0) + 1)),
            )
        }
    }

    fun isFlagged(q: Question, cert: Certification) = progress(cert).flagged.contains(q.id)

    fun toggleFlag(q: Question, cert: Certification) {
        val p = progress(cert)
        setProgress(cert, p.copy(flagged = if (p.flagged.contains(q.id)) p.flagged - q.id else p.flagged + q.id))
    }

    fun setExamDate(millis: Long?, cert: Certification) {
        val secs = millis?.let { it / 1000.0 - 978_307_200.0 }
        setProgress(cert, progress(cert).copy(examDate = secs))
    }

    fun reset(cert: Certification) = setProgress(cert, CertProgress(examDate = progress(cert).examDate))

    // MARK: Stats

    fun answeredCount(cert: Certification) = progress(cert).answers.size
    fun correctCount(cert: Certification) = progress(cert).answers.values.count { it.correct }
    fun accuracy(cert: Certification): Double {
        val a = answeredCount(cert)
        return if (a == 0) 0.0 else correctCount(cert).toDouble() / a
    }

    /** Readiness blends accuracy with how much of the bank has been covered. */
    fun readiness(cert: Certification): Double {
        if (cert.questions.isEmpty()) return 0.0
        val coverage = answeredCount(cert).toDouble() / cert.questions.size
        return accuracy(cert) * min(1.0, 0.35 + coverage * 0.65)
    }

    fun domainScores(cert: Certification): List<DomainScore> {
        val answers = progress(cert).answers
        return cert.domains.map { d ->
            val qs = cert.questionsIn(d.id)
            val recs = qs.mapNotNull { answers[it.id] }
            DomainScore(d.id, d.name, qs.size, recs.size, recs.count { it.correct })
        }
    }

    fun weakestDomain(cert: Certification): DomainScore? {
        val scores = domainScores(cert)
        val tried = scores.filter { it.answered > 0 }
        return if (tried.isEmpty()) scores.minByOrNull { it.answered } else tried.minByOrNull { it.accuracy }
    }

    // MARK: Days & streaks

    fun count(dayMillis: Long) = data.dailyCounts[dayKey(dayMillis)] ?: 0
    val todayCount: Int get() = count(System.currentTimeMillis())

    val streak: Int
        get() {
            val cal = Calendar.getInstance()
            if (count(cal.timeInMillis) == 0) cal.add(Calendar.DAY_OF_YEAR, -1)
            var n = 0
            while (count(cal.timeInMillis) > 0) { n++; cal.add(Calendar.DAY_OF_YEAR, -1) }
            return n
        }

    // MARK: Building quizzes

    fun questionOfDay(cert: Certification): Question? {
        if (cert.questions.isEmpty()) return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val days = (cal.timeInMillis / 1000 / 86_400).toInt()
        return cert.questions[abs(days * 7_919) % cert.questions.size]
    }

    fun questionOfDayDone(cert: Certification): Boolean {
        val q = questionOfDay(cert) ?: return false
        val rec = progress(cert).answers[q.id] ?: return false
        return dayKey(appleSecondsToMillis(rec.date)) == dayKey(System.currentTimeMillis())
    }

    private fun prioritized(pool: List<Question>, cert: Certification): List<Question> {
        val answers = progress(cert).answers
        val unanswered = pool.filter { answers[it.id] == null }.shuffled()
        val wrong = pool.filter { answers[it.id]?.correct == false }.shuffled()
        val right = pool.filter { answers[it.id]?.correct == true }.shuffled()
        return unanswered + wrong + right
    }

    fun questionsFor(mode: QuizMode, cert: Certification): List<Question> {
        val p = progress(cert)
        val pool = availableQuestions(cert)
        return when (mode) {
            QuizMode.QuestionOfDay -> listOfNotNull(questionOfDay(cert))
            QuizMode.Pop -> pool.shuffled().take(5)
            QuizMode.Quick10 -> prioritized(pool, cert).take(10).shuffled()
            QuizMode.Timed -> prioritized(pool, cert).take(min(if (hasPremium) 60 else 20, pool.size)).shuffled()
            is QuizMode.LevelUp -> prioritized(pool.filter { it.domain == mode.domain }, cert).take(10)
            QuizMode.Missed -> pool.filter { p.answers[it.id]?.correct == false }.shuffled().take(20)
            QuizMode.Flagged -> pool.filter { p.flagged.contains(it.id) }
            QuizMode.Weakest -> {
                val w = weakestDomain(cert) ?: return emptyList()
                prioritized(pool.filter { it.domain == w.id }, cert).take(10)
            }
        }
    }

    fun timeLimit(mode: QuizMode, count: Int): Int? = if (mode == QuizMode.Timed) count * 72 else null

    companion object {
        const val FREE_QUESTION_LIMIT = 30

        fun dayKey(millis: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }
}
