package dev.epegasus.billinginapppurchases.helper

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import dev.epegasus.billinginapppurchases.dataProvider.DataProvider
import dev.epegasus.billinginapppurchases.enums.BillingState
import dev.epegasus.billinginapppurchases.status.State.getBillingState
import dev.epegasus.billinginapppurchases.status.State.setBillingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("unused")
abstract class BillingHelper(private val activity: Activity) {

    private val dpProvider by lazy { DataProvider() }
    private var callback: ((isPurchased: Boolean, message: String) -> Unit)? = null

    /* ------------------------------------------------ Initializations ------------------------------------------------ */

    private val billingClient by lazy {
        BillingClient.newBuilder(activity).setListener(purchasesUpdatedListener).enablePendingPurchases().build()
    }

    /* ------------------------------------------------ Establish Connection ------------------------------------------------ */

    abstract fun startConnection(productIdsList: List<String>, callback: (connectionResult: Boolean, message: String) -> Unit)

    /**
     *  Get a single testing product_id ("android.test.purchased")
     */
    fun getDebugProductIDList() = dpProvider.getDebugProductIDList()

    /**
     *  Get multiple testing product_ids
     */
    fun getDebugProductIDsList() = dpProvider.getDebugProductIDsList()

    fun startBillingConnection(productIdsList: List<String>, callback: (connectionResult: Boolean, message: String) -> Unit) {
        if (productIdsList.isEmpty()) {
            setBillingState(BillingState.EMPTY_PRODUCT_ID_LIST)
            callback.invoke(false, BillingState.EMPTY_PRODUCT_ID_LIST.toString())
            return
        }
        dpProvider.setProductList(productIdsList)

        setBillingState(BillingState.CONNECTION_ESTABLISHING)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                setBillingState(BillingState.CONNECTION_DISCONNECTED)
                Handler(Looper.getMainLooper()).post { callback.invoke(false, BillingState.CONNECTION_DISCONNECTED.toString()) }
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val isBillingReady = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (isBillingReady) {
                    setBillingState(BillingState.CONNECTION_ESTABLISHED)
                    queryForAvailableProducts()
                } else {
                    setBillingState(BillingState.CONNECTION_FAILED)
                }
                Handler(Looper.getMainLooper()).post { callback.invoke(isBillingReady, billingResult.debugMessage) }
            }
        })
    }

    /* -------------------------------------------- Query available console products  -------------------------------------------- */

    private fun queryForAvailableProducts() = CoroutineScope(Dispatchers.Main).launch {
        setBillingState(BillingState.CONSOLE_PRODUCTS_FETCHING)
        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(dpProvider.getProductList()).build())
        }
        // Process the result.
        if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            setBillingState(BillingState.CONSOLE_PRODUCTS_FETCHED_SUCCESSFULLY)
            if (productDetailsResult.productDetailsList.isNullOrEmpty()) {
                setBillingState(BillingState.CONSOLE_PRODUCTS_NOT_EXIST)
            } else {
                dpProvider.setProductDetailsList(productDetailsResult.productDetailsList!!)
                setBillingState(BillingState.CONSOLE_PRODUCTS_AVAILABLE)
            }
        } else {
            setBillingState(BillingState.CONSOLE_PRODUCTS_FETCHING_FAILED)
        }
    }

    /* --------------------------------------------------- Make Purchase  --------------------------------------------------- */

    fun purchase(callback: (isPurchased: Boolean, message: String) -> Unit) {
        if (checkValidations(callback)) return

        this.callback = callback

        val productDetailsParamsList = listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(dpProvider.getProductDetail()).build())

        val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()

        // Launch the billing flow
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)


        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_SUCCESSFULLY)
            BillingClient.BillingResponseCode.USER_CANCELED -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_USER_CANCELLED)
            else -> setBillingState(BillingState.LAUNCHING_FLOW_INVOCATION_EXCEPTION_FOUND)
        }
    }

    private fun checkValidations(callback: (isPurchased: Boolean, message: String) -> Unit): Boolean {
        if (getBillingState() == BillingState.EMPTY_PRODUCT_ID_LIST) {
            callback.invoke(false, getBillingState().toString())
            return true
        }

        if (getBillingState() == BillingState.CONNECTION_FAILED || getBillingState() == BillingState.CONNECTION_DISCONNECTED || getBillingState() == BillingState.CONNECTION_ESTABLISHING) {
            callback.invoke(false, getBillingState().toString())
            return true
        }

        if (getBillingState() == BillingState.CONSOLE_PRODUCTS_FETCHING || getBillingState() == BillingState.CONSOLE_PRODUCTS_FETCHING_FAILED) {
            callback.invoke(false, getBillingState().toString())
            return true
        }

        if (getBillingState() == BillingState.CONSOLE_PRODUCTS_NOT_EXIST) {
            callback.invoke(false, BillingState.CONSOLE_PRODUCTS_NOT_EXIST.toString())
            return true
        }

        dpProvider.getDebugProductIDList().forEach { id ->
            dpProvider.getProductDetailsList().forEach { productDetails ->
                if (id != productDetails.productId) {
                    setBillingState(BillingState.CONSOLE_PRODUCTS_NOT_FOUND)
                    callback.invoke(false, BillingState.CONSOLE_PRODUCTS_NOT_FOUND.toString())
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
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> setBillingState(BillingState.PURCHASING_ALREADY_OWNED)
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {}
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {}
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {}
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {}
            BillingClient.BillingResponseCode.USER_CANCELED -> setBillingState(BillingState.PURCHASING_USER_CANCELLED)
        }
        callback?.invoke(false, getBillingState().toString())
    }

    private fun handlePurchase(purchases: MutableList<Purchase>?) = CoroutineScope(Dispatchers.Main).launch {
        purchases?.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                setBillingState(BillingState.PURCHASED_SUCCESSFULLY)
                callback?.invoke(true, BillingState.PURCHASED_SUCCESSFULLY.toString())
                if (!purchase.isAcknowledged) {
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
        callback?.invoke(false, getBillingState().toString())
    }

    private val acknowledgePurchaseResponseListener = AcknowledgePurchaseResponseListener {
        if (it.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "acknowledgePurchaseResponseListener: Acknowledged successfully")
        } else {
            Log.d(TAG, "acknowledgePurchaseResponseListener: Acknowledgment failure")
        }
    }

    companion object {
        const val TAG = "BillingManager"
    }
}
