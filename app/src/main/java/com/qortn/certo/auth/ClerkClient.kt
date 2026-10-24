package com.qortn.certo.auth

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A Clerk error with a user-facing message. */
class ClerkException(val code: String?, override val message: String) : Exception(message)

data class ClerkUser(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val email: String,
    val username: String?,
    val provider: String,
    val imageUrl: String?,
    val createdAt: Long,
) {
    val name: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank {
            username ?: email.substringBefore("@").ifBlank { "Certo user" }
        }
}

/**
 * Minimal native client for Clerk's Frontend API (the same endpoints the official mobile SDKs use).
 * The device token Clerk returns in the Authorization header identifies this device's client.
 */
class ClerkClient(context: Context, publishableKey: String) {
    private val prefs = context.getSharedPreferences("certo.clerk", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val base: HttpUrl = "https://${frontendApi(publishableKey)}/v1/".toHttpUrl()
    private val deviceId: String = prefs.getString("deviceId", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }

    private var deviceToken: String?
        get() = prefs.getString("deviceToken", null)
        set(v) { prefs.edit().putString("deviceToken", v).apply() }

    var client: JSONObject? = null
        private set

    val sessionId: String? get() = client?.optStringOrNull("last_active_session_id")

    val user: ClerkUser?
        get() {
            val c = client ?: return null
            val sid = sessionId ?: return null
            val sessions = c.optJSONArray("sessions") ?: return null
            for (i in 0 until sessions.length()) {
                val s = sessions.getJSONObject(i)
                if (s.optString("id") == sid && s.optString("status") == "active") {
                    return s.optJSONObject("user")?.let(::parseUser)
                }
            }
            return null
        }

    // MARK: HTTP

    private suspend fun request(
        method: String,
        path: String,
        form: Map<String, String>? = null,
        query: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments(path).apply {
            addQueryParameter("_is_native", "true")
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val body = form?.let { f -> FormBody.Builder().apply { f.forEach { (k, v) -> add(k, v) } }.build() }
        val builder = Request.Builder().url(url)
            .header("x-mobile", "1")
            .header("x-native-device-id", deviceId)
            .header("Accept", "application/json")
        deviceToken?.let { builder.header("Authorization", it) }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
            else -> builder.method(method, body ?: FormBody.Builder().build())
        }
        try {
            http.newCall(builder.build()).execute().use { res ->
                res.header("Authorization")?.let { deviceToken = it }
                val text = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                json.optJSONObject("client")?.let { client = it }
                if (!res.isSuccessful) {
                    val err = json.optJSONArray("errors")?.optJSONObject(0)
                    throw ClerkException(
                        err?.optString("code"),
                        err?.optStringOrNull("long_message") ?: err?.optStringOrNull("message") ?: "Something went wrong (${res.code}).",
                    )
                }
                json
            }
        } catch (e: ClerkException) {
            throw e
        } catch (e: java.io.IOException) {
            throw ClerkException("network", "You appear to be offline. Check your connection and try again.")
        }
    }

    /** Responses from client endpoints wrap the resource in "response". */
    private fun JSONObject.resource(): JSONObject = optJSONObject("response") ?: this

    // MARK: Client

    suspend fun load() {
        val json = request("GET", "client")
        val c = json.optJSONObject("response")
        client = if (c != null && c.has("id")) c else client
    }

    // MARK: Sign in

    suspend fun signInWithPassword(identifier: String, password: String): JSONObject =
        request("POST", "client/sign_ins", mapOf("identifier" to identifier, "password" to password, "strategy" to "password")).resource()

    suspend fun startSignIn(identifier: String): JSONObject =
        request("POST", "client/sign_ins", mapOf("identifier" to identifier)).resource()

    suspend fun prepareSecondFactorEmail(signInId: String): JSONObject {
        val signIn = request("GET", "client/sign_ins/$signInId").resource()
        val factor = signIn.optJSONArray("supported_second_factors")?.findStrategy("email_code")
        val form = mutableMapOf("strategy" to "email_code")
        factor?.optStringOrNull("email_address_id")?.let { form["email_address_id"] = it }
        return request("POST", "client/sign_ins/$signInId/prepare_second_factor", form).resource()
    }

    suspend fun attemptSecondFactorEmail(signInId: String, code: String): JSONObject =
        request("POST", "client/sign_ins/$signInId/attempt_second_factor", mapOf("strategy" to "email_code", "code" to code)).resource()

    suspend fun prepareFirstFactorEmail(signIn: JSONObject): JSONObject {
        val id = signIn.getString("id")
        val factor = signIn.optJSONArray("supported_first_factors")?.findStrategy("email_code")
            ?: throw ClerkException(null, "Email codes aren't available for this account.")
        return request(
            "POST", "client/sign_ins/$id/prepare_first_factor",
            mapOf("strategy" to "email_code", "email_address_id" to factor.getString("email_address_id")),
        ).resource()
    }

    suspend fun attemptFirstFactorEmail(signInId: String, code: String): JSONObject =
        request("POST", "client/sign_ins/$signInId/attempt_first_factor", mapOf("strategy" to "email_code", "code" to code)).resource()

    // Password reset
    suspend fun prepareReset(signIn: JSONObject): JSONObject {
        val id = signIn.getString("id")
        val factor = signIn.optJSONArray("supported_first_factors")?.findStrategy("reset_password_email_code")
            ?: throw ClerkException(null, "Password reset isn't available for this account.")
        return request(
            "POST", "client/sign_ins/$id/prepare_first_factor",
            mapOf("strategy" to "reset_password_email_code", "email_address_id" to factor.getString("email_address_id")),
        ).resource()
    }

    suspend fun attemptReset(signInId: String, code: String): JSONObject =
        request("POST", "client/sign_ins/$signInId/attempt_first_factor", mapOf("strategy" to "reset_password_email_code", "code" to code)).resource()

    suspend fun resetPassword(signInId: String, password: String): JSONObject =
        request("POST", "client/sign_ins/$signInId/reset_password", mapOf("password" to password, "sign_out_of_other_sessions" to "true")).resource()

    // MARK: Sign up

    suspend fun signUp(email: String, password: String, firstName: String, lastName: String?): JSONObject {
        val form = mutableMapOf("email_address" to email, "password" to password, "first_name" to firstName)
        if (!lastName.isNullOrBlank()) form["last_name"] = lastName
        return request("POST", "client/sign_ups", form).resource()
    }

    suspend fun prepareSignUpEmail(signUpId: String): JSONObject =
        request("POST", "client/sign_ups/$signUpId/prepare_verification", mapOf("strategy" to "email_code")).resource()

    suspend fun attemptSignUpEmail(signUpId: String, code: String): JSONObject =
        request("POST", "client/sign_ups/$signUpId/attempt_verification", mapOf("strategy" to "email_code", "code" to code)).resource()

    // MARK: Google (OAuth redirect)

    /** Starts OAuth and returns (signInId, url to open in the browser). */
    suspend fun startOAuth(strategy: String, redirectUrl: String): Pair<String, String> {
        var signIn = request("POST", "client/sign_ins", mapOf("strategy" to strategy, "redirect_url" to redirectUrl)).resource()
        var url = signIn.optJSONObject("first_factor_verification")?.optStringOrNull("external_verification_redirect_url")
        if (url == null) {
            signIn = request(
                "POST", "client/sign_ins/${signIn.getString("id")}/prepare_first_factor",
                mapOf("strategy" to strategy, "redirect_url" to redirectUrl),
            ).resource()
            url = signIn.optJSONObject("first_factor_verification")?.optStringOrNull("external_verification_redirect_url")
        }
        val link = url ?: throw ClerkException(null, "Couldn't start sign-in. Please try again.")
        return signIn.getString("id") to withAccountPicker(link)
    }

    /** Google picks the phone's main account on its own unless asked; this makes it show the account chooser. */
    private fun withAccountPicker(link: String): String {
        val uri = android.net.Uri.parse(link)
        if (uri.host?.endsWith("google.com") != true) return link
        val b = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name == "prompt") continue
            for (v in uri.getQueryParameters(name)) b.appendQueryParameter(name, v)
        }
        return b.appendQueryParameter("prompt", "select_account").build().toString()
    }

    /** Finishes OAuth after the browser returns to the app. */
    suspend fun completeOAuth(signInId: String, nonce: String?): Boolean {
        val query = if (nonce.isNullOrBlank()) emptyMap() else mapOf("rotating_token_nonce" to nonce)
        val signIn = request("GET", "client/sign_ins/$signInId", query = query).resource()
        if (signIn.optString("status") == "complete") { load(); return true }
        val verification = signIn.optJSONObject("first_factor_verification")
        if (verification?.optString("status") == "transferable") {
            val signUp = request("POST", "client/sign_ups", mapOf("transfer" to "true")).resource()
            if (signUp.optString("status") == "complete") { load(); return true }
            throw ClerkException(null, "We couldn't finish setting up your account. Please try again.")
        }
        load()
        return user != null
    }

    // MARK: Session

    suspend fun token(): String? {
        val sid = sessionId ?: return null
        val json = request("POST", "client/sessions/$sid/tokens")
        return json.optStringOrNull("jwt") ?: json.optJSONObject("response")?.optStringOrNull("jwt")
    }

    suspend fun signOut() {
        val sid = sessionId
        runCatching { if (sid != null) request("POST", "client/sessions/$sid/remove") else request("DELETE", "client/sessions") }
        client = null
    }

    suspend fun updateName(first: String, last: String?) {
        val sid = sessionId ?: return
        request("PATCH", "me", mapOf("first_name" to first, "last_name" to (last ?: "")), mapOf("_clerk_session_id" to sid))
        load()
    }

    suspend fun deleteUser() {
        val sid = sessionId ?: return
        request("DELETE", "me", query = mapOf("_clerk_session_id" to sid))
        client = null
        runCatching { load() }
    }

    suspend fun environment(): JSONObject = request("GET", "environment")

    // MARK: Parsing

    private fun parseUser(u: JSONObject): ClerkUser {
        val primaryId = u.optStringOrNull("primary_email_address_id")
        val emails = u.optJSONArray("email_addresses") ?: JSONArray()
        var email = ""
        for (i in 0 until emails.length()) {
            val e = emails.getJSONObject(i)
            if (primaryId == null || e.optString("id") == primaryId) { email = e.optString("email_address"); break }
        }
        val provider = u.optJSONArray("external_accounts")?.optJSONObject(0)?.optString("provider")
            ?.removePrefix("oauth_") ?: "email"
        return ClerkUser(
            id = u.getString("id"),
            firstName = u.optStringOrNull("first_name"),
            lastName = u.optStringOrNull("last_name"),
            email = email,
            username = u.optStringOrNull("username"),
            provider = provider,
            imageUrl = if (u.optBoolean("has_image")) u.optStringOrNull("image_url") else null,
            createdAt = u.optLong("created_at"),
        )
    }

    companion object {
        fun frontendApi(pk: String): String {
            val encoded = pk.substringAfter("pk_live_").substringAfter("pk_test_")
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
            return decoded.trimEnd('$')
        }
    }
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

private fun JSONArray.findStrategy(strategy: String): JSONObject? {
    for (i in 0 until length()) {
        val f = optJSONObject(i) ?: continue
        if (f.optString("strategy") == strategy) return f
    }
    return null
}
