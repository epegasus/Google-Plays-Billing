package dev.epegasus.billinginapppurchases

import android.app.Activity
import dev.epegasus.billinginapppurchases.enums.SubscriptionTags
import dev.epegasus.billinginapppurchases.helper.BillingHelper
import dev.epegasus.billinginapppurchases.interfaces.OnConnectionListener
import dev.epegasus.billinginapppurchases.interfaces.OnPurchaseListener

/**
 * @param activity: Must be a reference of an Activity
 */
class BillingManager(private val activity: Activity) : BillingHelper(activity) {

    override fun setCheckForSubscription(isCheckRequired: Boolean) {
        checkForSubscription = isCheckRequired
    }

    override fun startConnection(productIdsList: List<String>, onConnectionListener: OnConnectionListener) = startBillingConnection(productIdsList, onConnectionListener)

    fun makeInAppPurchase(onPurchaseListener: OnPurchaseListener) = purchaseInApp(onPurchaseListener)

    fun makeSubPurchase(subscriptionTags: SubscriptionTags, onPurchaseListener: OnPurchaseListener) = purchaseSub(subscriptionTags, onPurchaseListener)
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
 *          6)  Get Response, save it in SharedPreference or else
 *
 */