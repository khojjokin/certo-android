package com.qortn.certo.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qortn.certo.CertoApp
import com.qortn.certo.data.Certification
import com.qortn.certo.data.Question
import com.qortn.certo.data.QuizMode
import kotlinx.coroutines.launch

/** Full-screen pages shown above the tabs (never half-height sheets). */
sealed class Page {
    data class Quiz(val mode: QuizMode, val cert: Certification, val questions: List<Question>) : Page()
    data object Paywall : Page()
    data class Legal(val terms: Boolean) : Page()
    data object Account : Page()
    data object ManagePaths : Page()
    data object SwitchPath : Page()
    data object Admin : Page()
}

class Nav {
    val stack = mutableStateListOf<Page>()
    fun push(p: Page) { stack.add(p) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    val top: Page? get() = stack.lastOrNull()
}

@Composable
fun CertoRoot(app: CertoApp, activity: Activity) {
    val auth = app.auth
    val store = app.store
    val billing = app.billing
    val nav = remember { Nav() }
    val scope = rememberCoroutineScope()
    var syncedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { auth.start() }

    // Keep the study profile, premium state and cloud copy in step with the signed-in user.
    val userId = auth.user?.id
    LaunchedEffect(userId) {
        store.switchProfile(userId)
        billing.accountId = userId
        nav.stack.clear()
        if (userId != null && syncedFor != userId) {
            syncedFor = userId
            app.pull()
            auth.refreshAdminIfNeeded()
        }
    }
    SideEffect { store.isPremium = billing.isPremium }

    LaunchedEffect(store.showPaywall) {
        if (store.showPaywall) { store.showPaywall = false; nav.push(Page.Paywall) }
    }

    CertoTheme(store.data.appearance) {
        Box(Modifier.fillMaxSize().background(P.bg)) {
            when {
                !auth.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Brand)
                }
                auth.user == null -> WelcomeScreen(app, activity)
                !store.data.onboarded -> OnboardingScreen(store)
                else -> MainTabs(app, nav)
            }

            val top = nav.top
            if (top != null && auth.user != null) {
                BackHandler { nav.pop() }
                AnimatedContent(top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { page ->
                    Box(Modifier.fillMaxSize().background(P.bg)) {
                        when (page) {
                            is Page.Quiz -> QuizScreen(store, page, onClose = { nav.pop() }, onPaywall = { nav.pop(); nav.push(Page.Paywall) })
                            Page.Paywall -> PaywallScreen(app, activity, onClose = { nav.pop() }, onLegal = { nav.push(Page.Legal(it)) })
                            is Page.Legal -> LegalScreen(page.terms, onClose = { nav.pop() })
                            Page.Account -> AccountScreen(app, onClose = { nav.pop() })
                            Page.ManagePaths -> ManagePathsScreen(store, onClose = { nav.pop() })
                            Page.SwitchPath -> SwitchPathScreen(store, onClose = { nav.pop() })
                            Page.Admin -> AdminScreen(app, onClose = { nav.pop() })
                        }
                    }
                }
            }
        }
    }
}
