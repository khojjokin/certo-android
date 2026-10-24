package com.qortn.certo

import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import com.qortn.certo.ui.CertoRoot
import com.qortn.certo.ui.SplashScreen

class MainActivity : ComponentActivity() {
    /** The branded intro: shown once each time the app is opened fresh (never on top of a visible app). */
    private var showSplash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        val app = application as CertoApp
        if (savedInstanceState != null) showSplash = false   // rotation etc., not a fresh open
        setContent {
            Box {
                CertoRoot(app, this@MainActivity)
                AnimatedVisibility(visible = showSplash, enter = androidx.compose.animation.EnterTransition.None, exit = fadeOut(tween(450))) {
                    SplashScreen { showSplash = false }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as CertoApp).leftAt = 0
        val app = application as CertoApp
        app.scope.launch { app.auth.refreshState() }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as CertoApp).leftAt = System.currentTimeMillis()
        if (!isChangingConfigurations) {
            (application as CertoApp).flush()
            Nudges.scheduleAll(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "clerk") callbacks.tryEmit(uri)
    }

    companion object {
        private const val AWAY_LIMIT_MS = 10 * 60 * 1000L
        /** OAuth redirects back into the app (Google sign-in). */
        val callbacks = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    }
}
