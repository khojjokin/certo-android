package com.qortn.certo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

enum class SplashMode { Launch, Cover, Replay }

/**
 * Short branded intro: the Certo logo in the middle, "Made by Qortn" at the bottom.
 * [SplashMode.Cover] is drawn instantly and waits (shown when leaving the app, so the recents
 * snapshot is the splash); [SplashMode.Replay] plays it again after a long break.
 */
@Composable
fun SplashScreen(mode: SplashMode = SplashMode.Launch, onFinish: () -> Unit) {
    val start = if (mode == SplashMode.Launch) 0f else 1f
    val logo = remember { Animatable(start) }
    val title = remember { Animatable(start) }
    val footer = remember { Animatable(start) }
    val glow = remember { Animatable(start) }

    LaunchedEffect(mode) {
        if (mode == SplashMode.Cover) return@LaunchedEffect
        if (mode == SplashMode.Replay) {
            logo.snapTo(1f); title.snapTo(1f); footer.snapTo(1f)
            glow.snapTo(0.4f); glow.animateTo(1f, tween(900))
            delay(300)
            onFinish()
            return@LaunchedEffect
        }
        coroutineScope {
            launch { logo.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
            launch { glow.animateTo(1f, tween(1400)) }
            launch { delay(180); title.animateTo(1f, tween(450)) }
            launch { delay(380); footer.animateTo(1f, tween(450)) }
        }
        delay(900)
        onFinish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF6D28D9).copy(alpha = 0.25f + 0.2f * glow.value), Color.Transparent),
                    radius = 900f,
                )
            )
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.certo_mark), contentDescription = "Certo",
                modifier = Modifier
                    .size(112.dp)
                    .scale(0.82f + 0.18f * logo.value)
                    .alpha(logo.value.coerceIn(0f, 1f))
                    .shadow((8 + 24 * glow.value).dp, RoundedCornerShape(26.dp), ambientColor = Color(0xFF8B5CF6), spotColor = Color(0xFF8B5CF6))
                    .clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = title.value; translationY = (1 - title.value) * 24f },
            ) {
                Text("Certo", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Pass your IT certification", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .graphicsLayer { alpha = footer.value; translationY = (1 - footer.value) * 18f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("MADE BY", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Image(painterResource(R.drawable.qortn_mark_white), contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text("Qortn", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
