package com.qortn.certo

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.qortn.certo.ui.P
import org.json.JSONObject
import kotlin.random.Random

/**
 * Google AdMob ads for free users, switched on and off from the admin console (app_config.ads_*).
 * Premium members never see ads, and never see the consent form.
 *
 * Where ads appear (kept calm on purpose): one small banner card in the Study tab after the quiz modes,
 * one at the end of the Learn tab, and now and then a full-screen ad after closing a finished quiz.
 * Never during a quiz or exam, on the paywall, sign-in, onboarding, Settings or the splash.
 */
object Ads {
    // Certo's own AdMob units (app ca-app-pub-6723276431169192~4158613067). The console can override them.
    private const val LIVE_BANNER = "ca-app-pub-6723276431169192/7147738483"
    private const val LIVE_INTERSTITIAL = "ca-app-pub-6723276431169192/6158090441"
    // Google's test units, used in debug builds so nobody taps a live ad by mistake.
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    private const val QUIZZES_KEY = "ads.finishedQuizzes"
    private const val GRACE_QUIZZES = 2
    private const val GAP_MS = 240_000L

    var enabled by mutableStateOf(false); private set
    var bannerOn by mutableStateOf(true); private set
    /** True once consent (where the law asks for it) lets ads load. */
    var ready by mutableStateOf(false); private set
    /** Whether Settings should offer "Ad privacy". */
    var privacyOptionsNeeded by mutableStateOf(false); private set
    var bannerId by mutableStateOf(if (BuildConfig.DEBUG) TEST_BANNER else LIVE_BANNER); private set
    private var interstitialId = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else LIVE_INTERSTITIAL
    private var chance = 3
    private var preparing = false
    private var sdkStarted = false
    private var loading = false
    private var interstitial: InterstitialAd? = null
    private var lastShown = 0L
    private var appContext: Context? = null

    /** Reads the console's settings (from app_config). */
    fun apply(context: Context, row: JSONObject) {
        appContext = context.applicationContext
        enabled = row.optBoolean("ads_enabled", false)
        bannerOn = row.optBoolean("ads_banner", true)
        chance = row.optInt("ads_chance", 3).coerceIn(2, 10)
        if (!BuildConfig.DEBUG) {
            row.optString("ads_android_banner_id").takeIf { it.startsWith("ca-app-pub-") }?.let { bannerId = it }
            row.optString("ads_android_interstitial_id").takeIf { it.startsWith("ca-app-pub-") }?.let {
                if (it != interstitialId) { interstitialId = it; interstitial = null }
            }
        }
        if (enabled && ready) preload()
    }

    fun showBanner(isPremium: Boolean) = enabled && bannerOn && ready && !isPremium

    /** Asks for consent where needed (UK and EEA), then starts the SDK. Only for free members while ads are on. */
    fun prepare(activity: Activity, isPremium: Boolean) {
        if (!enabled || isPremium || ready || preparing) return
        preparing = true
        appContext = activity.applicationContext
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { consentDone(info) }
        }, { consentDone(info) })
    }

    private fun consentDone(info: ConsentInformation) {
        preparing = false
        privacyOptionsNeeded = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!info.canRequestAds()) return
        val ctx = appContext ?: return
        if (sdkStarted) { ready = true; preload(); return }
        sdkStarted = true
        MobileAds.initialize(ctx) { ready = true; preload() }
    }

    /** Settings › Ad privacy. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { consentDone(UserMessagingPlatform.getConsentInformation(activity)) }
    }

    private fun preload() {
        val ctx = appContext ?: return
        if (!enabled || !ready || interstitial != null || loading) return
        loading = true
        InterstitialAd.load(ctx, interstitialId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; loading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null; loading = false }
        })
    }

    /**
     * When someone closes a finished quiz's results: now and then a full-screen ad. Never for Premium,
     * never for short quizzes, not for a new member's first quizzes, at most once every four minutes.
     */
    fun afterQuiz(activity: Activity, isPremium: Boolean, questions: Int) {
        if (!enabled || isPremium) return
        val prefs = activity.getSharedPreferences("certo.ads", Context.MODE_PRIVATE)
        val finished = prefs.getInt(QUIZZES_KEY, 0) + 1
        prefs.edit().putInt(QUIZZES_KEY, finished).apply()
        if (!ready) { prepare(activity, isPremium); return }
        if (finished <= GRACE_QUIZZES || questions < 5) { preload(); return }
        if (System.currentTimeMillis() - lastShown < GAP_MS) return
        if (Random.nextInt(chance) != 0) { preload(); return }
        val ad = interstitial ?: run { preload(); return }
        interstitial = null
        lastShown = System.currentTimeMillis()
        ad.show(activity)
        preload()
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** A quiet banner card that sits in a page like any other card, marked "Ad". Free members only. */
@Composable
fun AdCard(isPremium: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(isPremium, Ads.enabled) { context.findActivity()?.let { Ads.prepare(it, isPremium) } }
    if (!Ads.showBanner(isPremium)) return
    val unit = Ads.bannerId
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).background(P.card).border(1.dp, P.stroke, shape).padding(12.dp),
    ) {
        Text("Ad", color = P.inkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.size(320.dp, 50.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = unit
                        loadAd(AdRequest.Builder().build())
                    }
                },
            )
        }
    }
}
