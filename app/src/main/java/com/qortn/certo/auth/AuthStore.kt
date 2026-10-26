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
        // Open straight into the app from the saved session (no wait on a slow or missing network);
        // the check with Clerk below then confirms it, or signs out if the session has gone.
        refresh()
        if (user != null) loaded = true
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
        var s = runCatching { CertoApi.registerMember(t, u.provider) }.getOrNull() ?: return
        if (s.announcement != null && s.announcement == prefs.getString("dismissedAnnouncement", null)) s = s.copy(announcement = null)
        state = s
        isAdmin = s.isAdmin
    }

    suspend fun acknowledge(notice: MemberNotice) {
        state = state.copy(notices = state.notices.filterNot { it.id == notice.id })
        token()?.let { CertoApi.acknowledgeNotice(it, notice.id) }
    }

    fun dismissAnnouncement() {
        state.announcement?.let { prefs.edit().putString("dismissedAnnouncement", it).apply() }
        state = state.copy(announcement = null)
    }

    fun clearSupportUnread() { state = state.copy(supportUnread = 0) }

    private val prefs = context.getSharedPreferences("certo", Context.MODE_PRIVATE)

    suspend fun token(): String? = runCatching { clerk.token() }.getOrNull()

    suspend fun signOut() {
        runCatching { clerk.signOut() }
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
        state = MemberState()
    }

    suspend fun updateName(first: String, last: String?) {
        clerk.updateName(first, last)
        refresh()
    }
}
