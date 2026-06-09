package ai.telegram.android.billing

import ai.telegram.android.R
import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DonationProduct(
    val productId: String,
    val title: String,
    val price: String,
    internal val productDetails: ProductDetails
)

data class DonationBillingState(
    val ready: Boolean = false,
    val loading: Boolean = false,
    val products: List<DonationProduct> = emptyList(),
    val message: String? = null
)

class DonationBillingManager(
    context: Context,
    private val onStateChanged: (DonationBillingState) -> Unit
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private var state = DonationBillingState(loading = true)
        set(value) {
            field = value
            onStateChanged(value)
        }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    suspend fun start() {
        state = state.copy(loading = true, message = appContext.getString(R.string.billing_connecting))
        val result = connect()
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            state = DonationBillingState(message = result.debugMessage.ifBlank { appContext.getString(R.string.billing_unavailable) })
            return
        }
        consumeOwnedDonations()
        refreshProducts()
    }

    suspend fun refreshProducts() {
        state = state.copy(loading = true, message = appContext.getString(R.string.billing_loading_products))
        val productDetailsResponse = queryProductDetails(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    DONATION_PRODUCT_IDS.map { productId ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    }
                )
                .build()
        )

        val result = productDetailsResponse.billingResult
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            state = state.copy(
                loading = false,
                ready = false,
                products = emptyList(),
                message = result.debugMessage.ifBlank { appContext.getString(R.string.billing_load_products_failed) }
            )
            return
        }

        val products = productDetailsResponse.productDetailsResult.productDetailsList
            .map { details ->
                DonationProduct(
                    productId = details.productId,
                    title = details.name.ifBlank { details.title },
                    price = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                        ?: details.oneTimePurchaseOfferDetails?.formattedPrice
                        ?: "",
                    productDetails = details
                )
            }
            .sortedBy { DONATION_PRODUCT_IDS.indexOf(it.productId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }

        state = DonationBillingState(
            ready = true,
            loading = false,
            products = products,
            message = if (products.isEmpty()) appContext.getString(R.string.billing_products_not_configured) else null
        )
    }

    fun launchDonation(activity: Activity, product: DonationProduct) {
        val offerToken = product.productDetails.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product.productDetails)
        if (!offerToken.isNullOrBlank()) {
            productParamsBuilder.setOfferToken(offerToken)
        }

        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            state = state.copy(message = result.debugMessage.ifBlank { appContext.getString(R.string.billing_launch_failed) })
        }
    }

    private data class ProductDetailsResponse(
        val billingResult: BillingResult,
        val productDetailsResult: QueryProductDetailsResult
    )

    private data class PurchasesResponse(
        val billingResult: BillingResult,
        val purchases: List<Purchase>
    )

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                state = state.copy(message = appContext.getString(R.string.billing_user_canceled))
            }
            else -> {
                state = state.copy(message = billingResult.debugMessage.ifBlank { appContext.getString(R.string.billing_not_completed) })
            }
        }
    }

    fun close() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            state = state.copy(message = appContext.getString(R.string.billing_purchase_pending))
            return
        }

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { result, _ ->
            state = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                state.copy(message = appContext.getString(R.string.billing_thank_you))
            } else {
                state.copy(message = result.debugMessage.ifBlank { appContext.getString(R.string.billing_consume_failed) })
            }
        }
    }

    private suspend fun consumeOwnedDonations() {
        val response = queryPurchases(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return

        response.purchases
            .filter { purchase -> purchase.products.any { it in DONATION_PRODUCT_IDS } }
            .forEach(::processPurchase)
    }

    private suspend fun connect(): BillingResult {
        if (billingClient.isReady) {
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build()
        }

        return suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    continuation.resume(billingResult)
                }

                override fun onBillingServiceDisconnected() {
                    state = state.copy(ready = false, message = appContext.getString(R.string.billing_disconnected))
                }
            })
        }
    }

    private suspend fun queryProductDetails(params: QueryProductDetailsParams): ProductDetailsResponse {
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                continuation.resume(ProductDetailsResponse(billingResult, productDetailsResult))
            }
        }
    }

    private suspend fun queryPurchases(params: QueryPurchasesParams): PurchasesResponse {
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                continuation.resume(PurchasesResponse(billingResult, purchases))
            }
        }
    }

    companion object {
        val DONATION_PRODUCT_IDS = listOf(
            "donation_20k_vnd",
            "donation_50k_vnd",
            "donation_100k_vnd"
        )
    }
}
