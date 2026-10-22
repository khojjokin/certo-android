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

    suspend fun registerMember(token: String, provider: String): Boolean =
        call("me", token, JSONObject().put("provider", provider)).optBoolean("isAdmin", false)

    suspend fun deleteMember(token: String) { call("deleteAccount", token) }

    /** Strength score only (0-4). The password itself never leaves the device. */
    suspend fun reportPasswordStrength(token: String, score: Int) { call("passwordStrength", token, JSONObject().put("score", score)) }

    suspend fun fetchProgress(token: String): JSONObject? {
        val j = call("getProgress", token)
        return if (j.has("data") && !j.isNull("data")) j.optJSONObject("data") else null
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
