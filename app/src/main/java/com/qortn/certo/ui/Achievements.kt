package com.qortn.certo.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qortn.certo.data.StudyStore
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Achievement(val id: String, val title: String, val detail: String, val icon: ImageVector, val colors: List<Color>)

/** Milestones worth celebrating, and the queue of celebrations waiting to be shown. */
object Achievements {
    private val fire = listOf(Color(0xFFF97316), Color(0xFFDC2626))
    private val gold = listOf(Color(0xFFFACC15), Color(0xFFCA8A04))
    val all = listOf(
        Achievement("first_quiz", "First steps", "You finished your first quiz. The hardest part is starting.", Icons.Filled.Flag, listOf(Brand, BrandDeep)),
        Achievement("perfect", "Flawless", "Every answer right in a quiz of 5 or more questions.", Icons.Filled.AutoAwesome, listOf(Color(0xFFF59E0B), Color(0xFFEA580C))),
        Achievement("streak_3", "On a roll", "3 days in a row. Habits start here.", Icons.Filled.LocalFireDepartment, fire),
        Achievement("streak_7", "One week strong", "A 7-day study streak.", Icons.Filled.LocalFireDepartment, fire),
        Achievement("streak_14", "Two-week streak", "14 days without missing a day.", Icons.Filled.LocalFireDepartment, listOf(Color(0xFFEF4444), Color(0xFFB91C1C))),
        Achievement("streak_30", "Unstoppable", "A 30-day streak. That's real dedication.", Icons.Filled.MilitaryTech, listOf(Color(0xFFEC4899), Color(0xFFBE185D))),
        Achievement("streak_100", "Centurion", "100 days in a row. Legendary.", Icons.Filled.WorkspacePremium, gold),
        Achievement("answered_100", "Century", "100 questions answered.", Icons.Filled.MilitaryTech, listOf(Good, Color(0xFF15803D))),
        Achievement("answered_500", "Question machine", "500 questions answered.", Icons.Filled.MilitaryTech, listOf(Color(0xFF14B8A6), Color(0xFF0F766E))),
        Achievement("answered_1000", "Thousand club", "1,000 questions answered.", Icons.Filled.EmojiEvents, gold),
        Achievement("exam_ready", "Exam ready", "Your readiness passed 80%. Time to book that exam!", Icons.Filled.Verified, listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
    )
    val dailyGoal = Achievement("daily_goal", "Daily goal done", "You hit today's goal. See you tomorrow!", Icons.Filled.TrackChanges, listOf(Good, Color(0xFF15803D)))

    val queue = mutableStateListOf<Achievement>()

    fun evaluate(context: Context, store: StudyStore, quizCorrect: Int? = null, quizTotal: Int? = null) {
        val prefs = context.getSharedPreferences("certo", Context.MODE_PRIVATE)
        val firstRun = !prefs.getBoolean("ach.init", false)
        val unlocked = prefs.getStringSet("ach.unlocked", emptySet())!!.toMutableSet()

        val total = store.data.progress.values.sumOf { it.answers.size }
        val earned = mutableListOf<String>()
        if (total > 0) earned += "first_quiz"
        for (n in listOf(3, 7, 14, 30, 100)) if (store.streak >= n) earned += "streak_$n"
        for (n in listOf(100, 500, 1000)) if (total >= n) earned += "answered_$n"
        store.currentCert?.let { if (store.answeredCount(it) >= 30 && store.readiness(it) >= 0.8) earned += "exam_ready" }
        if (quizTotal != null && quizCorrect != null && quizTotal >= 5 && quizCorrect == quizTotal) earned += "perfect"

        val fresh = earned.filter { it !in unlocked }
        unlocked += earned
        prefs.edit().putBoolean("ach.init", true).putStringSet("ach.unlocked", unlocked).apply()
        if (!firstRun) {
            // Most impressive first when several unlock at once.
            fresh.sortedByDescending { id -> all.indexOfFirst { it.id == id } }
                .mapNotNull { id -> all.firstOrNull { it.id == id } }
                .forEach { queue += it }
        }
        val today = StudyStore.dayKey(System.currentTimeMillis())
        if (store.todayCount >= store.data.dailyGoal && prefs.getString("ach.goalDay", null) != today) {
            prefs.edit().putString("ach.goalDay", today).apply()
            if (!firstRun) queue += dailyGoal
        }
    }
}

/** Shows queued celebrations one at a time. Put once near the root of the UI. */
@Composable
fun AchievementHost() {
    val a = Achievements.queue.firstOrNull() ?: return
    AchievementCelebration(a) { if (Achievements.queue.isNotEmpty()) Achievements.queue.removeAt(0) }
}

@Composable
fun AchievementCelebration(a: Achievement, onDone: () -> Unit) {
    val pop = remember(a.id) { Animatable(0.3f) }
    val textIn = remember(a.id) { Animatable(0f) }
    val burst = remember(a.id) { Animatable(0f) }
    val pieces = remember(a.id) { List(80) { Piece() } }
    val spin by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "spin")

