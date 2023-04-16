package dev.epegasus.billinginapppurchases.helper

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import dev.epegasus.billinginapppurchases.dataProvider.DataProviderInApp
import dev.epegasus.billinginapppurchases.dataProvider.DataProviderSub
import dev.epegasus.billinginapppurchases.enums.BillingState
import dev.epegasus.billinginapppurchases.enums.SubscriptionTags
import dev.epegasus.billinginapppurchases.interfaces.OnConnectionListener
import dev.epegasus.billinginapppurchases.interfaces.OnPurchaseListener
import dev.epegasus.billinginapppurchases.status.State.getBillingState
import dev.epegasus.billinginapppurchases.status.State.setBillingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("unused")
abstract class BillingHelper(private val activity: Activity) {

    private val dataProviderInApp by lazy { DataProviderInApp() }
    private val dataProviderSub by lazy { DataProviderSub() }

    private var onConnectionListener: OnConnectionListener? = null
    private var onPurchaseListener: OnPurchaseListener? = null

    private var isPurchasedFound = false
    protected var checkForSubscription = false

    /* ------------------------------------------------ Initializations ------------------------------------------------ */

    private val billingClient by lazy {
        BillingClient.newBuilder(activity).setListener(purchasesUpdatedListener).enablePendingPurchases().build()
    }

    /* ------------------------------------------------ Establish Connection ------------------------------------------------ */
    abstract fun setCheckForSubscription(isCheckRequired: Boolean)

    abstract fun startConnection(productIdsList: List<String>, onConnectionListener: OnConnectionListener)

    /**
     *  Get a single testing product_id ("android.test.purchased")
     */
    fun getDebugProductIDList() = dataProviderInApp.getDebugProductIDList()

    /**
     *  Get multiple testing product_ids
     */
    fun getDebugProductIDsList() = dataProviderInApp.getDebugProductIDsList()

