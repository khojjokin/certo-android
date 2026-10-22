package com.qortn.certo.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qortn.certo.net.Backend
import com.qortn.certo.net.CertoApi
import com.qortn.certo.net.MemberNotice
import com.qortn.certo.net.MemberState

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
    /** Account state from the Certo server: approval, suspension, warnings and remote settings. */
    var state by mutableStateOf(MemberState())
        private set

    val hasFreePremium: Boolean get() = (state.premiumUntil ?: 0L) > System.currentTimeMillis()

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
        if (u == null) { isAdmin = false; state = MemberState() }
        if (isNew && u != null) pendingAdminRefresh = true
    }

    private var pendingAdminRefresh = false

    suspend fun refreshAdminIfNeeded() {
        if (!pendingAdminRefresh) return
        pendingAdminRefresh = false
        refreshState()
    }

    /** Re-reads the account state (on sign-in and whenever the app comes back to the front). */
    suspend fun refreshState() {
        val u = user ?: return
        val t = token() ?: return
        val s = runCatching { CertoApi.registerMember(t, u.provider) }.getOrNull() ?: return
        state = s
        isAdmin = s.isAdmin
    }

    suspend fun acknowledge(notice: MemberNotice) {
        state = state.copy(notices = state.notices.filterNot { it.id == notice.id })
        token()?.let { CertoApi.acknowledgeNotice(it, notice.id) }
    }

    fun dismissAnnouncement() { state = state.copy(announcement = null) }

    suspend fun token(): String? = runCatching { clerk.token() }.getOrNull()

    suspend fun signOut() {
        clerk.signOut()
        user = null
        isAdmin = false
        state = MemberState()
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
