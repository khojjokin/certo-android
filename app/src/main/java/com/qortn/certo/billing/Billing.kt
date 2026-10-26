package com.qortn.certo.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/** One plan the user can buy (a base plan of the certo_premium subscription). */
data class Plan(
    val id: String,          // "monthly" or "yearly"
    val title: String,
    val price: String,
    val period: String,
    val offerToken: String,
)

/** Google Play Billing for Certo Premium. */
class Billing(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var isPremium by mutableStateOf(false)
        private set
    var plans by mutableStateOf<List<Plan>>(emptyList())
        private set
    var activePlan by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** Obfuscated account id so purchases can be matched to the Certo member. */
    var accountId: String? = null
    /** Called for each active purchase so it can be recorded on the server (for refunds). */
    var onPurchase: (suspend (Purchase) -> Unit)? = null

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> scope.launch { onPurchases(result, purchases) } }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { refresh() }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    suspend fun refresh() {
        if (!client.isReady) return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        ).build()
        val result = client.queryProductDetails(params)
        details = result.productDetailsList?.firstOrNull()
        plans = details?.subscriptionOfferDetails.orEmpty()
            .filter { it.offerId == null }
            .mapNotNull { offer ->
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                val yearly = phase.billingPeriod.contains("Y")
                Plan(
                    id = offer.basePlanId,
                    title = if (yearly) "Yearly" else "Monthly",
                    price = phase.formattedPrice,
                    period = if (yearly) "per year" else "per month",
                    offerToken = offer.offerToken,
                )
            }
            .sortedBy { if (it.id.contains("year")) 0 else 1 }

        val owned = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        handle(owned.purchasesList)
    }

    fun buy(activity: Activity, plan: Plan) {
        val d = details ?: run { message = "Subscriptions aren't available right now. Please try again."; return }
        busy = true
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(d)
            .setOfferToken(plan.offerToken)
            .build()
        val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product))
        accountId?.let { builder.setObfuscatedAccountId(hash(it)) }
        val result = client.launchBillingFlow(activity, builder.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            busy = false
            message = "Couldn't open Google Play. Please try again."
        }
    }

    fun restore() = scope.launch {
        // Not connected to Google Play yet: say so, rather than wrongly reporting "no subscription".
        if (!client.isReady) { message = "Couldn't reach Google Play. Check your connection and try again."; return@launch }
        busy = true
        runCatching { refresh() }
        busy = false
        message = if (isPremium) "Your Certo Premium subscription is active." else "No active subscription was found for this Google account."
    }

    private suspend fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
        busy = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                handle(purchases.orEmpty())
                if (isPremium) message = "You're all set. Your purchase was successful."
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> message = "The purchase didn't go through. Please try again."
        }
    }

    private suspend fun handle(purchases: List<Purchase>) {
        val active = purchases.filter { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        for (p in active) {
            if (!p.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
            }
        }
        for (p in active) runCatching { onPurchase?.invoke(p) }
        isPremium = active.isNotEmpty()
        activePlan = if (active.isEmpty()) null else plans.firstOrNull()?.let { "Premium" } ?: "Premium"
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(("certo:$s").toByteArray()).joinToString("") { "%02x".format(it) }.take(64)

    companion object {
        const val PRODUCT_ID = "certo_premium"
    }
}
