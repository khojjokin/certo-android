package com.qortn.certo.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class CertDomain(
    val id: String,
    val name: String,
    val part: String?,
    val summary: String?,
    val topics: List<String>,
)

data class CertPart(val id: String, val name: String, val examCode: String, val summary: String)

data class Question(
    val id: String,
    val domain: String,
    val prompt: String,
    val choices: List<String>,
    val answer: Int,
    val explanation: String,
)

data class Certification(
    val id: String,
    val order: Int,
    val name: String,
    val shortName: String,
    val vendor: String,
    val examCode: String,
    val symbol: String,
    val colorHex: String,
    val passingPercent: Int,
    val summary: String,
    val domains: List<CertDomain>,
    val parts: List<CertPart>,
    val questions: List<Question>,
) {
    val color: Color get() = hexColor(colorHex)
    fun domainName(id: String) = domains.firstOrNull { it.id == id }?.name ?: id
    fun questionsIn(domainId: String) = questions.filter { it.domain == domainId }
    fun domainsInPart(partId: String?) = if (partId == null) domains else domains.filter { it.part == partId }
}

fun hexColor(hex: String): Color {
    val s = hex.trim().removePrefix("#")
    val v = s.toLongOrNull(16) ?: return Color.White
    return Color(0xFF000000 or v)
}

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
private fun JSONObject.optStr(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

/** Loads every cert_*.json bundled in assets/certs. */
object CertLibrary {
    @Volatile private var cache: List<Certification>? = null

    fun all(context: Context): List<Certification> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    /** Used by screenshot tests, which read the JSON files straight from the source folder. */
    fun preload(texts: List<String>) {
        cache = texts.mapNotNull { runCatching { parse(JSONObject(it)) }.getOrNull() }
            .sortedWith(compareBy({ it.order }, { it.name }))
    }

    private fun load(context: Context): List<Certification> {
        val files = context.assets.list("certs").orEmpty().filter { it.startsWith("cert_") && it.endsWith(".json") }
        return files.mapNotNull { name ->
            runCatching {
                val text = context.assets.open("certs/$name").bufferedReader().use { it.readText() }
                parse(JSONObject(text))
            }.getOrNull()
        }.sortedWith(compareBy({ it.order }, { it.name }))
    }

    private fun parse(o: JSONObject): Certification {
        val domains = o.getJSONArray("domains").let { a ->
            (0 until a.length()).map { i ->
                val d = a.getJSONObject(i)
                CertDomain(
                    d.getString("id"), d.getString("name"), d.optStr("part"), d.optStr("summary"),
                    d.optJSONArray("topics")?.strings() ?: emptyList(),
                )
            }
        }
        val parts = o.optJSONArray("parts")?.let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                CertPart(p.getString("id"), p.getString("name"), p.getString("examCode"), p.optString("summary"))
            }
        } ?: emptyList()
        val questions = o.getJSONArray("questions").let { a ->
            (0 until a.length()).map { i ->
                val q = a.getJSONObject(i)
                Question(
                    q.getString("id"), q.getString("domain"), q.getString("prompt"),
                    q.getJSONArray("choices").strings(), q.getInt("answer"), q.optString("explanation"),
                )
            }
        }
        return Certification(
            id = o.getString("id"),
            order = o.optInt("order", 99),
            name = o.getString("name"),
            shortName = o.getString("shortName"),
            vendor = o.getString("vendor"),
            examCode = o.getString("examCode"),
            symbol = o.optString("symbol"),
            colorHex = o.optString("colorHex", "8B5CF6"),
            passingPercent = o.optInt("passingPercent", 75),
            summary = o.optString("summary"),
            domains = domains,
            parts = parts,
            questions = questions,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Progress. Stored as JSON in the same shape the iOS app uses (Swift's JSONEncoder defaults),
// so progress syncs between iPhone and Android through the account.
// Dates are seconds since 1 Jan 2001 (Apple's reference date).
// ---------------------------------------------------------------------------------------------

private const val APPLE_EPOCH_OFFSET = 978_307_200.0

fun nowAppleSeconds(): Double = System.currentTimeMillis() / 1000.0 - APPLE_EPOCH_OFFSET
fun appleSecondsToMillis(s: Double): Long = ((s + APPLE_EPOCH_OFFSET) * 1000).toLong()

data class AnswerRecord(
    val chosen: Int,
    val correct: Boolean,
    val date: Double,
    val attempts: Int,
    val timesCorrect: Int,
)

data class CertProgress(
    val answers: Map<String, AnswerRecord> = emptyMap(),
    val flagged: Set<String> = emptySet(),
    val examDate: Double? = null,
)

enum class Appearance(val raw: String, val label: String) {
    System("system", "Auto"), Black("black", "Black"), White("white", "White");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: Black }
}

data class AppData(
    val onboarded: Boolean = false,
    val selectedCertID: String? = null,
    val enrolled: List<String> = emptyList(),
    val progress: Map<String, CertProgress> = emptyMap(),
    val dailyCounts: Map<String, Int> = emptyMap(),
    val appearance: Appearance = Appearance.Black,
    val haptics: Boolean = true,
    val remindersOn: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val dailyGoal: Int = 10,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("onboarded", onboarded)
        selectedCertID?.let { put("selectedCertID", it) }
        put("enrolled", JSONArray(enrolled))
        put("progress", JSONObject().apply {
            progress.forEach { (certId, p) ->
                put(certId, JSONObject().apply {
                    put("answers", JSONObject().apply {
                        p.answers.forEach { (qid, r) ->
                            put(qid, JSONObject().apply {
                                put("chosen", r.chosen); put("correct", r.correct); put("date", r.date)
                                put("attempts", r.attempts); put("timesCorrect", r.timesCorrect)
                            })
                        }
                    })
                    put("flagged", JSONArray(p.flagged.toList()))
                    p.examDate?.let { put("examDate", it) }
                })
            }
        })
        put("dailyCounts", JSONObject(dailyCounts as Map<*, *>))
        put("appearance", appearance.raw)
        put("haptics", haptics)
        put("remindersOn", remindersOn)
        put("reminderHour", reminderHour)
        put("reminderMinute", reminderMinute)
        put("dailyGoal", dailyGoal)
    }

    companion object {
        fun fromJson(o: JSONObject): AppData {
            val progress = mutableMapOf<String, CertProgress>()
            o.optJSONObject("progress")?.let { po ->
                po.keys().forEach { certId ->
                    val p = po.getJSONObject(certId)
                    val answers = mutableMapOf<String, AnswerRecord>()
                    p.optJSONObject("answers")?.let { ao ->
                        ao.keys().forEach { qid ->
                            val r = ao.getJSONObject(qid)
                            answers[qid] = AnswerRecord(
                                r.optInt("chosen"), r.optBoolean("correct"), r.optDouble("date", 0.0),
                                r.optInt("attempts", 1), r.optInt("timesCorrect", 0),
                            )
                        }
                    }
                    val flagged = p.optJSONArray("flagged")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                    val exam = if (p.has("examDate") && !p.isNull("examDate")) p.getDouble("examDate") else null
                    progress[certId] = CertProgress(answers, flagged, exam)
                }
            }
            val daily = mutableMapOf<String, Int>()
            o.optJSONObject("dailyCounts")?.let { d -> d.keys().forEach { daily[it] = d.optInt(it) } }
            return AppData(
                onboarded = o.optBoolean("onboarded", false),
                selectedCertID = if (o.has("selectedCertID") && !o.isNull("selectedCertID")) o.getString("selectedCertID") else null,
                enrolled = o.optJSONArray("enrolled")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                progress = progress,
                dailyCounts = daily,
                appearance = Appearance.from(o.optString("appearance", "black")),
                haptics = o.optBoolean("haptics", true),
                remindersOn = o.optBoolean("remindersOn", false),
                reminderHour = o.optInt("reminderHour", 19),
                reminderMinute = o.optInt("reminderMinute", 0),
                dailyGoal = o.optInt("dailyGoal", 10),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Quiz modes
// ---------------------------------------------------------------------------------------------

sealed class QuizMode(val title: String, val subtitle: String, val tintHex: String) {
    data object QuestionOfDay : QuizMode("Question of the Day", "One new question every day", "10B981")
    data object Pop : QuizMode("Pop Quiz", "5 questions · 20 seconds each", "F59E0B")
    data object Quick10 : QuizMode("Quick 10", "10 questions, instant feedback", "8B5CF6")
    data object Timed : QuizMode("Exam Simulator", "Timed, results at the end", "3B82F6")
    data class LevelUp(val domain: String) : QuizMode("Level Up", "Master one domain at a time", "A855F7")
    data object Missed : QuizMode("Missed Questions", "Retry what you got wrong", "EF4444")
    data object Flagged : QuizMode("Flagged", "Questions you saved", "F97316")
    data object Weakest : QuizMode("Weakest Domain", "Focus on your lowest score", "EC4899")

    val tint: Color get() = hexColor(tintHex)
    val instantFeedback: Boolean get() = this != Timed
    val perQuestionSeconds: Int? get() = if (this == Pop) 20 else null
}

data class DomainScore(val id: String, val name: String, val total: Int, val answered: Int, val correct: Int) {
    val accuracy: Double get() = if (answered == 0) 0.0 else correct.toDouble() / answered
}
