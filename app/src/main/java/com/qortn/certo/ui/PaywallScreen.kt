package com.qortn.certo.ui

import android.app.Activity
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.CertoApp
import com.qortn.certo.R

@Composable
fun PaywallScreen(app: CertoApp, activity: Activity, onClose: () -> Unit, onLegal: (Boolean) -> Unit) {
    val billing = app.billing
    var selected by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }       // bumped by "Try again"
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(loads) { loaded = false; runCatching { billing.refresh() }; loaded = true }
    LaunchedEffect(billing.isPremium) { if (billing.isPremium && billing.message == null) onClose() }
    val plans = billing.plans
    val pick = plans.firstOrNull { it.id == selected } ?: plans.firstOrNull()

    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Restore", color = P.inkSoft, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { billing.restore() }.padding(horizontal = 8.dp, vertical = 10.dp))
            CircleIcon(Icons.Filled.Close, onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            Image(painterResource(R.drawable.certo_mark), null, Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(14.dp))
            Text("Certo Premium", color = P.ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("Every path. One plan.", color = P.inkSoft, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(22.dp))
            val total = app.store.certs.size
            val qs = app.store.certs.sumOf { it.questions.size }
            listOf(
                "All $total cert paths",
                "%,d+ questions".format(qs / 100 * 100),
                "Exam simulator",
                "Full stats and streaks",
                "More erha AI help",
                "No ads",
            ).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Brand, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(it, color = P.ink, fontSize = 16.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (plans.isEmpty() && !loaded) {
                Text("Loading plans…", color = P.inkSoft, fontSize = 14.sp, maxLines = 1)
            } else if (plans.isEmpty()) {
                // Google Play unreachable (offline, or Play not ready yet): don't spin forever.
                Text("Plans aren't available right now.", color = P.inkSoft, fontSize = 14.sp, maxLines = 1)
                Text("Try again", color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { loads++ }.padding(horizontal = 12.dp, vertical = 10.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    plans.forEach { plan ->
                        val on = plan.id == pick?.id
                        val shape = RoundedCornerShape(20.dp)
                        Column(
                            Modifier.weight(1f).clip(shape).background(P.card)
                                .border(if (on) 2.dp else 1.dp, if (on) Brand else P.stroke, shape)
                                .clickable { selected = plan.id }.padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (plan.title == "Yearly") "12 months" else "Flexible", color = Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Brand.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp))
                                Spacer(Modifier.weight(1f))
                                Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (on) Brand else P.inkFaint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(plan.title, color = P.ink, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(plan.price, color = P.ink, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(plan.period, color = P.inkSoft, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton("Continue", enabled = pick != null, busy = billing.busy, tint = Brand) {
            pick?.let { billing.buy(activity, it) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Renews automatically. Cancel anytime in Google Play.",
            color = P.inkFaint, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            Text("Terms of Use", color = P.inkSoft, fontSize = 12.sp, modifier = Modifier.clickable { onLegal(true) })
            Text("   ·   ", color = P.inkFaint, fontSize = 12.sp)
            Text("Privacy Policy", color = P.inkSoft, fontSize = 12.sp, modifier = Modifier.clickable { onLegal(false) })
        }
    }

    billing.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { billing.message = null },
            confirmButton = { TextButton({ billing.message = null; if (billing.isPremium) onClose() }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
}
