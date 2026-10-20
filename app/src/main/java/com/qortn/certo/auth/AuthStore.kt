package com.qortn.certo.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qortn.certo.net.Backend
import com.qortn.certo.net.CertoApi

/** The signed-in Certo user (Clerk) for the rest of the app. */
class AuthStore(context: Context) {
    val clerk = ClerkClient(context, Backend.CLERK_PUBLISHABLE_KEY)

    var user by mutableStateOf<ClerkUser?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var isAdmin by mutableStateOf(false)
        private set
    var googleEnabled by mutableStateOf(true)
        private set

    suspend fun start() {
        runCatching { clerk.load() }
        refresh()
        loaded = true
        runCatching {
            val env = clerk.environment()
            val social = env.optJSONObject("user_settings")?.optJSONObject("social")
            googleEnabled = social?.optJSONObject("oauth_google")?.optBoolean("enabled", true) ?: true
        }
    }

    fun refresh() {
        val u = clerk.user
        val isNew = u?.id != user?.id
        user = u
        if (u == null) isAdmin = false
        if (isNew && u != null) pendingAdminRefresh = true
    }

    private var pendingAdminRefresh = false

    suspend fun refreshAdminIfNeeded() {
        if (!pendingAdminRefresh) return
        pendingAdminRefresh = false
        val u = user ?: return
        val t = token() ?: return
        isAdmin = runCatching { CertoApi.registerMember(t, u.provider) }.getOrDefault(false)
    }

    suspend fun token(): String? = runCatching { clerk.token() }.getOrNull()

    suspend fun signOut() {
        clerk.signOut()
        user = null
        isAdmin = false
    }

    /** Deletes the user's progress from the database, then the Clerk account itself. */
    suspend fun deleteAccount() {
        token()?.let { CertoApi.deleteMember(it) }
        clerk.deleteUser()
        user = null
        isAdmin = false
    }

    suspend fun updateName(first: String, last: String?) {
        clerk.updateName(first, last)
        refresh()
    }
}
