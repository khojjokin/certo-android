package com.qortn.certo.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.CertoApp
import com.qortn.certo.MainActivity
import com.qortn.certo.R
import com.qortn.certo.auth.ClerkException
import com.qortn.certo.auth.optStringOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject

const val REDIRECT_URL = "clerk://com.qortn.certo.callback"

/** Turns Clerk errors into friendly text. */
fun friendly(e: Throwable): String {
    val c = e as? ClerkException ?: return e.message ?: "Something went wrong. Please try again."
    return when (c.code) {
        "form_password_incorrect" -> "That password isn't right. Try again or reset it."
        "form_identifier_not_found" -> "There's no Certo account with that email or username. Create one instead?"
        "form_identifier_exists" -> "That email already has an account. Sign in instead."
        "form_password_pwned" -> "That password is too easy to guess. Try a stronger one, like a few random words with a number."
        "form_password_not_strong_enough", "form_password_validation_failed" -> "That password is too weak. Try a stronger one, like a few random words with a number."
        "form_password_length_too_short" -> "Use at least 8 characters for your password."
        "form_code_incorrect" -> "That code isn't right. Check your email and try again."
        "verification_expired" -> "That code has expired. Tap Resend for a new one."
        "too_many_requests", "user_locked" -> "Too many attempts. Please wait a little and try again."
        "session_exists" -> "You're already signed in."
        else -> if (c.message.lowercase().contains("breach")) "That password is too easy to guess. Try a stronger one, like a few random words with a number." else c.message
    }
}

private enum class Step { Form, VerifySignUp, VerifySignIn, ResetCode, ResetPassword }