    LaunchedEffect(a.id) {
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(a.id) { delay(200); textIn.animateTo(1f, tween(400)) }
    LaunchedEffect(a.id) { burst.animateTo(1f, tween(3000, easing = LinearEasing)) }
    LaunchedEffect(a.id) { delay(5000); onDone() }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            // Confetti
            val colors = a.colors + listOf(Color.White, Color(0xFFA78BFA))
            Canvas(Modifier.fillMaxSize()) {
                val t = burst.value * 3f
                if (burst.value >= 1f) return@Canvas
                val origin = Offset(size.width / 2, size.height * 0.42f)
                for (p in pieces) {
                    val x = origin.x + cos(p.angle) * p.speed * t + p.drift * t
                    val y = origin.y + sin(p.angle) * p.speed * t + 0.5f * 1800f * t * t
                    rotate(p.spin * t * 57f, pivot = Offset(x, y)) {
                        drawRoundRect(colors[p.color % colors.size], topLeft = Offset(x - p.w / 2, y - p.h / 2), size = Size(p.w, p.h),
                            cornerRadius = CornerRadius(4f), alpha = (1f - burst.value).coerceIn(0f, 1f))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                    Canvas(Modifier.size(220.dp).rotate(spin).alpha(pop.value.coerceIn(0f, 1f))) {
                        for (i in 0 until 12) {
                            rotate(i * 30f) {
                                drawRoundRect(
                                    Brush.verticalGradient(listOf(Color.Transparent, a.colors[0].copy(alpha = 0.55f)), startY = 0f, endY = size.height * 0.32f),
                                    topLeft = Offset(size.width / 2 - 6f, 0f), size = Size(12f, size.height * 0.32f), cornerRadius = CornerRadius(6f),
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.size(128.dp).scale(pop.value).clip(CircleShape)
                            .background(Brush.linearGradient(a.colors))
                            .border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(58.dp)) }
                }
                Column(Modifier.alpha(textIn.value).padding(horizontal = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (a.id == "daily_goal") "GOAL REACHED" else "ACHIEVEMENT UNLOCKED", color = a.colors[0], fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(a.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(a.detail, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "Nice!", color = Color.Black, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onDone).padding(horizontal = 44.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

private class Piece {
    val angle = Random.nextDouble(-Math.PI * 0.95, -Math.PI * 0.05).toFloat()
    val speed = Random.nextDouble(700.0, 1500.0).toFloat()
    val spin = Random.nextDouble(-8.0, 8.0).toFloat()
    val w = Random.nextDouble(14.0, 24.0).toFloat()
    val h = Random.nextDouble(22.0, 40.0).toFloat()
    val color = Random.nextInt(100)
    val drift = Random.nextDouble(-120.0, 120.0).toFloat()
}
