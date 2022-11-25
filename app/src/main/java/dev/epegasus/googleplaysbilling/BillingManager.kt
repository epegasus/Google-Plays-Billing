package dev.epegasus.googleplaysbilling

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @param activity: Must be a reference of an Activity
 */
class BillingManager(private val activity: Activity) {

    var productDetailsList = ArrayList<ProductDetails>()
    private var isBillingPurchased = false
    private var isBillingReady = false


    private val acknowledgePurchaseResponseListener = AcknowledgePurchaseResponseListener {
        if (it.responseCode == BillingResponseCode.OK) {
            isBillingPurchased = true
            Toast.makeText(activity, "Purchased successfully", Toast.LENGTH_SHORT).show()
        } else {
            isBillingPurchased = false
            Toast.makeText(activity, "Purchased failed", Toast.LENGTH_SHORT).show()
        }
    }


    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult: BillingResult, purchaseMutableList: MutableList<Purchase>? ->
        Log.d(TAG, "BillingResult: ${billingResult.debugMessage}")
        Log.d(TAG, "BillingResult: $purchaseMutableList")
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                Log.d(TAG, "BillingResponse: OK")
                updateListener(purchaseMutableList)
            }

            BillingResponseCode.BILLING_UNAVAILABLE -> Log.d(TAG, "BillingResponse: BILLING_UNAVAILABLE")
            BillingResponseCode.DEVELOPER_ERROR -> Log.d(TAG, "BillingResponse: DEVELOPER_ERROR")
            BillingResponseCode.ERROR -> Log.d(TAG, "BillingResponse: ERROR")
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> Log.d(TAG, "BillingResponse: FEATURE_NOT_SUPPORTED")
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                isBillingPurchased = true
                Log.d(TAG, "BillingResponse: ITEM_ALREADY_OWNED")
            }

            BillingResponseCode.ITEM_NOT_OWNED -> Log.d(TAG, "BillingResponse: ITEM_NOT_OWNED")
            BillingResponseCode.ITEM_UNAVAILABLE -> Log.d(TAG, "BillingResponse: ITEM_UNAVAILABLE")
            BillingResponseCode.SERVICE_DISCONNECTED -> Log.d(TAG, "BillingResponse: SERVICE_DISCONNECTED")
            BillingResponseCode.SERVICE_TIMEOUT -> Log.d(TAG, "BillingResponse: SERVICE_TIMEOUT")
            BillingResponseCode.USER_CANCELED -> Log.d(TAG, "BillingResponse: USER_CANCELED")
        }

        purchaseMutableList?.forEach {
            Log.d(TAG, "purchaseMutableList: $it")
        }
    }

    private fun updateListener(purchaseMutableList: MutableList<Purchase>?) {
        purchaseMutableList?.let {
            handlePurchase(it)
        } ?: kotlin.run {
            Log.d(TAG, "updateListener: List is Empty")
        }
    }

    private fun handlePurchase(purchases: MutableList<Purchase>) = CoroutineScope(Dispatchers.Main).launch {
        purchases.forEach { purchase ->

            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
                    val ackPurchaseResult = withContext(Dispatchers.IO) {
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams.build(), acknowledgePurchaseResponseListener)
                    }
                }
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "onBillingServiceDisconnected: Didn't connected with Server")
                isBillingReady = false
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    Log.d(TAG, "onBillingSetupFinished: The BillingClient is ready. You can query purchases here.")
                    isBillingReady = true
                    showProductsAvailableToBuy()
                } else {
                    Log.d(TAG, "onBillingSetupFinished: Some Exception found!")
                }
            }
        })
    }

    // Pent, Shirt, Belt, Socks

    private val productList by lazy {
        listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
    }

    private val queryProductDetailsParams by lazy {
        QueryProductDetailsParams.newBuilder().setProductList(productList).build()
    }

    private fun showProductsAvailableToBuy() = CoroutineScope(Dispatchers.Main).launch {
        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(queryProductDetailsParams)
        }
        // Process the result.
        if (productDetailsResult.billingResult.responseCode == BillingResponseCode.OK) {
            if (productDetailsResult.productDetailsList == null) {
                Log.d(TAG, "showProductsAvailableToBuy: No products have been purchased")
                isBillingPurchased = false
                return@launch
            }
            productDetailsResult.productDetailsList?.forEach {
                productDetailsList.add(it)
                Log.d(TAG, "showProductsAvailableToBuy: Product ID: ${it.productId}")
                if (it.productId == PRODUCT_ID) {
                    Log.d(TAG, "showProductsAvailableToBuy: Our product is found!")
                   // makePurchase(it)
                    return@launch
                }
            }
            Log.d(TAG, "showProductsAvailableToBuy: ProductDetailsList: $productDetailsList")
        } else {
            Log.d(TAG, "queryAllPurchasedProducts: Exception: ${productDetailsResult.billingResult.responseCode}")
        }
    }

    /**
     *      Result obtained from server productDetailsList{"product_id"}
     */

    fun makePurchase(productDetails: ProductDetails) {
        if (billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS).responseCode != BillingResponseCode.OK) {
            Log.d(TAG, "makePurchase: not Supported")
            return
        }
        if (!isBillingReady) {
            Log.d(TAG, "makePurchase: Billing is not ready")
            return
        }

        // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        // Launch the billing flow
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)


        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                Toast.makeText(activity, "BottomSheet is launched", Toast.LENGTH_SHORT).show()
            }

            BillingResponseCode.USER_CANCELED -> {
                isBillingPurchased = false
                Toast.makeText(activity, "BottomSheet: is revoked by user", Toast.LENGTH_SHORT).show()
            }

            else -> {
                isBillingPurchased = false
                Toast.makeText(activity, "BottomSheet: Exception found", Toast.LENGTH_SHORT).show()
            }
        }
    }


    companion object {
        private const val TAG = "MyTag"
        private val PRODUCT_ID = if (BuildConfig.DEBUG) "android.test.purchased" else "asd"
    }
}