@Composable
fun WelcomeScreen(app: CertoApp, activity: Activity) {
    var mode by remember { mutableStateOf<Boolean?>(null) } // true = sign up, false = sign in
    Column(
        Modifier.fillMaxSize().background(P.bg).systemBarsPadding().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.certo_mark), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        Spacer(Modifier.height(24.dp))
        Text("Pass your IT\ncertification.", color = P.ink, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 44.sp)
        Spacer(Modifier.height(14.dp))
        Text("4,200+ questions across CompTIA, Cisco, Microsoft, AWS, ITIL and ISC2.", color = P.inkSoft, fontSize = 17.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("A+", "Network+", "Security+", "AWS", "CCNA").forEach {
                Text(it, color = Brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(Brand.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Create account") { mode = true }
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Sign in") { mode = false }
        Spacer(Modifier.height(24.dp))
    }
    mode?.let { signUp ->
        BackHandler { mode = null }
        AuthScreen(app, activity, signUp, onClose = { mode = null }, onSwitch = { mode = !signUp })
    }
}

@Composable
private fun AuthScreen(app: CertoApp, activity: Activity, signUp: Boolean, onClose: () -> Unit, onSwitch: () -> Unit) {
    val clerk = app.auth.clerk
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Form) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var attemptId by remember { mutableStateOf<String?>(null) }
    var oauthSignInId by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val id = email.trim().lowercase()
    val emailValid = id.contains("@") && id.substringAfter("@").contains(".")
    val canSubmit = if (signUp) emailValid && password.length >= 8 && first.isNotBlank() else id.length >= 3 && password.isNotEmpty()

    fun finish() { scope.launch { runCatching { clerk.load() }; app.auth.refresh() } }

    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { work() } catch (e: Throwable) { error = friendly(e) } finally { busy = false }
        }
    }

    suspend fun continueSignIn(si: JSONObject) {
        when (si.optString("status")) {
            "complete" -> finish()
            "needs_second_factor", "needs_client_trust" -> {
                clerk.prepareSecondFactorEmail(si.getString("id"))
                attemptId = si.getString("id"); code = ""; step = Step.VerifySignIn
            }
            "needs_first_factor" -> {
                val prepared = clerk.prepareFirstFactorEmail(si)
                attemptId = prepared.getString("id"); code = ""; step = Step.VerifySignIn
            }
            else -> throw Exception("We couldn't sign you in. Please try again.")
        }
    }

    // Google sign-in returns here through the clerk:// callback.
    LaunchedEffect(Unit) {
        MainActivity.callbacks.collect { uri: Uri ->
            val sid = oauthSignInId ?: return@collect
            oauthSignInId = null
            run {
                val ok = clerk.completeOAuth(sid, uri.getQueryParameter("rotating_token_nonce"))
                if (ok) finish() else throw Exception("Sign-in was cancelled.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(P.bg).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step != Step.Form) CircleIcon(Icons.Filled.ArrowBack) { step = Step.Form; error = null; code = "" } else Spacer(Modifier.size(40.dp))
            CircleIcon(Icons.Filled.Close, onClose)
        }
        Spacer(Modifier.height(20.dp))
        Image(painterResource(R.drawable.certo_mark), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))

        when (step) {
            Step.Form -> {
                Title(if (signUp) "Create your account" else "Welcome back", if (signUp) "Save your progress and study on any device." else "Sign in to pick up where you left off.")
                if (app.auth.googleEnabled) {
                    SecondaryButton("Continue with Google", icon = { GoogleG() }) {
                        run {
                            val (sid, url) = clerk.startOAuth("oauth_google", REDIRECT_URL)
                            oauthSignInId = sid
                            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(url))
                        }
                    }
                    OrDivider()
                }
                if (signUp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(first, { first = it }, "First name", Icons.Filled.Person, Modifier.weight(1f))
                        Field(last, { last = it }, "Last name", null, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Field(email, { email = it }, if (signUp) "Email address" else "Email or username", Icons.Filled.Email, keyboard = KeyboardType.Email)
                Spacer(Modifier.height(10.dp))
                Field(password, { password = it }, if (signUp) "Password (8+ characters)" else "Password", Icons.Filled.Lock, secure = true)
                if (!signUp) {
                    Text("Forgot password?", color = Brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.End).padding(top = 10.dp).clickable {
                            if (!emailValid) { error = "Enter your email address first."; return@clickable }
                            run {
                                val si = clerk.startSignIn(id)
                                val prepared = clerk.prepareReset(si)
                                attemptId = prepared.getString("id"); code = ""; step = Step.ResetCode
                            }
                        })
                }
                Spacer(Modifier.height(16.dp))
                error?.let { ErrorBanner(it); Spacer(Modifier.height(12.dp)) }
                PrimaryButton(if (signUp) "Create account" else "Sign in", enabled = canSubmit, busy = busy) {
                    run {
                        if (signUp) {
                            val su = clerk.signUp(id, password, first.trim(), last.trim().ifBlank { null })
                            when {
                                su.optString("status") == "complete" -> finish()
                                (su.optJSONArray("unverified_fields")?.toString() ?: "").contains("email_address") -> {
                                    clerk.prepareSignUpEmail(su.getString("id"))
                                    attemptId = su.getString("id"); code = ""; step = Step.VerifySignUp
                                }
                                else -> throw Exception("We couldn't finish creating your account. Please try again.")
                            }
                        } else {
                            continueSignIn(clerk.signInWithPassword(id, password))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(if (signUp) "Already have an account? " else "New to Certo? ", color = P.inkSoft, fontSize = 15.sp)
                    Text(if (signUp) "Sign in" else "Create account", color = P.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSwitch() })
                }
                if (signUp) {
                    Spacer(Modifier.height(14.dp))
                    Text("By continuing you agree to the Terms and Privacy Policy.", color = P.inkFaint, fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Step.VerifySignUp, Step.VerifySignIn, Step.ResetCode -> {
                Title("Check your email", "Enter the 6-digit code we sent to $id.")
                Field(code, { v -> code = v.filter(Char::isDigit).take(6) }, "6-digit code", null, keyboard = KeyboardType.Number)
                Spacer(Modifier.height(16.dp))
                error?.let { ErrorBanner(it); Spacer(Modifier.height(12.dp)) }
                info?.let { Text(it, color = P.inkSoft, fontSize = 13.sp); Spacer(Modifier.height(8.dp)) }
                PrimaryButton("Continue", enabled = code.length == 6, busy = busy) {
                    val aid = attemptId ?: return@PrimaryButton
                    run {
                        when (step) {
                            Step.VerifySignUp -> {
                                val r = clerk.attemptSignUpEmail(aid, code)
                                if (r.optString("status") == "complete") finish() else throw Exception("That code didn't work. Please try again.")
                            }
                            Step.VerifySignIn -> {
                                val r = try { clerk.attemptSecondFactorEmail(aid, code) } catch (e: ClerkException) {
                                    if (e.code == "verification_strategy_not_valid" || e.code == "sign_in_not_needs_second_factor") clerk.attemptFirstFactorEmail(aid, code) else throw e
                                }
                                if (r.optString("status") == "complete") finish() else continueSignIn(r)
                            }
                            else -> {
                                val r = clerk.attemptReset(aid, code)
                                when (r.optString("status")) {
                                    "needs_new_password" -> step = Step.ResetPassword
                                    "complete" -> finish()
                                    else -> throw Exception("That code didn't work. Please try again.")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Resend code", color = Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                    val aid = attemptId ?: return@clickable
                    run {
                        when (step) {
                            Step.VerifySignUp -> clerk.prepareSignUpEmail(aid)
                            Step.VerifySignIn -> clerk.prepareSecondFactorEmail(aid)
                            else -> clerk.prepareReset(clerk.startSignIn(id))
                        }
                        info = "We sent a new code."
                    }
                })
            }
            Step.ResetPassword -> {
                Title("Choose a new password", "Use at least 8 characters.")
                Field(newPassword, { newPassword = it }, "New password", Icons.Filled.Lock, secure = true)
                Spacer(Modifier.height(16.dp))
                error?.let { ErrorBanner(it); Spacer(Modifier.height(12.dp)) }
                PrimaryButton("Save and sign in", enabled = newPassword.length >= 8, busy = busy) {
                    val aid = attemptId ?: return@PrimaryButton
                    run {
                        val r = clerk.resetPassword(aid, newPassword)
                        if (r.optString("status") == "complete") finish() else continueSignIn(r)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(title, color = P.ink, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = P.inkSoft, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(P.stroke))
        Text("or", color = P.inkFaint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(P.stroke))
    }
}

@Composable
fun CircleIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(P.card).border(1.dp, P.stroke, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = P.ink, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun GoogleG() {
    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 20.sp)
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    var reveal by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, P.stroke, RoundedCornerShape(16.dp)),
        placeholder = { Text(placeholder, color = P.inkFaint) },
        leadingIcon = icon?.let { { Icon(it, null, tint = P.inkSoft) } },
        trailingIcon = if (secure) { {
            Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = P.inkSoft,
                modifier = Modifier.clickable { reveal = !reveal })
        } } else null,
        singleLine = true,
        visualTransformation = if (secure && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secure) KeyboardType.Password else keyboard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = P.card, unfocusedContainerColor = P.card,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = P.ink, unfocusedTextColor = P.ink, cursorColor = Brand,
        ),
    )
}
