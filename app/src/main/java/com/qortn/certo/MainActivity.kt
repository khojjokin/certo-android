package com.qortn.certo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableSharedFlow
import com.qortn.certo.ui.CertoRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        val app = application as CertoApp
        setContent { CertoRoot(app, this) }
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
        /** OAuth redirects back into the app (Google sign-in). */
        val callbacks = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    }
}