/**
 *      *********************** Documentation ***********************
 *      link:   https://developer.android.com/google/play/billing
 *
 *      * Product Types:-
 *
 *          ->  One-time Products (aka 'INAPP' products)            -- Non-recurring charges
 *              ~   Consumable product                              -- e.g. Purchase game currency ($5 to 1000 game coins)
 *              ~   Non-consumable product                      -- Permanent Purchase e.g. Premium updates
 *
 *          ->  Subscriptions (aka 'SUBS')
 *              ~   Life of a purchase
 *                  - Active
 *                  - Cancelled
 *                  - In Grace Period
 *                  - On hold
 *                  - Paused
 *                  - Expired
 *
 *      * Products & Transactions tracking:-
 *
 *          ->  Purchase Tokens:
 *                  "A purchase token is a string that represents a buyer's entitlement to a product on Google Play.
 *                  It indicates that a Google user is entitled to a specific product that is represented by a purchase object.
 *                  You can use the purchase token with the Google Play Developer API"
 *
 *                  :. Product ID
 *
 *          ->  Order ID
 *                  "An Order ID is a string that represents a financial transaction on Google Play.
 *                  This string is included in a receipt that is emailed to the buyer"
 *
 *                  :. Transaction Id (Unique Id for every purchase)
 *
 *          ~   For one-time products:
 *                  New 'purchase token' & 'order id' are created everytime.
 *          ~   For subscriptions:
 *                  New 'order id' is created everytime, while purchase tokens remain same.
 *                  Upgrades, downgrades, replacements, and re-sign-ups all create new 'purchase tokens' and 'Order IDs'
 *
 *      * Concepts:
 *
 *          ->  Entitlement:    When a user purchases an in-app product, they are then entitled to that product within your app.
 *          ->  Product SKU:    A product SKU is the ID of a specific product type.
 *          ->  SKU:            Stock-Keeping Unit



 *      *********************** Google Billing App's ***********************
 *
 *      *   Steps
 *
 *          ->  Setup Payment Profile in Developer Account
 *          ->  Add Test SKUs and licensed accounts for testing
 *          ->  Play Billing Library (Android Side)
 *              ~   Show all available SKUs
 *              ~   Enable basic purchase flow
 *              ~   Test your integration



 *      *********************** Android Implementation ***********************
 *      link:   https://developer.android.com/google/play/billing/getting-ready
 *
 *      *   Steps
 *
 *          ->  Add Dependency
 *              ~   implementation "com.android.billingclient:billing-ktx:$billing_version"
 *
 *          ->  Upload your App
 *
 *          ->  Create and configure your products (On Console)
 *              ~   For each product, you need to provide a unique product ID, a title, a description, and pricing information.
 *              ~   Subscriptions have additional required information, such as selecting whether it's an auto-renewing or prepaid renewal type for the base plan.
 *
 *          1)  Initialize Billing Client
 *              ~   It is the main interface for communication between the Google Play Billing Library and the rest of your app.
 *          2)  Start Connection
 *              ~   Connection Setup Disconnected
 *              ~   Connection Setup Finished
 *          3)  Query all available products detail by giving list of product_id.
 *
 *          // Make Purchase
 *          4)  Query all available products detail by giving list of product_id.
 *          5)  Launch billing flow (bottomSheet)
 *
 */