    protected fun startBillingConnection(productIdsList: List<String>, onConnectionListener: OnConnectionListener) {
        this.onConnectionListener = onConnectionListener
        if (productIdsList.isEmpty()) {
            setBillingState(BillingState.EMPTY_PRODUCT_ID_LIST)
            onConnectionListener.onConnectionResult(false, BillingState.EMPTY_PRODUCT_ID_LIST.message)
            return
        }
        dataProviderInApp.setProductIdsList(productIdsList)
        setBillingState(BillingState.CONNECTION_ESTABLISHING)

        if (billingClient.isReady) {
            setBillingState(BillingState.CONNECTION_ALREADY_ESTABLISHING)
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                setBillingState(BillingState.CONNECTION_DISCONNECTED)
                Handler(Looper.getMainLooper()).post {
                    onConnectionListener.onConnectionResult(false, BillingState.CONNECTION_DISCONNECTED.message)
                }
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val isBillingReady = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (isBillingReady) {
                    setBillingState(BillingState.CONNECTION_ESTABLISHED)
                    getInAppOldPurchases()
                    Handler(Looper.getMainLooper()).post {
                        onConnectionListener.onConnectionResult(false, BillingState.CONNECTION_ESTABLISHED.message)
                    }
                } else {
                    setBillingState(BillingState.CONNECTION_FAILED)
                    onConnectionListener.onConnectionResult(false, billingResult.debugMessage)
                }
            }
        })
    }

    private fun getInAppOldPurchases() = CoroutineScope(Dispatchers.Main).launch {
        setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_INAPP_FETCHING)
        val queryPurchasesParams = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(queryPurchasesParams) { _, purchases ->
            onConnectionListener?.onOldPurchaseResult(false)
            purchases.forEach { purchase ->
                if (purchase.products.isEmpty()) {
                    setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_INAPP_NOT_FOUND)
                    return@forEach
                }
                // getting the  single  product-id of every purchase in the list = sku
                val compareSKU = purchase.products[0]

                if (purchase.isAcknowledged) {
                    dataProviderInApp.getProductIdsList().forEach {
                        if (it.contains(compareSKU, true)) {
                            setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_INAPP_OWNED)
                            Handler(Looper.getMainLooper()).post {
                                isPurchasedFound = true
                                onConnectionListener?.onOldPurchaseResult(true)
                            }
                        }
                    }
                } else {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        for (i in 0 until dataProviderInApp.getProductIdsList().size) {
                            setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_INAPP_OWNED_BUT_NOT_ACKNOWLEDGE)

                            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()

                            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult: BillingResult ->
                                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK || purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                    setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_INAPP_OWNED_AND_ACKNOWLEDGE)
                                    isPurchasedFound = true
                                    onConnectionListener?.onOldPurchaseResult(true)
                                } else {
                                    setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_INAPP_OWNED_AND_FAILED_TO_ACKNOWLEDGE)
                                }
                            }
                        }
                    }
                }
            }
            if (purchases.isEmpty()) {
                setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_INAPP_NOT_FOUND)
            }
            queryForAvailableInAppProducts()
            queryForAvailableSubProducts()
            checkForSubscriptionIfAvailable()
        }
    }

    /* -------------------------------------------- Query available console products  -------------------------------------------- */

    private fun queryForAvailableInAppProducts() = CoroutineScope(Dispatchers.Main).launch {
        setBillingState(BillingState.CONSOLE_PRODUCTS_IN_APP_FETCHING)
        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(dataProviderInApp.getProductList()).build())
        }
        // Process the result.
        if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            setBillingState(BillingState.CONSOLE_PRODUCTS_IN_APP_FETCHED_SUCCESSFULLY)
            if (productDetailsResult.productDetailsList.isNullOrEmpty()) {
                setBillingState(BillingState.CONSOLE_PRODUCTS_IN_APP_NOT_EXIST)
            } else {
                dataProviderInApp.setProductDetailsList(productDetailsResult.productDetailsList!!)
                setBillingState(BillingState.CONSOLE_PRODUCTS_IN_APP_AVAILABLE)
            }
        } else {
            setBillingState(BillingState.CONSOLE_PRODUCTS_IN_APP_FETCHING_FAILED)
        }
    }

    private fun queryForAvailableSubProducts() = CoroutineScope(Dispatchers.Main).launch {
        setBillingState(BillingState.CONSOLE_PRODUCTS_SUB_FETCHING)
        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(dataProviderSub.getProductList()).build())
        }
        // Process the result.
        if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            setBillingState(BillingState.CONSOLE_PRODUCTS_SUB_FETCHED_SUCCESSFULLY)
            if (productDetailsResult.productDetailsList.isNullOrEmpty()) {
                setBillingState(BillingState.CONSOLE_PRODUCTS_SUB_NOT_EXIST)
            } else {
                dataProviderSub.setProductDetailsList(productDetailsResult.productDetailsList!!)
                setBillingState(BillingState.CONSOLE_PRODUCTS_SUB_AVAILABLE)
            }
        } else {
            setBillingState(BillingState.CONSOLE_PRODUCTS_SUB_FETCHING_FAILED)
        }
    }

    private fun checkForSubscriptionIfAvailable() {
        if (isPurchasedFound || !checkForSubscription) {
            return
        }
        getSubscriptionOldPurchases()
    }

    private fun getSubscriptionOldPurchases() = CoroutineScope(Dispatchers.Main).launch {
        setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_SUB_FETCHING)
        val queryPurchasesParams = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()

        billingClient.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onConnectionListener?.onOldPurchaseResult(false)

                purchases.forEach { purchase ->
                    if (purchase.products.isEmpty()) {
                        setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_SUB_NOT_FOUND)
                        return@forEach
                    }

                    // getting the  single  product-id of every purchase in the list = sku
                    val compareSKU = purchase.products[0]

                    if (purchase.isAcknowledged) {
                        for (i in 0 until dataProviderSub.productIdsList.size) {
                            if (dataProviderSub.productIdsList[i].contains(compareSKU)) {
                                setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_SUB_OWNED)
                                onConnectionListener?.onOldPurchaseResult(true)
                                return@forEach
                            }
                        }
                    } else {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_SUB_OWNED_BUT_NOT_ACKNOWLEDGE)
                            for (i in 0 until dataProviderSub.productIdsList.size) {
                                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken)
                                    .build()
                                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult: BillingResult ->
                                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                        setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_SUB_OWNED_AND_ACKNOWLEDGE)
                                        onConnectionListener?.onOldPurchaseResult(true)
                                    } else {
                                        setBillingState(BillingState.CONSOLE_OLD_PRODUCTS_SUB_OWNED_AND_FAILED_TO_ACKNOWLEDGE)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    /* --------------------------------------------------- Make Purchase  --------------------------------------------------- */


    protected fun purchaseInApp(onPurchaseListener: OnPurchaseListener) {
        this.onPurchaseListener = onPurchaseListener
        if (checkValidationsInApp()) return

        val productDetailsParamsList = listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(dataProviderInApp.getProductDetail()).build())
        val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()

        // Launch the billing flow
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_SUCCESSFULLY)
            BillingClient.BillingResponseCode.USER_CANCELED -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_USER_CANCELLED)
            else -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_EXCEPTION_FOUND)
        }
    }

    private fun checkValidationsInApp(): Boolean {
        if (getBillingState() == BillingState.EMPTY_PRODUCT_ID_LIST) {
            onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
            return true
        }

        if (getBillingState() == BillingState.NO_INTERNET_CONNECTION) {
            if (isInternetConnected && onConnectionListener != null) {
                startBillingConnection(productIdsList = dataProviderInApp.getProductIdsList(), onConnectionListener!!)
                return true
            }
            onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
            return true
        }


        if (getBillingState() == BillingState.CONNECTION_FAILED || getBillingState() == BillingState.CONNECTION_DISCONNECTED || getBillingState() == BillingState.CONNECTION_ESTABLISHING) {
            onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
            return true
        }

        if (getBillingState() == BillingState.CONSOLE_PRODUCTS_IN_APP_FETCHING || getBillingState() == BillingState.CONSOLE_PRODUCTS_IN_APP_FETCHING_FAILED) {
            onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
            return true
        }

        if (getBillingState() == BillingState.CONSOLE_PRODUCTS_IN_APP_NOT_EXIST) {
            onPurchaseListener?.onPurchaseResult(false, BillingState.CONSOLE_PRODUCTS_IN_APP_NOT_EXIST.message)
            return true
        }

        dataProviderInApp.getProductIdsList().forEach { id ->
            dataProviderInApp.getProductDetailsList().forEach { productDetails ->
                if (id != productDetails.productId) {
                    setBillingState(BillingState.CONSOLE_PRODUCTS_IN_APP_NOT_FOUND)
                    onPurchaseListener?.onPurchaseResult(false, BillingState.CONSOLE_PRODUCTS_IN_APP_NOT_FOUND.message)
                    return true
                }
            }
        }

        if (billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS).responseCode != BillingClient.BillingResponseCode.OK) {
            setBillingState(BillingState.FEATURE_NOT_SUPPORTED)
            return true
        }
        return false
    }

    protected fun purchaseSub(subscriptionTags: SubscriptionTags, onPurchaseListener: OnPurchaseListener) {
        if (checkValidationsSub()) return

        this.onPurchaseListener = onPurchaseListener
        val productDetails = dataProviderSub.getProductDetailsList()[0]

        // Retrieve all offers the user is eligible for.
        val offers = productDetails.subscriptionOfferDetails?.let {
            retrieveEligibleOffers(offerDetails = it, tag = subscriptionTags.toString())
        }

        //  Get the offer id token of the lowest priced offer.
        val offerToken = offers?.let { leastPricedOfferToken(it) }

        offerToken?.let { token ->
            val productDetailsParamsList = listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(dataProviderSub.getProductDetail()).setOfferToken(token).build())
            val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()

            // Launch the billing flow
            val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)


            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_SUCCESSFULLY)
                BillingClient.BillingResponseCode.USER_CANCELED -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_USER_CANCELLED)
                else -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_EXCEPTION_FOUND)
            }
        }
    }

    private fun checkValidationsSub(): Boolean {
        if (getBillingState() == BillingState.NO_INTERNET_CONNECTION) {
            if (isInternetConnected && onConnectionListener != null) {
                startBillingConnection(productIdsList = dataProviderInApp.getProductIdsList(), onConnectionListener!!)
                return true
            }
            onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
            return true
        }


        if (getBillingState() == BillingState.CONNECTION_FAILED || getBillingState() == BillingState.CONNECTION_DISCONNECTED || getBillingState() == BillingState.CONNECTION_ESTABLISHING) {
            onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
            return true
        }

        if (getBillingState() == BillingState.CONSOLE_PRODUCTS_SUB_FETCHING || getBillingState() == BillingState.CONSOLE_PRODUCTS_SUB_FETCHING_FAILED) {
            onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
            return true
        }

        if (getBillingState() == BillingState.CONSOLE_PRODUCTS_SUB_NOT_EXIST) {
            onPurchaseListener?.onPurchaseResult(false, BillingState.CONSOLE_PRODUCTS_SUB_NOT_EXIST.message)
            return true
        }

        dataProviderInApp.getProductIdsList().forEach { id ->
            dataProviderInApp.getProductDetailsList().forEach { productDetails ->
                if (id != productDetails.productId) {
                    setBillingState(BillingState.CONSOLE_PRODUCTS_SUB_NOT_FOUND)
                    onPurchaseListener?.onPurchaseResult(false, BillingState.CONSOLE_PRODUCTS_SUB_NOT_FOUND.message)
                    return true
                }
            }
        }

        if (billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS).responseCode != BillingClient.BillingResponseCode.OK) {
            setBillingState(BillingState.FEATURE_NOT_SUPPORTED)
            return true
        }
        return false
    }

    /**
     * Retrieves all eligible base plans and offers using tags from ProductDetails.
     *
     * @param offerDetails offerDetails from a ProductDetails returned by the library.
     * @param tag string representing tags associated with offers and base plans.
     *
     * @return the eligible offers and base plans in a list.
     *
     */
    private fun retrieveEligibleOffers(offerDetails: MutableList<ProductDetails.SubscriptionOfferDetails>, tag: String): List<ProductDetails.SubscriptionOfferDetails> {
        val eligibleOffers = emptyList<ProductDetails.SubscriptionOfferDetails>().toMutableList()
        offerDetails.forEach { offerDetail ->
            if (offerDetail.offerTags.contains(tag)) {
                eligibleOffers.add(offerDetail)
            }
        }
        return eligibleOffers
    }

    /**
     * Calculates the lowest priced offer amongst all eligible offers.
     * In this implementation the lowest price of all offers' pricing phases is returned.
     * It's possible the logic can be implemented differently.
     * For example, the lowest average price in terms of month could be returned instead.
     *
     * @param offerDetails List of of eligible offers and base plans.
     *
     * @return the offer id token of the lowest priced offer.
     *
     */
    private fun leastPricedOfferToken(offerDetails: List<ProductDetails.SubscriptionOfferDetails>): String {
        var offerToken = String()
        var leastPricedOffer: ProductDetails.SubscriptionOfferDetails
        var lowestPrice = Int.MAX_VALUE

        if (offerDetails.isNotEmpty()) {
            for (offer in offerDetails) {
                for (price in offer.pricingPhases.pricingPhaseList) {
                    if (price.priceAmountMicros < lowestPrice) {
                        lowestPrice = price.priceAmountMicros.toInt()
                        leastPricedOffer = offer
                        offerToken = leastPricedOffer.offerToken
                    }
                }
            }
        }
        return offerToken
    }

    /* --------------------------------------------------- Purchase Response  --------------------------------------------------- */

    private val purchasesUpdatedListener: PurchasesUpdatedListener = PurchasesUpdatedListener { billingResult: BillingResult, purchaseMutableList: MutableList<Purchase>? ->
        Log.d(TAG, "purchasesUpdatedListener: $purchaseMutableList")

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                setBillingState(BillingState.PURCHASED_SUCCESSFULLY)
                handlePurchase(purchaseMutableList)
                return@PurchasesUpdatedListener
            }

            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {}
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {}
            BillingClient.BillingResponseCode.ERROR -> setBillingState(BillingState.PURCHASING_ERROR)
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {}
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                setBillingState(BillingState.PURCHASING_ALREADY_OWNED)
                onPurchaseListener?.onPurchaseResult(true, BillingState.PURCHASING_ALREADY_OWNED.message)
                return@PurchasesUpdatedListener
            }

            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {}
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {}
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {}
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {}
            BillingClient.BillingResponseCode.USER_CANCELED -> setBillingState(BillingState.PURCHASING_USER_CANCELLED)
        }
        onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
    }

    private fun handlePurchase(purchases: MutableList<Purchase>?) = CoroutineScope(Dispatchers.Main).launch {
        purchases?.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (purchase.isAcknowledged) {
                    setBillingState(BillingState.PURCHASED_SUCCESSFULLY)
                    onPurchaseListener?.onPurchaseResult(true, BillingState.PURCHASED_SUCCESSFULLY.message)
                } else {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    withContext(Dispatchers.IO) {
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener)
                    }
                }
                return@launch
            } else {
                setBillingState(BillingState.PURCHASING_FAILURE)
            }
        } ?: kotlin.run {
            setBillingState(BillingState.PURCHASING_USER_CANCELLED)
        }
        onPurchaseListener?.onPurchaseResult(false, getBillingState().message)
    }


    private val acknowledgePurchaseResponseListener = AcknowledgePurchaseResponseListener {
        if (it.responseCode == BillingClient.BillingResponseCode.OK) {
            setBillingState(BillingState.PURCHASED_SUCCESSFULLY)
            onPurchaseListener?.onPurchaseResult(true, BillingState.PURCHASED_SUCCESSFULLY.message)
            Log.d(TAG, "acknowledgePurchaseResponseListener: Acknowledged successfully")
        } else {
            Log.d(TAG, "acknowledgePurchaseResponseListener: Acknowledgment failure")
        }
    }

    /* ------------------------------------- Internet Connection ------------------------------------- */

    private val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val isInternetConnected: Boolean
        get() {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }

    companion object {
        const val TAG = "BillingManager"
    }
}
