package com.qortn.certo.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Backend {
    const val URL = "https://fjnifvugmbtajelmiuuh.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_CEwinwr0mVRMfG8qtbpQcA_lZHg_nfw"
    const val CLERK_PUBLISHABLE_KEY = "pk_live_Y2xlcmsucW9ydHluLmNvbSQ"
}

/** Calls the certo-api Edge Function (Supabase) with the user's Clerk session token. */
object CertoApi {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    private suspend fun call(action: String, token: String, extra: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        extra.put("action", action)
        val req = Request.Builder()
            .url("${Backend.URL}/functions/v1/certo-api")
            .header("apikey", Backend.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .post(extra.toString().toRequestBody(jsonType))
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!res.isSuccessful) throw Exception(json.optString("error", "Something went wrong (${res.code})."))
            json
        }
    }

    /** Registers the member and returns their account state (admin, status, notices, remote settings). */
    suspend fun registerMember(token: String, provider: String): MemberState {
        val j = call("me", token, JSONObject().put("provider", provider))
        val arr = j.optJSONArray("notices")
        val notices = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val n = arr!!.optJSONObject(i) ?: return@mapNotNull null
            MemberNotice(n.optInt("id"), n.optString("kind") == "warning", n.optString("title"), n.optString("body"))
        }
        fun str(k: String) = if (j.isNull(k)) null else j.optString(k).ifBlank { null }
        return MemberState(
            isAdmin = j.optBoolean("isAdmin", false),
            status = j.optString("status", "active"),
            statusMessage = str("statusMessage"),
            premiumUntil = str("premiumUntil")?.let { runCatching { java.time.Instant.parse(it.replace("+00:00", "Z")).toEpochMilli() }.getOrNull() },
            notices = notices,
            maintenance = str("maintenance"),
            announcement = str("announcement"),
            minVersion = str("minVersion"),
            supportUnread = j.optInt("supportUnread", 0),
        )
    }

    // erha (AI study assistant)

    data class ErhaReply(val text: String, val offTopic: Boolean, val remaining: Int?, val id: Int?)

    /** Asks erha. Before the learner answers, the correct answer is never sent, so erha can't give it away. */
    suspend fun erha(token: String, mode: String, cert: String?, question: com.qortn.certo.data.Question?, picked: Int? = null,
                     revealAnswer: Boolean = true, messages: List<Pair<String, String>> = emptyList()): ErhaReply {
        val body = JSONObject().put("mode", mode).put("consent", true)
        cert?.let { body.put("cert", it) }
        val msgs = org.json.JSONArray()
        messages.forEach { (role, content) -> msgs.put(JSONObject().put("role", role).put("content", content)) }
        body.put("messages", msgs)
        question?.let { q ->
            val qd = JSONObject().put("prompt", q.prompt).put("choices", org.json.JSONArray(q.choices))
            if (revealAnswer || mode == "hint") { qd.put("answer", q.answer); qd.put("explanation", q.explanation) }
            picked?.let { qd.put("picked", it) }
            body.put("question", qd)
        }
        val j = call("erha", token, body)
        return ErhaReply(j.optString("reply"), j.optBoolean("offTopic"), if (j.has("remaining")) j.optInt("remaining") else null, if (j.isNull("id")) null else j.optInt("id"))
    }

    suspend fun rateErha(token: String, id: Int, helpful: Boolean) { runCatching { call("rateErha", token, JSONObject().put("id", id).put("rating", if (helpful) 1 else -1)) } }

    suspend fun reportQuestion(token: String, cert: String, q: com.qortn.certo.data.Question, reason: String, note: String) {
        call("reportQuestion", token, JSONObject().put("cert", cert).put("questionId", q.id).put("prompt", q.prompt).put("reason", reason).put("note", note))
    }

    // Live chat with the Certo team

    data class SupportMessage(val id: Int, val at: String, val fromTeam: Boolean, val body: String)

    suspend fun supportMessages(token: String, after: Int?): List<SupportMessage> {
        val body = JSONObject()
        after?.let { body.put("after", it) }
        val arr = call("support.get", token, body).optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            SupportMessage(m.optInt("id"), m.optString("at"), m.optBoolean("fromTeam"), m.optString("body"))
        }
    }

    suspend fun sendSupport(token: String, text: String) { call("support.send", token, JSONObject().put("body", text)) }

    suspend fun acknowledgeNotice(token: String, id: Int) { runCatching { call("ackNotice", token, JSONObject().put("id", id)) } }

    /** Records a Google Play purchase so the admin console can refund it. */
    suspend fun reportPlayPurchase(token: String, purchaseToken: String, productId: String?, orderId: String?, purchaseTime: Long) {
        call("playPurchase", token, JSONObject().put("token", purchaseToken).put("productId", productId).put("orderId", orderId).put("purchaseTime", purchaseTime))
    }

    suspend fun deleteMember(token: String) { call("deleteAccount", token) }

    /** Strength score only (0-4). The password itself never leaves the device. */
    suspend fun reportPasswordStrength(token: String, score: Int) { call("passwordStrength", token, JSONObject().put("score", score)) }

    /** The account's saved progress and when it was saved (epoch ms). */
    suspend fun fetchProgress(token: String): Pair<JSONObject, Long?>? {
        val j = call("getProgress", token)
        if (!j.has("data") || j.isNull("data")) return null
        val at = if (j.isNull("updatedAt")) null else runCatching { java.time.OffsetDateTime.parse(j.optString("updatedAt")).toInstant().toEpochMilli() }.getOrNull()
        return j.optJSONObject("data")?.let { it to at }
    }

    suspend fun saveProgress(token: String, data: JSONObject) {
        call("saveProgress", token, JSONObject().put("data", data))
    }

    suspend fun setSubscriptions(token: String, on: Boolean) { call("setSubscriptions", token, JSONObject().put("enabled", on)) }

    suspend fun subscriptionsEnabled(): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${Backend.URL}/rest/v1/app_config?select=subscriptions_enabled")
                .header("apikey", Backend.PUBLISHABLE_KEY)
                .build()
            http.newCall(req).execute().use { res ->
                JSONArray(res.body?.string().orEmpty()).optJSONObject(0)?.optBoolean("subscriptions_enabled", true)
            }
        }.getOrNull()
    }
}


/** What the server says about the signed-in account. */
data class MemberState(
    val isAdmin: Boolean = false,
    val status: String = "active",
    val statusMessage: String? = null,
    val premiumUntil: Long? = null,
    val notices: List<MemberNotice> = emptyList(),
    val maintenance: String? = null,
    val announcement: String? = null,
    val minVersion: String? = null,
    val supportUnread: Int = 0,
)

data class MemberNotice(val id: Int, val isWarning: Boolean, val title: String, val body: String)
