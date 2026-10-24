package com.qortn.certo

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.json.JSONObject
import kotlin.random.Random

/**
 * Google AdMob ads for free users, switched on and off from the admin console (app_config.ads_*).
 * Premium members never see ads. Until real ad unit IDs are set in the console, Google's test units are used.
 */
object Ads {
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    var enabled by mutableStateOf(false); private set
    var bannerOn by mutableStateOf(true); private set
    var bannerId by mutableStateOf(TEST_BANNER); private set
    private var interstitialId = TEST_INTERSTITIAL
    private var chance = 3
    private var started = false
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
        row.optString("ads_android_banner_id").takeIf { it.isNotBlank() && it != "null" }?.let { bannerId = it }
        row.optString("ads_android_interstitial_id").takeIf { it.isNotBlank() && it != "null" }?.let {
            if (it != interstitialId) { interstitialId = it; interstitial = null }
        }
        if (enabled) start()
    }

    fun showBanner(isPremium: Boolean) = enabled && bannerOn && !isPremium

    private fun start() {
        val ctx = appContext ?: return
        if (started) { preload(); return }
        started = true
        MobileAds.initialize(ctx) { preload() }
    }

    private fun preload() {
        val ctx = appContext ?: return
        if (!enabled || interstitial != null || loading) return
        loading = true
        InterstitialAd.load(ctx, interstitialId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; loading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null; loading = false }
        })
    }

    /** When someone leaves a quiz: now and then a full-screen ad, never for Premium, at most once every two minutes. */
    fun afterQuiz(activity: Activity, isPremium: Boolean) {
        if (!enabled || isPremium) return
        if (System.currentTimeMillis() - lastShown < 120_000) return
        if (Random.nextInt(chance) != 0) { preload(); return }
        val ad = interstitial ?: run { preload(); return }
        interstitial = null
        lastShown = System.currentTimeMillis()
        ad.show(activity)
        preload()
    }
}

/** An adaptive banner the width of the screen, only for free users while ads are on. */
@Composable
fun AdBanner(isPremium: Boolean) {
    if (!Ads.showBanner(isPremium)) return
    val width = LocalConfiguration.current.screenWidthDp
    val unit = Ads.bannerId
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width))
                adUnitId = unit
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
